package io.github.bluestormdna.kocoboy.core.cartridge

@OptIn(ExperimentalUnsignedTypes::class)
class MBC0(private val rom: UByteArray) : Cartridge {

    // ROM+RAM carts have plain, always-enabled, unbanked RAM; ROM ONLY carts have none.
    private val eRam = UByteArray(ERAM_OFFSET * rom.ramBankCount)

    override fun readLoROM(addr: UShort): UByte = rom[addr.toInt()]

    override fun readHiROM(addr: UShort): UByte = rom[addr.toInt()]

    override fun writeROM(addr: UShort, value: UByte) {
        // MBC0 should ignore writes
    }

    override fun readERAM(addr: UShort): UByte =
        if (eRam.isEmpty()) 0xFFu else eRam[(addr and 0x1FFFu).toInt() % eRam.size]

    override fun writeERAM(addr: UShort, value: UByte) {
        if (eRam.isNotEmpty()) eRam[(addr and 0x1FFFu).toInt() % eRam.size] = value
    }

    companion object {
        private const val ERAM_OFFSET = 0x2000
    }
}
