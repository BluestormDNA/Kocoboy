package io.github.bluestormdna.kocoboy.core.cartridge


@OptIn(ExperimentalUnsignedTypes::class)
class MBC5(private val rom: UByteArray): Cartridge {

    private val eRam = UByteArray(0x20000)

    private var eRamEnabled = false
    private var romBankHi = 0
    private var romBankLo = 1
    private var ramBank = 0

    override fun readLoROM(addr: UShort): UByte {
        return rom[addr.toInt()]
    }

    override fun readHiROM(addr: UShort): UByte {
        // Do any MBC5 game actually use Hi?
        return rom[(ROM_OFFSET * (romBankLo)) + (addr and 0x3FFFu).toInt()]
    }

    override fun writeROM(addr: UShort, value: UByte) {
        when (addr) {
            in 0x0000u..0x1FFFu -> eRamEnabled = value == 0x0A.toUByte()
            in 0x2000u..0x3FFFu -> romBankLo = value.toInt() and 0xFF
            in 0x4000u..0x5FFFu -> romBankHi = value.toInt() and 0xFF
            in 0x6000u..0x7FFFu -> ramBank = value.toInt() and 0xF
        }
    }

    override fun readERAM(addr: UShort): UByte {
        return if (eRamEnabled) {
            eRam[(ERAM_OFFSET * ramBank + (addr and 0x1FFFu).toInt())]
        } else {
            0xFFu
        }
    }

    override fun writeERAM(addr: UShort, value: UByte) {
        if (eRamEnabled) {
            eRam[(ERAM_OFFSET * ramBank) + (addr and 0x1FFFu).toInt()] = value
        }
    }

    companion object {
        private const val ROM_OFFSET = 0x4000
        private const val ERAM_OFFSET = 0x2000
    }
}