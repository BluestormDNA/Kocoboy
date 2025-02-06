package io.github.bluestormdna.kocoboy.core

import io.github.bluestormdna.kocoboy.host.Host
import kotlin.experimental.and
import kotlin.experimental.inv

class PPU(private val host: Host) {

    private var scanlineCounter = 0
    private var windowInternalLine = 0
    private val frameBuffer = IntArray(160 * 144)

    fun update(cycles: Int, bus: Bus) {
        scanlineCounter += cycles

        if(!isLCDEnabled(bus.LCDC)) {
            scanlineCounter = 0
            bus.LY = 0
            bus.STAT = (bus.STAT and 0x3.toByte().inv())
            return
        }

        when ((bus.STAT and 0x3).toInt()) {
            Mode.OAM -> if (scanlineCounter >= OAM_CYCLES) {
                scanlineCounter -= OAM_CYCLES
                updateStatMode(3, bus)
                if (isBit(5, bus.STAT)) {
                    bus.requestInterrupt(LCD_INTERRUPT)
                }
            }

            Mode.VRAM -> if (scanlineCounter >= VRAM_CYCLES) {
                scanlineCounter -= VRAM_CYCLES
                updateStatMode(0, bus)
                drawScanLine(bus)
            }

            Mode.HBLANK -> if (scanlineCounter >= HBLANK_CYCLES) {
                scanlineCounter -= HBLANK_CYCLES
                bus.LY++
                handleCoincidenceFlag(bus)

                if (isBit(3, bus.STAT)) {
                    bus.requestInterrupt(LCD_INTERRUPT)
                }

                if (bus.LY.toInt() and 0xFF == SCREEN_HEIGHT) { //check if we arrived Vblank
                    updateStatMode(1, bus) // Set VBlank
                    bus.requestInterrupt(VBLANK_INTERRUPT)
                    host.render(frameBuffer)
                } else { //not arrived yet so return to mode 2 / OAM
                    updateStatMode(2, bus)
                }
            }

            Mode.VBLANK -> if (scanlineCounter >= SCANLINE_CYCLES) {
                scanlineCounter -= SCANLINE_CYCLES
                bus.LY++
                handleCoincidenceFlag(bus)

                if (isBit(4, bus.STAT)) {
                    bus.requestInterrupt(LCD_INTERRUPT)
                }

                if ((bus.LY.toInt() and 0xFF) > SCREEN_VBLANK_HEIGHT) { //check end of VBLANK
                    updateStatMode(2, bus)
                    bus.LY = 0
                }
            }
        }
    }

    private fun handleCoincidenceFlag(bus: Bus) {
        if (bus.LY == bus.LYC) {
            bus.STAT = bitSet(2, bus.STAT)
            if (isBit(6, bus.STAT)) {
                bus.requestInterrupt(LCD_INTERRUPT)
            }
        } else {
            bus.STAT = bitClear(2, bus.STAT)
        }
    }

    private fun updateStatMode(mode: Int, bus: Bus) {
        val stat = (bus.STAT and 0x3.toByte().inv())
        bus.STAT = (stat.toInt() or mode).toByte()
    }

    private fun drawScanLine(bus: Bus) {
        val LCDC = bus.LCDC
        if (isBit(0, LCDC)) { //Bit 0 - BG Display (0=Off, 1=On)
            renderBG(bus)
        }
        if (isBit(1, LCDC)) { //Bit 1 - OBJ (Sprite) Display Enable
            //val time = measureTime {
            renderSpritesBuffer(bus)
            //}
            //println("meassured: $time on ${mmu.LY.toInt() and 0xFF}")
        }
    }

    private fun renderBG(bus: Bus) {
        val WX = ((bus.WX.toInt() and 0xFF) - 7 and 0xFF) //WX needs -7 Offset
        val WY = bus.WY.toInt() and 0xFF
        val LY = bus.LY.toInt() and 0xFF
        val LCDC = bus.LCDC
        val SCY = bus.SCY.toInt() and 0xFF
        val SCX = bus.SCX.toInt() and 0xFF
        val BGP = bus.BGP.toInt() and 0xFF
        val isWin = isWindow(LCDC, WY, LY)

        if (LY == WY) windowInternalLine = 0

        val windowTileMapAddress = getWindowTileMapAddress(LCDC)
        val bgTileMapAddress = getBackgroundTileMapAddress(LCDC)
        var hi: Byte = 0
        var lo: Byte = 0

        var windowAppeared = false
        for (p in 0 ..< SCREEN_WIDTH) {
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

                val tileLoc = if (isSignedAddress(LCDC)) {
                    getTileDataAddress(LCDC) + bus.readVRAM(tileAddress) * 16
                } else {
                    getTileDataAddress(LCDC) + (bus.readVRAM(tileAddress).toByte() /*sbyte*/ + 128) * 16
                }

                lo = bus.readVRAM((tileLoc + tileLine)).toByte()
                hi = bus.readVRAM((tileLoc + tileLine + 1)).toByte()
            }

            val colorBit = 7 - (x and 7) // reversed
            val colorId = getColorIdBits(colorBit, lo, hi)
            val colorIdThroughPalette = getPaletteColorIndex(BGP, colorId)

            frameBuffer.write(p, LY, color[colorIdThroughPalette])
        }

        if (windowAppeared) {
            windowInternalLine++
        }
    }

    private val orderBuffer = IntArray(40 + 1) // Oam Indexes plus terminator

    private fun renderSpritesBuffer(bus: Bus) {
        val LY = bus.LY.toInt() and 0xFF
        val LCDC = bus.LCDC
        val spriteSize = spriteSize(LCDC)

        // 0x9F OAM Size, 40 Sprites x 4 bytes filtering:
        // Out of y range and ordered by x limited to 10
        bus.orderSprites(LY, spriteSize, orderBuffer)

        var orderBufferPointer = 0
        while (orderBuffer[orderBufferPointer] != -1) {
            val index = orderBuffer[orderBufferPointer]
            val x = bus.readOAM(index + 1) - 8 //Byte1 - X Position //needs 8 offset
            // Out of range X values are not drawn but will consume
            // sprite object slots towards the 10 limit (hence not filtering them on the mmu)
            orderBufferPointer++
            if(x <= -8 || x >= 160) continue

            val y = bus.readOAM(index) - 16 //Byte0 - Y Position //needs 16 offset
            val tile = bus.readOAM(index + 2) //Byte2 - Tile/Pattern Number
            val attr = bus.readOAM(index + 3).toByte() //Byte3 - Attributes/Flags
            val tileIndex = tile and (spriteSize shr 4).inv()

            //Bit4   Palette number  **Non CGB Mode Only** (0=OBP0, 1=OBP1)
            val palette = if (isBit(4, attr)) bus.OBP1 else bus.OBP0

            val tileRow = if (isYFlipped(attr)) spriteSize - 1 - (LY - y) else (LY - y)

            val tileAddress = ((0x8000 + (tileIndex * 16) + (tileRow * 2)))
            val lo = bus.readVRAM(tileAddress)
            val hi = bus.readVRAM(tileAddress + 1)

            for (p in 0..7) {
                if ((x + p) >= 0 && (x + p) < SCREEN_WIDTH) {
                    val idPos = if (isXFlipped(attr)) p else 7 - p
                    val colorId: Int = getColorIdBits(idPos, lo.toByte(), hi.toByte())

                    if (!isTransparent(colorId) && (isAboveBG(attr) || isBGWhite(bus.BGP, x + p, LY))) {
                        val paletteColorIndex: Int = getPaletteColorIndex(palette.toInt(), colorId)

                        frameBuffer.write(x + p, LY, color[paletteColorIndex])
                    }
                }
            }
        }
    }

    private inline fun isWindow(LCDC: Byte, WY: Int, LY: Int): Boolean {
        //Bit 5 - Window Display Enable (0=Off, 1=On)
        return isBit(5, LCDC) && WY <= LY
    }


    private inline fun isLCDEnabled(LCDC: Byte): Boolean {
        //Bit 7 - LCD Display Enable
        return isBit(7, LCDC)
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

    private fun isBGWhite(BGP: Byte, x: Int, y: Int): Boolean {
        val id = BGP.toInt() and 0x3
        return frameBuffer.read(x, y) == color[id]
    }


    private inline fun isAboveBG(attr: Byte): Boolean {
        //Bit7 OBJ-to - BG Priority(0 = OBJ Above BG, 1 = OBJ Behind BG color 1 - 3)
        return (attr.toInt() and 0xFF) shr 7 == 0
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

    private inline fun getPaletteColorIndex(palette: Int, colorId: Int): Int {
        return (palette shr colorId * 2) and 0x3
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
        private val colorPocket = intArrayOf(0xFFFFFFFF.toInt(), 0xFFA9A9A9.toInt(), 0xFF545454.toInt(), 0xFF000000.toInt())
        private val color = intArrayOf(0xFF9AA13C.toInt(), 0xFF6c712a.toInt(), 0xFF4d511e.toInt(), 0xFF1f200c.toInt())

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