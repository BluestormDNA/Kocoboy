package io.github.bluestormdna.kocoboy.core.cartridge

@OptIn(ExperimentalUnsignedTypes::class)
class MBC2(private val rom: UByteArray) : Cartridge {

    private val eRam = UByteArray(0x200)

    private var eRamEnabled = false
    private var romBank = 1

    override fun readLoROM(addr: UShort): UByte = rom[addr.toInt()]

    override fun readHiROM(addr: UShort): UByte =
        rom[(ROM_OFFSET * romBank) + (addr and 0x3FFFu).toInt()]

    override fun writeROM(addr: UShort, value: UByte) {
        when (addr) {
            in 0x0000u..0x1FFFu -> eRamEnabled = value and 0x01u == 0.toUByte()
            in 0x2000u..0x3FFFu -> romBank = value.toInt() and 0xF
        }
    }

    override fun readERAM(addr: UShort): UByte = if (eRamEnabled) {
        eRam[(addr and 0x1FFFu).toInt()]
    } else {
        0xFFu
    }

    override fun writeERAM(addr: UShort, value: UByte) {
        if (eRamEnabled) {
            eRam[(addr and 0x1FFFu).toInt()] = value
        }
    }

    companion object {
        private const val ROM_OFFSET = 0x4000
    }
}
