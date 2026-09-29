package io.github.bluestormdna.kocoboy.core

import io.github.bluestormdna.kocoboy.host.Host
import kotlin.experimental.and

class PPU(private val host: Host, private val scheduler: Scheduler) {

    private var windowInternalLine = 0
    private var windowTriggeredThisFrame = false
    private val frameBuffer = IntArray(160 * 144)

    private val vRam = ByteArray(0x2000)

    val oam = ByteArray(0xA0)

    // Every pixel of 8000-97FF as its colour id, 8 per tile row, kept in step with vRam by writeVram
    private val tileIds = ByteArray(TILE_COUNT * TILE_PIXELS)

    // per scanline BG colour ids, sprite priority is decided on the id not the shade
    private val lineIds = ByteArray(SCREEN_WIDTH)

    // PPU Regs
    private var lcdc: Byte = 0 // FF40 - LCDC - LCD Control (R/W)
    private var stat: Byte = 0 // FF41 - STAT - LCDC Status (R/W)
    private var scy: Byte = 0 // FF42 - SCY - Scroll Y (R/W)
    private var scx: Byte = 0 // FF43 - SCX - Scroll X (R/W)
    private var lyc: Byte = 0 // FF45 - LYC - LY Compare(R/W)
    private var dma: Byte = 0 // FF46 - DMA - OAM transfer source, reads back what was written
    private var bgp: Byte = 0 // FF47 - BGP - BG Palette Data(R/W) - Non CGB Mode Only
    private var obp0: Byte = 0 // FF48 - OBP0 - Object Palette 0 Data (R/W) - Non CGB Mode Only
    private var obp1: Byte = 0 // FF49 - OBP1 - Object Palette 1 Data (R/W) - Non CGB Mode Only
    private var wy: Byte = 0 // FF4A - WY - Window Y Position (R/W)
    private var wx: Byte = 0 // FF4B - WX - Window X Position minus 7 (R/W)

    // lcdc bit fields
    private var isEnabled: Boolean = false

    // STAT's LY=LYC bit, frozen while the LCD is off
    private var lycWhileOff = 0

    private var lcdOnAt: Long = 0

    private var renderFrame: Long = 0
    private var renderedLines = 0

    // Where mode 3 ends on the latest line that reached it
    private var latchedLine = Long.MIN_VALUE
    private var latchedHblankDot = HBLANK_DOT

    // Cached palettes
    private val backgroundPalette = IntArray(4)
    private val objectPalette0 = IntArray(4)
    private val objectPalette1 = IntArray(4)

    fun read(ioAddress: Int): Byte = when (ioAddress) {
        0x40 -> lcdc
        0x41 -> readStat()
        0x42 -> scy
        0x43 -> scx
        0x44 -> ly().toByte()
        0x45 -> lyc
        0x46 -> dma
        0x47 -> bgp
        0x48 -> obp0
        0x49 -> obp1
        0x4A -> wy
        0x4B -> wx
        else -> 0xFF.toByte()
    }

    private fun positionAt(time: Long): Int = ((time - lcdOnAt) % FRAME_CYCLES).toInt()

    // LY moves on to the next line in the last 4 dots of the current one
    private fun ly(): Int = if (isEnabled) positionAt(scheduler.clock + 4) / SCANLINE_CYCLES else 0

    // bit 7 is not wired and reads as 1
    private fun readStat(): Byte = (0x80 or stat.toInt() or statusAt(scheduler.clock)).toByte()

    // STAT's low bits: the mode, and the LY=LYC bit
    private fun statusAt(time: Long): Int {
        if (!isEnabled) return lycWhileOff
        val position = positionAt(time)
        val line = position / SCANLINE_CYCLES
        val dot = position % SCANLINE_CYCLES
        // The LY=LYC bit drops while LY already shows the next line
        val coincidenceBit = if (line == lyc.toInt() and 0xFF && dot < LINE_END_DOT) 0x4 else 0
        val mode = when {
            // the first line after the LCD turns on skips the OAM scan
            time - lcdOnAt < OAM_CYCLES -> 0
            line >= SCREEN_HEIGHT -> 1
            dot < OAM_CYCLES -> 2
            dot < hblankDot(time - dot, line) -> 3
            else -> 0
        }
        return coincidenceBit or mode
    }

    // the enabled sources share one line, only its rising edge requests the interrupt
    private fun statLineAt(time: Long): Boolean {
        val status = statusAt(time)
        val mode = status and 0x3
        // enables 3 to 5 are modes 0 to 2, mode 3 has none
        return (mode != 3 && isBit(3 + mode, stat)) || (status and 0x4 != 0 && isBit(6, stat)) ||
            // DMG raises the mode 2 source once more as line 144 starts
            (isEnabled && isBit(5, stat) && positionAt(time) - VBLANK_START in 0..<4)
    }

    private fun requestOnRise(before: Boolean, time: Long, bus: Bus) {
        if (!before && statLineAt(time)) bus.requestInterrupt(LCD_INTERRUPT)
    }

    // Reads lose VRAM one M-cycle before mode 3, except on the line the LCD turns on
    fun vramReadBlocked(): Boolean {
        if (!isEnabled) return false
        val time = scheduler.clock
        val position = positionAt(time)
        val line = position / SCANLINE_CYCLES
        val dot = position % SCANLINE_CYCLES
        val from = if (time - dot == lcdOnAt) OAM_CYCLES else OAM_CYCLES - 4
        if (line >= SCREEN_HEIGHT || dot < from) return false
        return dot < OAM_CYCLES || dot < hblankDot(time - dot, line)
    }

    fun vramWriteBlocked(): Boolean {
        if (!isEnabled) return false
        val time = scheduler.clock
        val position = positionAt(time)
        val line = position / SCANLINE_CYCLES
        val dot = position % SCANLINE_CYCLES
        return line < SCREEN_HEIGHT && dot >= OAM_CYCLES && dot < hblankDot(time - dot, line)
    }

    fun readVram(addr: Int): Byte = vRam[addr]

    fun writeVram(addr: Int, value: Byte) {
        vRam[addr] = value
        if (addr < TILE_DATA_SIZE) {
            val row = addr and 1.inv()
            val low = vRam[row].toInt()
            val high = vRam[row + 1].toInt()
            val first = row * 4
            for (p in 0..7) {
                val bit = 7 - p
                val id = (low ushr bit and 1) or ((high ushr bit and 1) shl 1)
                tileIds[first + p] = id.toByte()
            }
        }
    }

    // Reads lose OAM as LY moves to a visible line, writes only once its OAM scan starts
    fun oamReadBlocked(): Boolean {
        if (!isEnabled) return false
        val time = scheduler.clock
        val position = positionAt(time)
        val line = position / SCANLINE_CYCLES
        val dot = position % SCANLINE_CYCLES
        if (dot >= LINE_END_DOT) return line < SCREEN_HEIGHT - 1 || line == LAST_LINE
        if (line >= SCREEN_HEIGHT || time - lcdOnAt < OAM_CYCLES) return false
        return dot < OAM_CYCLES || dot < hblankDot(time - dot, line)
    }

    // Writes still reach OAM in the last M-cycle of the OAM scan
    fun oamWriteBlocked(): Boolean {
        if (!isEnabled) return false
        val time = scheduler.clock
        val position = positionAt(time)
        val line = position / SCANLINE_CYCLES
        val dot = position % SCANLINE_CYCLES
        if (line >= SCREEN_HEIGHT || time - lcdOnAt < OAM_CYCLES) return false
        if (dot < OAM_CYCLES) return dot < OAM_CYCLES - 4
        return dot < hblankDot(time - dot, line)
    }

    private fun scannedOamRow(): Int {
        if (!isEnabled) return -1
        val time = scheduler.clock + 4
        if (time - lcdOnAt < OAM_CYCLES) return -1
        val position = positionAt(time)
        val dot = position % SCANLINE_CYCLES
        if (position / SCANLINE_CYCLES >= SCREEN_HEIGHT || dot >= OAM_CYCLES) return -1
        return dot / 4 * OAM_ROW
    }

    fun corruptOamWrite() = corruptScannedRow(false)

    fun corruptOamRead() = corruptScannedRow(true)

    private fun corruptScannedRow(read: Boolean) {
        val row = scannedOamRow()
        if (row <= 0) return
        drawDueLines(scheduler.clock)
        val preceding = row - OAM_ROW
        for (i in 0..1) {
            val a = oam[row + i].toInt()
            val b = oam[preceding + i].toInt()
            val c = oam[preceding + 4 + i].toInt()
            oam[row + i] = (if (read) b or (a and c) else ((a xor c) and (b xor c)) xor c).toByte()
        }
        oam.copyInto(oam, row + 2, preceding + 2, row)
    }

    fun corruptOamIncrease() {
        val row = scannedOamRow()
        if (row < 4 * OAM_ROW || row == LAST_OAM_ROW) return
        drawDueLines(scheduler.clock)
        val preceding = row - OAM_ROW
        for (i in 0..1) {
            val a = oam[preceding - OAM_ROW + i].toInt()
            val b = oam[preceding + i].toInt()
            val c = oam[row + i].toInt()
            val d = oam[preceding + 4 + i].toInt()
            oam[preceding + i] = ((b and (a or c or d)) or (a and c and d)).toByte()
        }
        oam.copyInto(oam, preceding - OAM_ROW, preceding, row)
        oam.copyInto(oam, row, preceding, row)
    }

    // Mode 3 is latched from the line's own state the first time it is asked for
    private fun hblankDot(lineStart: Long, line: Int): Int {
        if (lineStart != latchedLine) {
            latchedLine = lineStart
            latchedHblankDot = HBLANK_DOT + mode3Penalty(line)
        }
        return latchedHblankDot
    }

    // Fine scroll, the window and every object fetched lengthen mode 3 (Pan Docs' OBJ penalty)
    private fun mode3Penalty(line: Int): Int {
        val scrollX = scx.toInt() and 0xFF
        val windowX = (wx.toInt() and 0xFF) - 7
        val window = isBit(5, lcdc) && line >= (wy.toInt() and 0xFF) && windowX < SCREEN_WIDTH
        var penalty = (scrollX and 7) + if (window) 6 else 0
        if (!isBit(1, lcdc)) return penalty
        val count = orderSprites(line, spriteSize(lcdc), orderBuffer)
        var backgroundTiles = 0L
        var windowTiles = 0L
        var objects = 0
        for (i in 0..<count) {
            val x = oam[orderBuffer[i] + 1].toInt() and 0xFF
            if (x < SCREEN_WIDTH + 8) {
                val pixel = x - 8
                val inWindow = window && pixel >= windowX
                val column = if (inWindow) pixel - windowX else x + scrollX
                val tile = 1L shl (column ushr 3)
                val fetched = if (inWindow) windowTiles else backgroundTiles
                if (fetched and tile == 0L) {
                    // Waiting for the tile's fetch to finish, an object at X 0 always waits 5
                    penalty += if (x == 0) 5 else maxOf(0, 5 - (column and 7))
                    if (inWindow) {
                        windowTiles = windowTiles or tile
                    } else {
                        backgroundTiles = backgroundTiles or tile
                    }
                }
                penalty += 6
                objects++
            }
        }
        // Measured on DMG (mooneye intr_2_mode0_timing_sprites): fetched objects end mode 3 three dots early
        return if (objects > 0) penalty - 3 else penalty
    }

    fun write(ioAddress: Int, value: Byte, bus: Bus) {
        when (ioAddress) {
            0x40 -> {
                if (value == lcdc) return
                drawDueLines(scheduler.clock)
                val wasEnabled = isBit(7, lcdc)
                val lineBefore = statLineAt(scheduler.clock)
                if (wasEnabled) lycWhileOff = statusAt(scheduler.clock) and 0x4
                lcdc = value
                isEnabled = isBit(7, value)

                if (!isEnabled) {
                    scheduler.cancel(Event.PPU)
                    windowInternalLine = 0
                    windowTriggeredThisFrame = false
                    // the panel goes blank white while the LCD is off, not frozen on the last frame
                    frameBuffer.fill(color[0])
                    host.render(frameBuffer)
                }

                if (!wasEnabled and isEnabled) {
                    startTimeline()
                    requestOnRise(lineBefore, scheduler.clock, bus)
                }
            }
            0x41 -> {
                val lineBefore = statLineAt(scheduler.clock)
                stat = value and 0xF8.toByte()
                requestOnRise(lineBefore, scheduler.clock, bus)
                if (isEnabled) scheduleNext(scheduler.clock)
            }
            0x42 -> {
                drawDueLines(scheduler.clock)
                scy = value
            }
            0x43 -> {
                drawDueLines(scheduler.clock)
                scx = value
            }
            0x44 -> Unit // LY is read only
            0x45 -> {
                val lineBefore = statLineAt(scheduler.clock)
                lyc = value
                requestOnRise(lineBefore, scheduler.clock, bus)
                if (isEnabled) scheduleNext(scheduler.clock)
            }
            0x46 -> {
                drawDueLines(scheduler.clock)
                dma = value
                bus.handleDma(value) // todo internalize
            }
            0x47 -> {
                if (value == bgp) return
                drawDueLines(scheduler.clock)
                bgp = value
                // (palette shr colorId * 2) and 0x3
                cachePalette(backgroundPalette, color, value)
            }
            0x48 -> {
                if (value == obp0) return
                drawDueLines(scheduler.clock)
                obp0 = value
                cachePalette(objectPalette0, color, value)
            }
            0x49 -> {
                if (value == obp1) return
                drawDueLines(scheduler.clock)
                obp1 = value
                cachePalette(objectPalette1, color, value)
            }
            0x4A -> {
                drawDueLines(scheduler.clock)
                wy = value
            }
            0x4B -> {
                drawDueLines(scheduler.clock)
                wx = value
            }
        }
    }

    private fun cachePalette(cachedPalette: IntArray, colors: IntArray, palette: Byte) {
        cachedPalette[0] = colors[palette.toInt() and 0x3]
        cachedPalette[1] = colors[palette.toInt() ushr 2 and 0x3]
        cachedPalette[2] = colors[palette.toInt() ushr 4 and 0x3]
        cachedPalette[3] = colors[palette.toInt() ushr 6 and 0x3]
    }

    private fun startTimeline() {
        lcdOnAt = scheduler.clock
        renderFrame = lcdOnAt
        renderedLines = 0
        scheduleNext(scheduler.clock)
    }

    fun onEvent(bus: Bus) {
        val at = scheduler.firedAt
        val position = positionAt(at)
        val line = position / SCANLINE_CYCLES
        val dot = position % SCANLINE_CYCLES

        if (line == SCREEN_HEIGHT && dot == 0) {
            drawDueLines(at)
            windowInternalLine = 0
            windowTriggeredThisFrame = false
            host.render(frameBuffer)
            bus.requestInterrupt(VBLANK_INTERRUPT)
        }
        requestOnRise(statLineAt(at - 1), at, bus)

        scheduleNext(at)
    }

    private fun scheduleNext(after: Long) {
        val frame = frameOf(after)
        var next = nextInFrame(frame, VBLANK_START, after)
        if (isBit(3, stat)) next = minOf(next, nextHblank(frame, after))
        if (isBit(5, stat)) next = minOf(next, nextVisibleLine(frame, 0, after))
        val compare = lyc.toInt() and 0xFF
        if (isBit(6, stat) && compare <= LAST_LINE) {
            next = minOf(next, nextInFrame(frame, compare * SCANLINE_CYCLES, after))
        }
        scheduler.scheduleAt(Event.PPU, next)
    }

    fun nextFrameEnd(after: Long): Long {
        if (!isEnabled) return after + FRAME_CYCLES
        return nextInFrame(frameOf(after), VBLANK_START, after)
    }

    private fun frameOf(time: Long): Long = lcdOnAt + (time - lcdOnAt) / FRAME_CYCLES * FRAME_CYCLES

    private fun nextInFrame(frame: Long, offset: Int, after: Long): Long {
        val at = frame + offset
        return if (at > after) at else at + FRAME_CYCLES
    }

    // A line's hblank is only known once its mode 3 starts, so wake there first
    private fun nextHblank(frame: Long, after: Long): Long {
        val position = (after - frame).toInt()
        val line = position / SCANLINE_CYCLES
        if (line < SCREEN_HEIGHT && position % SCANLINE_CYCLES >= OAM_CYCLES) {
            val lineStart = frame + line * SCANLINE_CYCLES
            val hblank = lineStart + hblankDot(lineStart, line)
            if (hblank > after) return hblank
        }
        return nextVisibleLine(frame, OAM_CYCLES, after)
    }

    private fun nextVisibleLine(frame: Long, dot: Int, after: Long): Long {
        val position = (after - frame).toInt()
        var line = position / SCANLINE_CYCLES
        if (position % SCANLINE_CYCLES >= dot) line++
        if (line >= SCREEN_HEIGHT) return frame + FRAME_CYCLES + dot
        return frame + line * SCANLINE_CYCLES + dot
    }

    fun drawDueLines(time: Long) {
        if (!isEnabled) return
        if (renderedLines == SCREEN_HEIGHT && time < renderFrame + FRAME_CYCLES) return
        val frame = frameOf(time)
        if (frame != renderFrame) {
            renderFrame = frame
            renderedLines = 0
        }
        val ended = ((time - frame).toInt() + SCANLINE_CYCLES - HBLANK_DOT) / SCANLINE_CYCLES
        val due = minOf(SCREEN_HEIGHT, ended)
        while (renderedLines < due) {
            drawScanLine(renderedLines)
            renderedLines++
        }
    }

    private fun drawScanLine(line: Int) {
        if (isBit(0, lcdc)) { // Bit 0 - BG Display (0=Off, 1=On)
            renderBG(line)
        } else {
            blankScanLine(line)
        }
        if (isBit(1, lcdc)) { // Bit 1 - OBJ (Sprite) Display Enable
            renderSpritesBuffer(line)
        }
    }

    // BG off renders white, and counts as colour 0 everywhere for sprite priority
    private fun blankScanLine(line: Int) {
        val start = line * SCREEN_WIDTH
        frameBuffer.fill(color[0], start, start + SCREEN_WIDTH)
        lineIds.fill(0)
    }

    private fun renderBG(line: Int) {
        val WX = (wx.toInt() and 0xFF) - 7 // WX needs -7 Offset
        val WY = wy.toInt() and 0xFF
        val SCY = scy.toInt() and 0xFF
        val SCX = scx.toInt() and 0xFF
        if (line == WY) windowTriggeredThisFrame = true
        val isWin = isBit(5, lcdc) && windowTriggeredThisFrame

        // Where the window takes over
        val split = if (isWin) WX.coerceIn(0, SCREEN_WIDTH) else SCREEN_WIDTH

        drawSegment(window = false, 0, split, SCX, (line + SCY) and 0xFF)
        drawSegment(window = true, split, SCREEN_WIDTH, -WX, windowInternalLine)

        for (p in 0..<SCREEN_WIDTH) {
            frameBuffer.write(p, line, backgroundPalette[lineIds[p].toInt()])
        }

        if (split < SCREEN_WIDTH) {
            windowInternalLine++
        }
    }

    // Fetch and emit tile rows over [from, end), the source scrolled by offset
    private fun drawSegment(window: Boolean, from: Int, end: Int, offset: Int, y: Int) {
        var p = from
        while (p < end) {
            val x = (p + offset) and 0xFF
            p += emitTileRow(fetchTileRow(window, x / 8, y), x and 7, p, end)
        }
    }

    // Where one tile row starts in tileIds; LCDC is sampled per fetch
    private fun fetchTileRow(window: Boolean, tileCol: Int, y: Int): Int {
        val tileMap = if (window) {
            getWindowTileMapOffset(lcdc)
        } else {
            getBackgroundTileMapOffset(lcdc)
        }
        val tile = vRam[tileMap + y / 8 * 32 + tileCol]
        // Bit 4 - BG & Window Tile Data Select (0 = signed from 9000, 1 = 8000-8FFF), in tiles
        val tileIndex = if (isBit(4, lcdc)) tile.toInt() and 0xFF else 256 + tile
        return (tileIndex * 8 + (y and 7)) * 8
    }

    // Pixels of the tile row at [row] from [start] on, stopping at [end]; returns how many were written
    private fun emitTileRow(row: Int, start: Int, p: Int, end: Int): Int {
        val count = minOf(8 - start, end - p)
        if (count == 8) {
            lineIds[p] = tileIds[row]
            lineIds[p + 1] = tileIds[row + 1]
            lineIds[p + 2] = tileIds[row + 2]
            lineIds[p + 3] = tileIds[row + 3]
            lineIds[p + 4] = tileIds[row + 4]
            lineIds[p + 5] = tileIds[row + 5]
            lineIds[p + 6] = tileIds[row + 6]
            lineIds[p + 7] = tileIds[row + 7]
            return 8
        }
        for (i in 0..<count) {
            lineIds[p + i] = tileIds[row + start + i]
        }
        return count
    }

    private val orderBuffer = IntArray(MAX_LINE_OBJECTS) // Oam Indexes

    // The line shows the first 10 sprites in OAM order that cover it, returned ordered by X
    private fun orderSprites(line: Int, size: Int, orderBuffer: IntArray): Int {
        var count = 0
        for (i in 0..oam.lastIndex step 4) {
            val y = (oam[i].toInt() and 0xFF) - 16
            val visible = (line >= y) && (line < (y + size))
            if (visible) {
                orderBuffer[count++] = i
                if (count == MAX_LINE_OBJECTS) break
            }
        }

        insertionSortOamOrderBuffer(indexes = orderBuffer, lastIndexExclusive = count)
        return count
    }

    private fun insertionSortOamOrderBuffer(indexes: IntArray, lastIndexExclusive: Int) {
        for (i in 1..<lastIndexExclusive) {
            val keyIndex = indexes[i]
            val keyValue = oam[keyIndex + 1].toInt() and 0xFF
            var j = i - 1

            while (j >= 0 && oam[indexes[j] + 1].toInt() and 0xFF > keyValue) {
                indexes[j + 1] = indexes[j]
                j--
            }
            indexes[j + 1] = keyIndex
        }
    }

    private fun renderSpritesBuffer(line: Int) {
        val spriteSize = spriteSize(lcdc)

        // 0x9F OAM Size, 40 Sprites x 4 bytes filtering:
        // Out of y range and ordered by x limited to 10
        var orderBufferPointer = orderSprites(line, spriteSize, orderBuffer)

        // Highest X first, so the lowest X is drawn last and wins
        while (--orderBufferPointer >= 0) {
            val index = orderBuffer[orderBufferPointer]
            val x = (oam[index + 1].toInt() and 0xFF) - 8 // Byte1 - X Position //needs 8 offset
            // Out of range X values are not drawn but will consume
            // sprite object slots towards the 10 limit (hence not filtering them on the mmu)
            if (x <= -8 || x >= SCREEN_WIDTH) continue

            val y = (oam[index].toInt() and 0xFF) - 16 // Byte0 - Y Position //needs 16 offset
            val tile = oam[index + 2].toInt() and 0xFF // Byte2 - Tile/Pattern Number
            val attr = oam[index + 3] // Byte3 - Attributes/Flags
            val tileIndex = tile and (spriteSize shr 4).inv()

            // Bit4   Palette number  **Non CGB Mode Only** (0=OBP0, 1=OBP1)
            val palette = if (isBit(4, attr)) objectPalette1 else objectPalette0

            val tileRow = if (isYFlipped(attr)) spriteSize - 1 - (line - y) else line - y

            // Sprites always take their tiles from 0x8000, the start of tileIds
            val row = (tileIndex * 8 + tileRow) * 8
            val above = isAboveBG(attr)
            // Flipped in X reads the row from the other end
            val flipped = isXFlipped(attr)
            val first = if (flipped) row + 7 else row
            val step = if (flipped) -1 else 1

            // Clamp to the screen once instead of testing every pixel
            val from = if (x < 0) -x else 0
            val to = if (x + 8 > SCREEN_WIDTH) SCREEN_WIDTH - x else 8
            for (p in from..<to) {
                val colorId = tileIds[first + step * p].toInt()
                if (colorId != 0 && (above || lineIds[x + p].toInt() == 0)) {
                    frameBuffer.write(x + p, line, palette[colorId])
                }
            }
        }
    }

    private inline fun spriteSize(LCDC: Byte): Int {
        // Bit 2 - OBJ (Sprite) Size (0=8x8, 1=8x16)
        return if (isBit(2, LCDC)) 16 else 8
    }

    private inline fun isXFlipped(attr: Byte): Boolean {
        // Bit5   X flip(0 = Normal, 1 = Horizontally mirrored)
        return isBit(5, attr)
    }

    private inline fun isYFlipped(attr: Byte): Boolean {
        // Bit6 Y flip(0 = Normal, 1 = Vertically mirrored)
        return isBit(6, attr)
    }

    private inline fun isAboveBG(attr: Byte): Boolean {
        // Bit7 OBJ-to - BG Priority(0 = OBJ Above BG, 1 = OBJ Behind BG color 1 - 3)
        return attr.toUInt() and 0x80u == 0u
    }

    private inline fun getBackgroundTileMapOffset(LCDC: Byte): Int {
        // Bit 3 - BG Tile Map Display Select     (0=9800-9BFF, 1=9C00-9FFF), as vRam offsets
        return if (isBit(3, LCDC)) 0x1C00 else 0x1800
    }

    private inline fun getWindowTileMapOffset(LCDC: Byte): Int {
        // Bit 6 - Window Tile Map Display Select(0 = 9800 - 9BFF, 1 = 9C00 - 9FFF), as vRam offsets
        return if (isBit(6, LCDC)) 0x1C00 else 0x1800
    }

    private inline fun IntArray.write(x: Int, y: Int, color: Int) {
        this[x + (y * SCREEN_WIDTH)] = color
    }

    fun reset() {
        lcdc = 0
        isEnabled = false
        lycWhileOff = 0
        stat = 0
        scy = 0
        scx = 0
        lyc = 0
        dma = 0
        bgp = 0
        obp0 = 0
        obp1 = 0
        wy = 0
        wx = 0
        lcdOnAt = 0
        renderFrame = 0
        renderedLines = 0
        latchedLine = Long.MIN_VALUE
        windowInternalLine = 0
        windowTriggeredThisFrame = false
        backgroundPalette.fill(0)
        objectPalette0.fill(0)
        objectPalette1.fill(0)
        lineIds.fill(0)
        vRam.fill(0)
        tileIds.fill(0)
        oam.fill(0)
        frameBuffer.fill(color[0])
        host.render(frameBuffer)
    }

    companion object {
        private val colorPocket = intArrayOf(
            0xFFFFFFFF.toInt(),
            0xFFA9A9A9.toInt(),
            0xFF545454.toInt(),
            0xFF000000.toInt(),
        )
        private val color = intArrayOf(
            0xFF9AA13C.toInt(),
            0xFF6c712a.toInt(),
            0xFF4d511e.toInt(),
            0xFF1f200c.toInt(),
        )

        private const val SCREEN_WIDTH = 160
        private const val SCREEN_HEIGHT = 144
        private const val LAST_LINE = 153
        private const val OAM_CYCLES = 80
        private const val VRAM_CYCLES = 172
        private const val HBLANK_DOT = OAM_CYCLES + VRAM_CYCLES
        private const val SCANLINE_CYCLES = 456
        private const val LINE_END_DOT = SCANLINE_CYCLES - 4
        private const val TILE_DATA_SIZE = 0x1800
        private const val TILE_COUNT = 384
        private const val TILE_PIXELS = 8 * 8
        private const val OAM_ROW = 8
        private const val LAST_OAM_ROW = 19 * OAM_ROW
        private const val MAX_LINE_OBJECTS = 10
        private const val VBLANK_START = SCREEN_HEIGHT * SCANLINE_CYCLES
        private const val FRAME_CYCLES = SCANLINE_CYCLES * (LAST_LINE + 1)

        private const val VBLANK_INTERRUPT: Byte = 0x1
        private const val LCD_INTERRUPT: Byte = 0x2
    }
}
