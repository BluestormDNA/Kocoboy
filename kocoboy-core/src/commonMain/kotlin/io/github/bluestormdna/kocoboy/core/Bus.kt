package io.github.bluestormdna.kocoboy.core

import io.github.bluestormdna.kocoboy.core.cartridge.Cartridge
import io.github.bluestormdna.kocoboy.core.cartridge.EmptySlot
import kotlin.experimental.and
import kotlin.experimental.or

@OptIn(ExperimentalStdlibApi::class)
class Bus(
    private val joypad: Joypad,
) {

    private val bootRoom = ByteArray(0x100)
    private var cartridge: Cartridge = EmptySlot()

    //DMG Memory Map
    private val vRam = ByteArray(0x2000)
    private val wRam0 = ByteArray(0x1000)
    private val wRam1 = ByteArray(0x1000)
    private val oam = ByteArray(0xA0)
    private val io = ByteArray(0x80)
    private val hRam = ByteArray(0x80)

    fun orderSprites(line: Int, size: Int, orderBuffer: IntArray) {
        // First extract to buffer the Y visible sprites
        var orderBufferIndex = 0
        for (i in 0..oam.lastIndex step 4) { // todo check if indices appear on profileable
            val y = (oam[i].toInt() and 0xFF) - 16
            val visible = (line >= y) && (line < (y + size))
            if (visible) {
                orderBuffer[orderBufferIndex++] = i
            }
        }

        // Sets the terminator
        orderBuffer[orderBufferIndex] = -1

        // Fast path if nothing to order
        if (orderBufferIndex <= 1) return

        // Reorder based on X
        insertionSortOamOrderBuffer(indexes = orderBuffer, lastIndexExclusive = orderBufferIndex)

        // Set the terminator to position 10 as only 10 sprites per scanline are allowed
        orderBuffer[10] = -1

        // Reverse the sprites in the buffer subset
        orderBufferIndex = if (orderBufferIndex >= 10) 10 else orderBufferIndex
        orderBuffer.reverse(0, orderBufferIndex)
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

    fun load(cart: Cartridge) {
        cartridge = cart
    }

    fun load(bios: ByteArray) {
        bios.copyInto(bootRoom)
    }

    // PPU Regs
    //FF44 - LY - LCDC Y-Coordinate (R) bypasses on write always 0
    var LY
        get() = io[0x44]
        set(value) {
            io[0x44] = value
        }
    //FF41 - STAT - LCDC Status (R/W)
    var STAT
        get() = io[0x41]
        set(value) {
            io[0x41] = value
        }
    val LCDC get() = io[0x40] //FF40 - LCDC - LCD Control (R/W)
    val SCY get() = io[0x42] //FF42 - SCY - Scroll Y (R/W)
    val SCX get() = io[0x43] //FF43 - SCX - Scroll X (R/W)
    val LYC get() = io[0x45] //FF45 - LYC - LY Compare(R/W)
    val WY get() = io[0x4A] //FF4A - WY - Window Y Position (R/W)
    val WX get() = io[0x4B] //FF4B - WX - Window X Position minus 7 (R/W)
    val BGP get() = io[0x47] //FF47 - BGP - BG Palette Data(R/W) - Non CGB Mode Only
    val OBP0 get() = io[0x48] //FF48 - OBP0 - Object Palette 0 Data (R/W) - Non CGB Mode Only
    val OBP1 get() = io[0x49] //FF49 - OBP1 - Object Palette 1 Data (R/W) - Non CGB Mode Only

    // Timer IO Regs
    //FF04 - DIV - Divider Register (R/W)
    var DIV
        get() = io[0x04]
        set(value) {
            io[0x04] = value
        }
    //FF05 - TIMA - Timer counter (R/W)
    var TIMA
        get() = io[0x05]
        set(value) {
            io[0x05] = value
        }
    //FF06 - TMA - Timer Modulo (R/W)
    var TMA
        get() = io[0x06]
        set(value) {
            io[0x06] = value
        }
    val TAC_ENABLED: Boolean //FF07 - TAC - Timer Control (R/W)
        get() {
            return (io[0x07] and 0x4) != 0.toByte()
        }
    val TAC_FREQ: Int
        get() {
            return io[0x07].toInt() and 0x3
        }

    val interruptFlags: Byte get() = io[0x0F]
    val interruptEnabled: Byte get() = hRam[0x7F]

    init {
        initializeRegisters()
    }

    private fun initializeRegisters() {
        //FF4D - KEY1 - CGB Mode Only - Prepare Speed Switch
        //HardCoded to FF to identify DMG as 00 is GBC
        io[0x4D] = 0xFF.toByte()

        io[0x10] = 0x80.toByte()
        io[0x11] = 0xBF.toByte()
        io[0x12] = 0xF3.toByte()
        io[0x14] = 0xBF.toByte()
        io[0x16] = 0x3F.toByte()
        io[0x19] = 0xBF.toByte()
        io[0x1A] = 0x7F.toByte()
        io[0x1B] = 0xFF.toByte()
        io[0x1C] = 0x9F.toByte()
        io[0x1E] = 0xBF.toByte()
        io[0x20] = 0xFF.toByte()
        io[0x23] = 0xBF.toByte()
        io[0x24] = 0x77.toByte()
        io[0x25] = 0xF3.toByte()
        io[0x26] = 0xF1.toByte()
        io[0x40] = 0x91.toByte()
        io[0x47] = 0xFC.toByte()
        io[0x48] = 0xFF.toByte()
        io[0x49] = 0xFF.toByte()
        io[0x00] = 0xFF.toByte()
    }

    private val onBootRom: Boolean
        get() = io[0x50] == 0.toByte()

    fun readByte(addr: Int): Int {
        // todo remove signed unsigned mess
        // either embrace unsigned and its kotlin limitations (infix ops, lack of operators...)
        // or go full signed with masking
        val address = addr.toUShort()
        return when (addr) {
            //in 0x0000..0x3FFF -> {
            //    if (onBootRom && address < 0x100u) {
            //        bootRoom[addr].toInt() and 0xFF
            //    } else {
            //        cartridge.readLoROM(address).toInt() and 0xFF
            //    }
            //}
            in 0x0000..0x3FFF -> cartridge.readLoROM(address).toInt() and 0xFF
            in 0x4000..0x7FFF -> cartridge.readHiROM(address).toInt() and 0xFF
            in 0x8000..0x9FFF -> vRam[addr and 0x1FFF].toInt() and 0xFF
            in 0xA000..0xBFFF -> cartridge.readERAM(address).toInt() and 0xFF
            in 0xC000..0xCFFF -> wRam0[addr and 0xFFF].toInt() and 0xFF
            in 0xD000..0xDFFF -> wRam1[addr and 0xFFF].toInt() and 0xFF
            in 0xE000..0xEFFF -> wRam0[addr and 0xFFF].toInt() and 0xFF
            in 0xF000..0xFDFF -> wRam1[addr and 0xFFF].toInt() and 0xFF
            in 0xFE00..0xFE9F -> oam[addr and 0xFF].toInt() and 0xFF
            in 0xFEA0..0xFEFF -> 0x00 // Not usable
            in 0xFF00..0xFF7F -> {
                // JOYPAD
                if (addr == 0xFF00) return joypad.read().toInt() and 0xFF
                return io[addr and 0x7F].toInt() and 0xFF
            }
            in 0xFF80..0xFFFF -> hRam[addr and 0x7F].toInt() and 0xFF
            else -> throw IllegalStateException("Attempting to read to ${addr.toHexString()}")
        }
    }

    fun writeByte(addr: Int, value: Int) {
        // todo address int to unsigned mess
        val address = addr.toUShort()
        val byte = value.toUByte()
        when (addr) {
            in 0x0000..0x7FFF -> cartridge.writeROM(address, byte)
            in 0x8000..0x9FFF -> vRam[addr and 0x1FFF] = value.toByte()
            in 0xA000..0xBFFF -> cartridge.writeERAM(address, byte)
            in 0xC000..0xCFFF -> wRam0[addr and 0xFFF] = value.toByte()
            in 0xD000..0xDFFF -> wRam1[addr and 0xFFF] = value.toByte()
            in 0xE000..0xEFFF -> wRam0[addr and 0xFFF] = value.toByte()
            in 0xF000..0xFDFF -> wRam1[addr and 0xFFF] = value.toByte()
            in 0xFE00..0xFE9F -> oam[addr and 0xFF] = value.toByte()
            in 0xFEA0..0xFEFF -> Unit // Not usable
            in 0xFF00..0xFF7F -> { // IO
                // JOYPAD
                if(addr == 0xFF00) {
                    joypad.write(value.toByte())
                    return
                }

                val ioValue = when (addr) {
                    0xFF0F -> value or 0xE0
                    0xFF04, 0xFF44 -> 0
                    0xFF46 -> handleDma(value)
                    else -> value
                }
                io[addr and 0x7F] = ioValue.toByte()

                //Temp Serial Link output for debug
                if (addr == 0xFF02 && value == 0x81) {
                    print(readByte(0xFF01).toChar())
                }
            }

            in 0xFF80..0xFFFF -> hRam[addr and 0x7F] = value.toByte()
            else -> throw IllegalStateException("Attempting to write ${byte.toHexString()} to ${addr.toHexString()}")
        }
    }

    fun readOAM(addr: Int): Int {
        return oam[addr].toInt() and 0xFF
    }

    fun readVRAM(addr: Int): Int {
        return vRam[addr and 0x1FFF].toInt() and 0xFF
    }

    private fun handleDma(value: Int): Int {
        val addr = value shl 8
        for (i in oam.indices) {
            oam[i] = readByte(addr + i).toByte()
        }
        return 0
    }

    fun clearInterrupt(b: Int) {
        val interruptFlags = io[0x0F]
        io[0x0F] = interruptFlags and ((1 shl b).inv()).toByte()
    }

    fun requestInterrupt(interrupt: Byte) {
        io[0x0F] = io[0x0F] or interrupt
    }

    fun reset() {
        cleanUp()
        initializeRegisters()
    }

    private fun cleanUp() {
        vRam.fill(0)
        wRam0.fill(0)
        wRam1.fill(0)
        oam.fill(0)
        io.fill(0)
        hRam.fill(0)
    }


}