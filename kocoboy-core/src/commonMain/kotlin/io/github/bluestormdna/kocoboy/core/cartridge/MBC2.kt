package io.github.bluestormdna.kocoboy.core.cartridge

@OptIn(ExperimentalUnsignedTypes::class)
class MBC2(private val rom: UByteArray) : Cartridge {

    // MBC2 has 512x4-bit RAM built into the MBC chip itself, not external.
    private val eRam = UByteArray(0x200)

    private var eRamEnabled = false
    private var romBank = 1

    // Out-of-range bank numbers wrap instead of over-reading.
    private val romBankMask = (rom.size / ROM_OFFSET) - 1

    override fun readLoROM(addr: UShort): UByte = rom[addr.toInt()]

    override fun readHiROM(addr: UShort): UByte {
        val bank = (if (romBank == 0) 1 else romBank) and romBankMask
        return rom[(ROM_OFFSET * bank) + (addr and 0x3FFFu).toInt()]
    }

    override fun writeROM(addr: UShort, value: UByte) {
        when (addr) {
            // Address bit 8, not which half of 0000-3FFF is written, selects RAM-enable vs ROM-bank.
            in 0x0000u..0x3FFFu -> if (addr.toUInt() and 0x100u != 0u) {
                romBank = value.toInt() and 0xF
            } else {
                eRamEnabled = value and 0x0Fu == 0x0A.toUByte()
            }
        }
    }

    // Only 4 data lines are wired; the upper nibble always reads back as 1s.
    override fun readERAM(addr: UShort): UByte = if (eRamEnabled) {
        eRam[(addr and 0x1FFu).toInt()] or 0xF0u
    } else {
        0xFFu
    }

    override fun writeERAM(addr: UShort, value: UByte) {
        if (eRamEnabled) {
            eRam[(addr and 0x1FFu).toInt()] = value and 0x0Fu
        }
    }

    companion object {
        private const val ROM_OFFSET = 0x4000
    }
}
