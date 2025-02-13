package io.github.bluestormdna.kocoboy.core

import io.github.bluestormdna.kocoboy.host.Host
import kotlin.experimental.and
import kotlin.experimental.inv

class PPU(private val host: Host) {

    private var scanlineCounter = 0
    private var windowInternalLine = 0
    private val frameBuffer = IntArray(160 * 144)

    // PPU Regs
    private var lcdc: Byte = 0 //FF40 - LCDC - LCD Control (R/W)
    private var stat: Byte = 0 //FF41 - STAT - LCDC Status (R/W)
    private var scy: Byte = 0  //FF42 - SCY - Scroll Y (R/W)
    private var scx: Byte = 0  //FF43 - SCX - Scroll X (R/W)
    private var ly: Byte = 0   //FF44 - LY - LCDC Y-Coordinate (R) bypasses on write always 0
    private var lyc: Byte = 0  //FF45 - LYC - LY Compare(R/W)
    private var bgp: Byte = 0  //FF47 - BGP - BG Palette Data(R/W) - Non CGB Mode Only
    private var obp0: Byte = 0 //FF48 - OBP0 - Object Palette 0 Data (R/W) - Non CGB Mode Only
    private var obp1: Byte = 0 //FF49 - OBP1 - Object Palette 1 Data (R/W) - Non CGB Mode Only
    private var wy: Byte = 0   //FF4A - WY - Window Y Position (R/W)
    private var wx: Byte = 0   //FF4B - WX - Window X Position minus 7 (R/W)

    // lcdc bit fields
    private var isEnabled: Boolean = false

    // Cached palettes
    private val backgroundPalette = IntArray(4)
    private val objectPalette0 = IntArray(4)
    private val objectPalette1 = IntArray(4)

    fun read(ioAddress: Int): Byte {
        return when (ioAddress) {
            0x40 -> lcdc
            0x41 -> stat
            0x42 -> scy
            0x43 -> scx
            0x44 -> ly
            0x45 -> lyc
            0x47 -> bgp
            0x48 -> obp0
            0x49 -> obp1
            0x4A -> wy
            0x4B -> wx
            else -> 0xFF.toByte()
        }
    }

    fun write(ioAddress: Int, value: Byte, bus: Bus) {
        when (ioAddress) {
            0x40 -> {
                if (value == lcdc) return
                lcdc = value
                isEnabled = isBit(7, value)

                if (!isEnabled) {
                    ly = 0
                    scanlineCounter = 0
                    windowInternalLine = 0
                    stat = (stat and 0x3.toByte().inv())
                }
                handleCoincidenceFlag(bus)
            }
            0x41 -> stat = value
            0x42 -> scy = value
            0x43 -> scx = value
            0x44 -> {
                ly = 0
                handleCoincidenceFlag(bus)
            }
            0x45 -> {
                lyc = value
                handleCoincidenceFlag(bus)
            }
            0x46 -> bus.handleDma(value) //todo internalize
            0x47 -> {
                if (value == bgp) return
                bgp = value
                // (palette shr colorId * 2) and 0x3
                cachePalette(backgroundPalette, color, value)
            }
            0x48 -> {
                if (value == obp0) return
                obp0 = value
                cachePalette(objectPalette0, color, value)
            }
            0x49 -> {
                if (value == obp1) return
                obp1 = value
                cachePalette(objectPalette1, color, value)
            }
            0x4A -> wy = value
            0x4B -> wx = value
        }
    }

    private fun cachePalette(cachedPalette: IntArray, colors: IntArray, palette: Byte) {
        cachedPalette[0] = colors[palette.toInt() and 0x3]
        cachedPalette[1] = colors[palette.toInt() ushr 2 and 0x3]
        cachedPalette[2] = colors[palette.toInt() ushr 4 and 0x3]
        cachedPalette[3] = colors[palette.toInt() ushr 6 and 0x3]
    }

    fun update(cycles: Int, bus: Bus) {
        if (!isEnabled) return

        scanlineCounter += cycles

        when ((stat and 0x3).toInt()) {
            Mode.OAM -> if (scanlineCounter >= OAM_CYCLES) {
                scanlineCounter -= OAM_CYCLES
                updateStatMode(3)
                if (isBit(5, stat)) {
                    bus.requestInterrupt(LCD_INTERRUPT)
                }
            }

            Mode.VRAM -> if (scanlineCounter >= VRAM_CYCLES) {
                scanlineCounter -= VRAM_CYCLES
                updateStatMode(0)
                drawScanLine(bus)
            }

            Mode.HBLANK -> if (scanlineCounter >= HBLANK_CYCLES) {
                scanlineCounter -= HBLANK_CYCLES
                ly++
                handleCoincidenceFlag(bus)

                if (isBit(3, stat)) {
                    bus.requestInterrupt(LCD_INTERRUPT)
                }

                if (ly.toInt() and 0xFF == SCREEN_HEIGHT) { //check if we arrived Vblank
                    updateStatMode(1) // Set VBlank
                    bus.requestInterrupt(VBLANK_INTERRUPT)
                    host.render(frameBuffer)
                } else { //not arrived yet so return to mode 2 / OAM
                    updateStatMode(2)
                }
            }

            Mode.VBLANK -> if (scanlineCounter >= SCANLINE_CYCLES) {
                scanlineCounter -= SCANLINE_CYCLES
                ly++
                handleCoincidenceFlag(bus)

                if (isBit(4, stat)) {
                    bus.requestInterrupt(LCD_INTERRUPT)
                }

                if ((ly.toInt() and 0xFF) > SCREEN_VBLANK_HEIGHT) { //check end of VBLANK
                    updateStatMode(2)
                    ly = 0
                    handleCoincidenceFlag(bus)
                }
            }
        }
    }

    private fun handleCoincidenceFlag(bus: Bus) {
        if (ly == lyc) {
            stat = bitSet(2, stat)
            if (isBit(6, stat)) {
                bus.requestInterrupt(LCD_INTERRUPT)
            }
        } else {
            stat = bitClear(2, stat)
        }
    }

    private fun updateStatMode(mode: Int) {
        val oldStat = stat.toUInt() and 0x3u.inv()
        stat = (oldStat or mode.toUInt()).toByte()
    }

    private fun drawScanLine(bus: Bus) {
        if (isBit(0, lcdc)) { //Bit 0 - BG Display (0=Off, 1=On)
            renderBG(bus)
        }
        if (isBit(1, lcdc)) { //Bit 1 - OBJ (Sprite) Display Enable
            //val time = measureTime {
            renderSpritesBuffer(bus)
            //}
            //println("meassured: $time on ${mmu.LY.toInt() and 0xFF}")
        }
    }

    private fun renderBG(bus: Bus) {
        val WX = ((wx.toInt() and 0xFF) - 7 and 0xFF) //WX needs -7 Offset
        val WY = wy.toInt() and 0xFF
        val LY = ly.toInt() and 0xFF
        val SCY = scy.toInt() and 0xFF
        val SCX = scx.toInt() and 0xFF
        val isWin = isWindow(lcdc, WY, LY)

        if (LY == WY) windowInternalLine = 0

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
                val y = if (inWin) windowInternalLine else (LY + SCY) and 0xFF

                val tileLine = (y and 7) * 2
                val tileRow = y / 8 * 32
                val tileAddress = tileMap + tileRow + tileCol

                val tileLoc = if (isSignedAddress(lcdc)) {
                    getTileDataAddress(lcdc) + bus.readVRAM(tileAddress) * 16
                } else {
                    getTileDataAddress(lcdc) + (bus.readVRAM(tileAddress).toByte() /*sbyte*/ + 128) * 16
                }

                lo = bus.readVRAM((tileLoc + tileLine)).toByte()
                hi = bus.readVRAM((tileLoc + tileLine + 1)).toByte()
            }

            val colorBit = 7 - (x and 7) // reversed
            val colorId = getColorIdBits(colorBit, lo, hi)
            val color = backgroundPalette[colorId]

            frameBuffer.write(p, LY, color)
        }

        if (windowAppeared) {
            windowInternalLine++
        }
    }

    private val orderBuffer = IntArray(40 + 1) // Oam Indexes plus terminator

    private fun renderSpritesBuffer(bus: Bus) {
        val unsignedLy = ly.toInt() and 0xFF
        val spriteSize = spriteSize(lcdc)

        // 0x9F OAM Size, 40 Sprites x 4 bytes filtering:
        // Out of y range and ordered by x limited to 10
        bus.orderSprites(unsignedLy, spriteSize, orderBuffer)

        var orderBufferPointer = 0
        while (orderBuffer[orderBufferPointer] != -1) {
            val index = orderBuffer[orderBufferPointer]
            val x = bus.readOAM(index + 1) - 8 //Byte1 - X Position //needs 8 offset
            // Out of range X values are not drawn but will consume
            // sprite object slots towards the 10 limit (hence not filtering them on the mmu)
            orderBufferPointer++
            if (x <= -8 || x >= 160) continue

            val y = bus.readOAM(index) - 16 //Byte0 - Y Position //needs 16 offset
            val tile = bus.readOAM(index + 2) //Byte2 - Tile/Pattern Number
            val attr = bus.readOAM(index + 3).toByte() //Byte3 - Attributes/Flags
            val tileIndex = tile and (spriteSize shr 4).inv()

            //Bit4   Palette number  **Non CGB Mode Only** (0=OBP0, 1=OBP1)
            val palette = if (isBit(4, attr)) objectPalette1 else objectPalette0

            val tileRow = if (isYFlipped(attr)) spriteSize - 1 - (unsignedLy - y) else (unsignedLy - y)

            val tileAddress = ((0x8000 + (tileIndex * 16) + (tileRow * 2)))
            val lo = bus.readVRAM(tileAddress)
            val hi = bus.readVRAM(tileAddress + 1)

            for (p in 0..7) {
                if ((x + p) >= 0 && (x + p) < SCREEN_WIDTH) {
                    val idPos = if (isXFlipped(attr)) p else 7 - p
                    val colorId: Int = getColorIdBits(idPos, lo.toByte(), hi.toByte())

                    if (!isTransparent(colorId) && (isAboveBG(attr) || isBGWhite(x + p, unsignedLy))) {
                        val color = palette[colorId]
                        frameBuffer.write(x + p, unsignedLy, color)
                    }
                }
            }
        }
    }

    private inline fun isWindow(LCDC: Byte, WY: Int, LY: Int): Boolean {
        //Bit 5 - Window Display Enable (0=Off, 1=On)
        return isBit(5, LCDC) && WY <= LY
    }

    private inline fun spriteSize(LCDC: Byte): Int {
        //Bit 2 - OBJ (Sprite) Size (0=8x8, 1=8x16)
        return if (isBit(2, LCDC)) 16 else 8
    }


    private inline fun isXFlipped(attr: Byte): Boolean {
        //Bit5   X flip(0 = Normal, 1 = Horizontally mirrored)
        return isBit(5, attr)
    }

    private inline fun isYFlipped(attr: Byte): Boolean {
        //Bit6 Y flip(0 = Normal, 1 = Vertically mirrored)
        return isBit(6, attr)
    }

    private inline fun isTransparent(b: Int): Boolean {
        return b == 0
    }

    private fun isBGWhite(x: Int, y: Int): Boolean {
        return frameBuffer.read(x, y) == backgroundPalette[0]
    }


    private inline fun isAboveBG(attr: Byte): Boolean {
        //Bit7 OBJ-to - BG Priority(0 = OBJ Above BG, 1 = OBJ Behind BG color 1 - 3)
        return attr.toUInt() and 0x80u == 0u
    }


    private inline fun isSignedAddress(LCDC: Byte): Boolean {
        //Bit 4 - BG & Window Tile Data Select   (0=8800-97FF, 1=8000-8FFF)
        return isBit(4, LCDC)
    }

    private inline fun getBackgroundTileMapAddress(LCDC: Byte): Int {
        //Bit 3 - BG Tile Map Display Select     (0=9800-9BFF, 1=9C00-9FFF)
        return if (isBit(3, LCDC)) 0x9C00 else 0x9800
    }


    private inline fun getWindowTileMapAddress(LCDC: Byte): Int {
        //Bit 6 - Window Tile Map Display Select(0 = 9800 - 9BFF, 1 = 9C00 - 9FFF)
        return if (isBit(6, LCDC)) 0x9C00 else 0x9800
    }


    private inline fun getTileDataAddress(LCDC: Byte): Int {
        //Bit 4 - BG & Window Tile Data Select   (0=8800-97FF, 1=8000-8FFF)
        return if (isBit(4, LCDC)) 0x8000 else 0x8800 //0x8800 signed area
    }

    private inline fun IntArray.read(x: Int, y: Int): Int {
        return this[x + (y * SCREEN_WIDTH)]
    }

    private inline fun IntArray.write(x: Int, y: Int, color: Int) {
        this[x + (y * SCREEN_WIDTH)] = color
    }

    private fun getColorIdBits(colorBit: Int, l: Byte, h: Byte): Int {
        val hi = (h.toInt() shr colorBit) and 0x1
        val lo = (l.toInt() shr colorBit) and 0x1
        return (hi shl 1 or lo) and 0xFF
    }

    fun reset() {
        scanlineCounter = 0
        windowInternalLine = 0
        frameBuffer.fill(color[0])
        host.render(frameBuffer)
    }

    companion object {
        private val colorPocket = intArrayOf(
            0xFFFFFFFF.toInt(),
            0xFFA9A9A9.toInt(),
            0xFF545454.toInt(),
            0xFF000000.toInt()
        )
        private val color = intArrayOf(
            0xFF9AA13C.toInt(),
            0xFF6c712a.toInt(),
            0xFF4d511e.toInt(),
            0xFF1f200c.toInt()
        )

        private const val SCREEN_WIDTH = 160
        private const val SCREEN_HEIGHT = 144
        private const val SCREEN_VBLANK_HEIGHT = 153
        private const val OAM_CYCLES = 80
        private const val VRAM_CYCLES = 172
        private const val HBLANK_CYCLES = 204
        private const val SCANLINE_CYCLES = 456

        private const val VBLANK_INTERRUPT: Byte = 0x1
        private const val LCD_INTERRUPT: Byte = 0x2

        object Mode {
            const val HBLANK = 0
            const val VBLANK = 1
            const val OAM = 2
            const val VRAM = 3
        }
    }
}