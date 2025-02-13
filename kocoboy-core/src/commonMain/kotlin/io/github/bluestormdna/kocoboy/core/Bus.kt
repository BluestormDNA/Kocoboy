package io.github.bluestormdna.kocoboy.core

import io.github.bluestormdna.kocoboy.core.cartridge.Cartridge
import io.github.bluestormdna.kocoboy.core.cartridge.EmptySlot
import kotlin.experimental.and
import kotlin.experimental.or

@OptIn(ExperimentalStdlibApi::class)
class Bus(
    private val apu: APU,
    private val joypad: Joypad,
    private val timer: Timer,
    private val ppu: PPU,
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

    val interruptFlags: Byte get() = io[0x0F]
    val interruptEnabled: Byte get() = hRam[0x7F]

    init {
        initializeRegisters()
    }

    private fun initializeRegisters() {
        //FF4D - KEY1 - CGB Mode Only - Prepare Speed Switch
        //HardCoded to FF to identify DMG as 00 is GBC
        io[0x4D] = 0xFF.toByte()

        ppu.write(0x40, 0x91.toByte(), this)
        ppu.write(0x47, 0xFC.toByte(), this)
        ppu.write(0x48, 0xFF.toByte(), this)
        ppu.write(0x49, 0xFF.toByte(), this)

        apu.write(0x10, 0x80.toByte())
        apu.write(0x11, 0xBF.toByte())
        apu.write(0x12, 0xF3.toByte())
        apu.write(0x14, 0xBF.toByte())
        apu.write(0x16, 0x3F.toByte())
        apu.write(0x19, 0xBF.toByte())
        apu.write(0x1A, 0x7F.toByte())
        apu.write(0x1B, 0xFF.toByte())
        apu.write(0x1C, 0x9F.toByte())
        apu.write(0x1E, 0xBF.toByte())
        apu.write(0x20, 0xFF.toByte())
        apu.write(0x23, 0xBF.toByte())
        apu.write(0x24, 0x77.toByte())
        apu.write(0x25, 0xF3.toByte())
        apu.write(0x26, 0xF1.toByte())
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
                when(val ioAddress = addr and 0x7F) {
                    0x00 -> joypad.read().toInt() and 0xFF
                    in 0x03..0x07 -> timer.read(ioAddress).toInt() and 0xFF
                    in 0x10..0x3F -> apu.read(ioAddress).toInt() and 0xFF
                    in 0x40..0x4B -> ppu.read(ioAddress).toInt() and 0xFF
                    else -> io[ioAddress].toInt() and 0xFF
                }
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
                when(val ioAddress = addr and 0x7F) {
                    0x00 -> joypad.write(value.toByte())
                    0x02 -> handleSerialLink(value)
                    0x0F -> io[ioAddress] = (value or 0xE0).toByte() // todo use interrupt field
                    in 0x03..0x07 -> timer.write(ioAddress, value.toByte())
                    in 0x10..0x3F -> apu.write(ioAddress, value.toByte())
                    in 0x40..0x4B -> ppu.write(ioAddress, value.toByte(), this) // Lyc can cause interrupts on write
                    else -> io[ioAddress] = value.toByte()
                }
            }

            in 0xFF80..0xFFFF -> hRam[addr and 0x7F] = value.toByte()
            else -> throw IllegalStateException("Attempting to write ${byte.toHexString()} to ${addr.toHexString()}")
        }
    }

    private fun handleSerialLink(value: Int) {
        //Temp Serial Link output for debug
        if (value == 0x81) {
            print(readByte(0xFF01).toChar())
        }
    }

    fun readOAM(addr: Int): Int {
        return oam[addr].toInt() and 0xFF
    }

    fun readVRAM(addr: Int): Int {
        return vRam[addr and 0x1FFF].toInt() and 0xFF
    }

    fun handleDma(value: Byte): Int {
        val addr = (value.toInt() and 0xFF) shl 8
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