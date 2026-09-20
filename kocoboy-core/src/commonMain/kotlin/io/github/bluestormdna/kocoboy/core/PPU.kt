package io.github.bluestormdna.kocoboy.core

import io.github.bluestormdna.kocoboy.host.Host
import kotlin.experimental.and

class PPU(private val host: Host, private val scheduler: Scheduler) {

    private var windowInternalLine = 0
    private var windowTriggeredThisFrame = false
    private val frameBuffer = IntArray(160 * 144)

    // per scanline BG colour ids, sprite priority is decided on the id not the shade
    private val bgColorZero = BooleanArray(160)

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

    private var lcdOnAt: Long = 0

    private var renderFrame: Long = 0
    private var renderedLines = 0

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

    private fun ly(): Int = if (isEnabled) positionAt(scheduler.clock) / SCANLINE_CYCLES else 0

    private fun coincidence(): Boolean = ly() == lyc.toInt() and 0xFF

    private fun readStat(): Byte {
        if (!isEnabled) return (stat.toInt() or (if (coincidence()) 0x4 else 0)).toByte()
        val position = positionAt(scheduler.clock)
        val line = position / SCANLINE_CYCLES
        val dot = position % SCANLINE_CYCLES
        val coincidenceBit = if (line == lyc.toInt() and 0xFF) 0x4 else 0
        val mode = when {
            line >= SCREEN_HEIGHT -> 1
            dot < OAM_CYCLES -> 2
            dot < HBLANK_DOT -> 3
            else -> 0
        }
        return (stat.toInt() or coincidenceBit or mode).toByte()
    }

    fun write(ioAddress: Int, value: Byte, bus: Bus) {
        when (ioAddress) {
            0x40 -> {
                if (value == lcdc) return
                drawDueLines(scheduler.clock, bus)
                val wasEnabled = isBit(7, lcdc)
                lcdc = value
                isEnabled = isBit(7, value)

                if (!isEnabled) {
                    scheduler.cancel(Event.PPU)
                    windowInternalLine = 0
                    windowTriggeredThisFrame = false
                    // the panel goes blank white while the LCD is off, not frozen on the last frame
                    frameBuffer.fill(color[0])
                    bgColorZero.fill(true)
                    host.render(frameBuffer)
                }

                if (!wasEnabled and isEnabled) {
                    startTimeline()
                    if (coincidence() && isBit(6, stat)) bus.requestInterrupt(LCD_INTERRUPT)
                }
            }
            0x41 -> {
                stat = value and 0xF8.toByte()
                if (isEnabled) scheduleNext(scheduler.clock)
            }
            0x42 -> {
                drawDueLines(scheduler.clock, bus)
                scy = value
            }
            0x43 -> {
                drawDueLines(scheduler.clock, bus)
                scx = value
            }
            0x44 -> Unit // LY is read only
            0x45 -> {
                lyc = value
                if (coincidence() && isBit(6, stat)) bus.requestInterrupt(LCD_INTERRUPT)
                if (isEnabled) scheduleNext(scheduler.clock)
            }
            0x46 -> {
                drawDueLines(scheduler.clock, bus)
                dma = value
                bus.handleDma(value) // todo internalize
            }
            0x47 -> {
                if (value == bgp) return
                drawDueLines(scheduler.clock, bus)
                bgp = value
                // (palette shr colorId * 2) and 0x3
                cachePalette(backgroundPalette, color, value)
            }
            0x48 -> {
                if (value == obp0) return
                drawDueLines(scheduler.clock, bus)
                obp0 = value
                cachePalette(objectPalette0, color, value)
            }
            0x49 -> {
                if (value == obp1) return
                drawDueLines(scheduler.clock, bus)
                obp1 = value
                cachePalette(objectPalette1, color, value)
            }
            0x4A -> {
                drawDueLines(scheduler.clock, bus)
                wy = value
            }
            0x4B -> {
                drawDueLines(scheduler.clock, bus)
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
            drawDueLines(at, bus)
            windowInternalLine = 0
            windowTriggeredThisFrame = false
            host.render(frameBuffer)
            bus.requestInterrupt(VBLANK_INTERRUPT)
            if (isBit(4, stat)) bus.requestInterrupt(LCD_INTERRUPT)
        }
        if (line < SCREEN_HEIGHT) {
            if (dot == HBLANK_DOT && isBit(3, stat)) bus.requestInterrupt(LCD_INTERRUPT)
            if (dot == 0 && isBit(5, stat)) bus.requestInterrupt(LCD_INTERRUPT)
        }
        if (dot == 0 && line == lyc.toInt() and 0xFF && isBit(6, stat)) {
            bus.requestInterrupt(LCD_INTERRUPT)
        }

        scheduleNext(at)
    }

    private fun scheduleNext(after: Long) {
        val frame = frameOf(after)
        var next = nextInFrame(frame, VBLANK_START, after)
        if (isBit(3, stat)) next = minOf(next, nextVisibleLine(frame, HBLANK_DOT, after))
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

    private fun nextVisibleLine(frame: Long, dot: Int, after: Long): Long {
        val position = (after - frame).toInt()
        var line = position / SCANLINE_CYCLES
        if (position % SCANLINE_CYCLES >= dot) line++
        if (line >= SCREEN_HEIGHT) return frame + FRAME_CYCLES + dot
        return frame + line * SCANLINE_CYCLES + dot
    }

    fun drawDueLines(time: Long, bus: Bus) {
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
            drawScanLine(renderedLines, bus)
            renderedLines++
        }
    }

    private fun drawScanLine(line: Int, bus: Bus) {
        if (isBit(0, lcdc)) { // Bit 0 - BG Display (0=Off, 1=On)
            renderBG(line, bus)
        } else {
            blankScanLine(line)
        }
        if (isBit(1, lcdc)) { // Bit 1 - OBJ (Sprite) Display Enable
            renderSpritesBuffer(line, bus)
        }
    }

    // BG off renders white, and counts as colour 0 everywhere for sprite priority
    private fun blankScanLine(line: Int) {
        for (p in 0..<SCREEN_WIDTH) {
            frameBuffer.write(p, line, color[0])
            bgColorZero[p] = true
        }
    }

    private fun renderBG(line: Int, bus: Bus) {
        val WX = (wx.toInt() and 0xFF) - 7 // WX needs -7 Offset
        val WY = wy.toInt() and 0xFF
        val SCY = scy.toInt() and 0xFF
        val SCX = scx.toInt() and 0xFF
        if (line == WY) windowTriggeredThisFrame = true
        val isWin = isBit(5, lcdc) && windowTriggeredThisFrame

        val windowTileMapAddress = getWindowTileMapAddress(lcdc)
        val bgTileMapAddress = getBackgroundTileMapAddress(lcdc)
        var hi: Byte = 0
        var lo: Byte = 0

        var windowAppeared = false
        for (p in 0..<SCREEN_WIDTH) {
            val inWin = isWin && p >= WX
            windowAppeared = windowAppeared or inWin
            val x = if (inWin) (p - WX) and 0xFF else (p + SCX) and 0xFF
            if (p == 0 || (x and 0x7) == 0) {
                val tileCol = x / 8
                val tileMap = if (inWin) windowTileMapAddress else bgTileMapAddress
                val y = if (inWin) windowInternalLine else (line + SCY) and 0xFF

                val tileLine = (y and 7) * 2
                val tileRow = y / 8 * 32
                val tileAddress = tileMap + tileRow + tileCol

                val tileLoc = if (isSignedAddress(lcdc)) {
                    getTileDataAddress(lcdc) + bus.readVRAM(tileAddress) * 16
                } else {
                    // Signed
                    getTileDataAddress(lcdc) + (bus.readVRAM(tileAddress).toByte() + 128) * 16
                }

                lo = bus.readVRAM((tileLoc + tileLine)).toByte()
                hi = bus.readVRAM((tileLoc + tileLine + 1)).toByte()
            }

            val colorBit = 7 - (x and 7) // reversed
            val colorId = getColorIdBits(colorBit, lo, hi)
            val color = backgroundPalette[colorId]

            bgColorZero[p] = colorId == 0
            frameBuffer.write(p, line, color)
        }

        if (windowAppeared) {
            windowInternalLine++
        }
    }

    private val orderBuffer = IntArray(40 + 1) // Oam Indexes plus terminator

    private fun renderSpritesBuffer(line: Int, bus: Bus) {
        val spriteSize = spriteSize(lcdc)

        // 0x9F OAM Size, 40 Sprites x 4 bytes filtering:
        // Out of y range and ordered by x limited to 10
        bus.orderSprites(line, spriteSize, orderBuffer)

        var orderBufferPointer = 0
        while (orderBuffer[orderBufferPointer] != -1) {
            val index = orderBuffer[orderBufferPointer]
            val x = bus.readOAM(index + 1) - 8 // Byte1 - X Position //needs 8 offset
            // Out of range X values are not drawn but will consume
            // sprite object slots towards the 10 limit (hence not filtering them on the mmu)
            orderBufferPointer++
            if (x <= -8 || x >= 160) continue

            val y = bus.readOAM(index) - 16 // Byte0 - Y Position //needs 16 offset
            val tile = bus.readOAM(index + 2) // Byte2 - Tile/Pattern Number
            val attr = bus.readOAM(index + 3).toByte() // Byte3 - Attributes/Flags
            val tileIndex = tile and (spriteSize shr 4).inv()

            // Bit4   Palette number  **Non CGB Mode Only** (0=OBP0, 1=OBP1)
            val palette = if (isBit(4, attr)) objectPalette1 else objectPalette0

            val tileRow = if (isYFlipped(attr)) {
                spriteSize - 1 - (line - y)
            } else {
                (
                    line -
                        y
                    )
            }

            val tileAddress = ((0x8000 + (tileIndex * 16) + (tileRow * 2)))
            val lo = bus.readVRAM(tileAddress)
            val hi = bus.readVRAM(tileAddress + 1)

            for (p in 0..7) {
                if ((x + p) >= 0 && (x + p) < SCREEN_WIDTH) {
                    val idPos = if (isXFlipped(attr)) p else 7 - p
                    val colorId: Int = getColorIdBits(idPos, lo.toByte(), hi.toByte())

                    if (!isTransparent(colorId) &&
                        (isAboveBG(attr) || bgColorZero[x + p])
                    ) {
                        val color = palette[colorId]
                        frameBuffer.write(x + p, line, color)
                    }
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

    private inline fun isTransparent(b: Int): Boolean = b == 0

    private inline fun isAboveBG(attr: Byte): Boolean {
        // Bit7 OBJ-to - BG Priority(0 = OBJ Above BG, 1 = OBJ Behind BG color 1 - 3)
        return attr.toUInt() and 0x80u == 0u
    }

    private inline fun isSignedAddress(LCDC: Byte): Boolean {
        // Bit 4 - BG & Window Tile Data Select   (0=8800-97FF, 1=8000-8FFF)
        return isBit(4, LCDC)
    }

    private inline fun getBackgroundTileMapAddress(LCDC: Byte): Int {
        // Bit 3 - BG Tile Map Display Select     (0=9800-9BFF, 1=9C00-9FFF)
        return if (isBit(3, LCDC)) 0x9C00 else 0x9800
    }

    private inline fun getWindowTileMapAddress(LCDC: Byte): Int {
        // Bit 6 - Window Tile Map Display Select(0 = 9800 - 9BFF, 1 = 9C00 - 9FFF)
        return if (isBit(6, LCDC)) 0x9C00 else 0x9800
    }

    private inline fun getTileDataAddress(LCDC: Byte): Int {
        // Bit 4 - BG & Window Tile Data Select   (0=8800-97FF, 1=8000-8FFF)
        return if (isBit(4, LCDC)) 0x8000 else 0x8800 // 0x8800 signed area
    }

    private inline fun IntArray.read(x: Int, y: Int): Int = this[x + (y * SCREEN_WIDTH)]

    private inline fun IntArray.write(x: Int, y: Int, color: Int) {
        this[x + (y * SCREEN_WIDTH)] = color
    }

    private fun getColorIdBits(colorBit: Int, l: Byte, h: Byte): Int {
        val hi = (h.toInt() shr colorBit) and 0x1
        val lo = (l.toInt() shr colorBit) and 0x1
        return (hi shl 1 or lo) and 0xFF
    }

    fun reset() {
        lcdc = 0
        isEnabled = false
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
        windowInternalLine = 0
        windowTriggeredThisFrame = false
        backgroundPalette.fill(0)
        objectPalette0.fill(0)
        objectPalette1.fill(0)
        bgColorZero.fill(false)
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
        private const val VBLANK_START = SCREEN_HEIGHT * SCANLINE_CYCLES
        private const val FRAME_CYCLES = SCANLINE_CYCLES * (LAST_LINE + 1)

        private const val VBLANK_INTERRUPT: Byte = 0x1
        private const val LCD_INTERRUPT: Byte = 0x2
    }
}
