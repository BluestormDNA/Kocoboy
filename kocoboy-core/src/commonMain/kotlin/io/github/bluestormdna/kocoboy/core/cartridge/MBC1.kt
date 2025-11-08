package io.github.bluestormdna.kocoboy.core.cartridge

@OptIn(ExperimentalUnsignedTypes::class)
class MBC1(private val rom: UByteArray) : Cartridge {

    private val eRam = UByteArray(0x8000)

    private var eRamEnabled = false
    private var bankingMode = 0
    private var romBank = 1
    private var ramBank = 0

    override fun readLoROM(addr: UShort): UByte = rom[addr.toInt()]

    override fun readHiROM(addr: UShort): UByte =
        rom[(ROM_OFFSET * romBank) + (addr and 0x3FFFu).toInt()]

    override fun writeROM(addr: UShort, value: UByte) {
        when (addr) {
            in 0x0000u..0x1FFFu -> eRamEnabled = value and 0x0Fu == 0x0A.toUByte()
            in 0x2000u..0x3FFFu -> {
                // only last 5bits are used
                romBank = romBank and 0b0110_0000 or value.toInt() and 0x1F
                if (romBank == 0x00) {
                    romBank++
                }
            }

            in 0x4000u..0x5FFFu -> if (bankingMode == 0) {
                romBank = romBank or (value.toInt() and 0x3)
                if (romBank == 0x00 || romBank == 0x20 || romBank == 0x40 ||
                    romBank == 0x60
                ) {
                    romBank++
                }
            } else {
                ramBank = value.toInt() and 0x3
            }

            in 0x6000u..0x7FFFu ->
                // 00h = ROM Banking Mode (up to 8KByte RAM, 2MByte ROM) (default)
                // 01h = RAM Banking Mode(up to 32KByte RAM, 512KByte ROM)
                bankingMode = value.toInt() and 0x1
        }
    }

    override fun readERAM(addr: UShort): UByte = if (eRamEnabled) {
        eRam[(ERAM_OFFSET * ramBank + (addr and 0x1FFFu).toInt())]
    } else {
        0xFFu
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
