@file:OptIn(ExperimentalUnsignedTypes::class)

package io.github.bluestormdna.kocoboy.core.cartridge

class MBC1(private val rom: UByteArray) : Cartridge {

    private val eRam = UByteArray(0x8000)

    private var eRamEnabled = false
    private var bankingMode = 0

    // Kept unmasked: the bank 00->01 quirk depends on the full 5-bit register value.
    private var romBankRegister = 1
    private var bankRegister2 = 0

    // Out-of-range bank numbers wrap instead of over-reading.
    private val romBankMask = (rom.size / ROM_OFFSET) - 1
    private val ramBankMask = (rom.ramBankCount - 1).coerceAtLeast(0)

    private val isMulticart = rom.isMbc1Multicart()

    // Multicart boards only wire 4 of the 5 ROM-bank address lines and shift bankRegister2 by 4 not 5.
    private val romBankAddressBits = if (isMulticart) 0xF else 0x1F
    private val bankRegister2Shift = if (isMulticart) 4 else 5

    override fun readLoROM(addr: UShort): UByte {
        val bank = if (bankingMode == 1) (bankRegister2 shl bankRegister2Shift) and romBankMask else 0
        return rom[(ROM_OFFSET * bank) + addr.toInt()]
    }

    override fun readHiROM(addr: UShort): UByte {
        val lowBits = (if (romBankRegister == 0) 1 else romBankRegister) and romBankAddressBits
        val bank = ((bankRegister2 shl bankRegister2Shift) or lowBits) and romBankMask
        return rom[(ROM_OFFSET * bank) + (addr and 0x3FFFu).toInt()]
    }

    override fun writeROM(addr: UShort, value: UByte) {
        when (addr) {
            in 0x0000u..0x1FFFu -> eRamEnabled = value and 0x0Fu == 0x0A.toUByte()
            in 0x2000u..0x3FFFu -> romBankRegister = value.toInt() and 0x1F
            in 0x4000u..0x5FFFu -> bankRegister2 = value.toInt() and 0x3
            in 0x6000u..0x7FFFu ->
                // 00h = ROM Banking Mode (up to 8KByte RAM, 2MByte ROM) (default)
                // 01h = RAM Banking Mode(up to 32KByte RAM, 512KByte ROM)
                bankingMode = value.toInt() and 0x1
        }
    }

    override fun readERAM(addr: UShort): UByte {
        if (!eRamEnabled) return 0xFFu
        val bank = if (bankingMode == 1) bankRegister2 and ramBankMask else 0
        return eRam[(ERAM_OFFSET * bank) + (addr and 0x1FFFu).toInt()]
    }

    override fun writeERAM(addr: UShort, value: UByte) {
        if (!eRamEnabled) return
        val bank = if (bankingMode == 1) bankRegister2 and ramBankMask else 0
        eRam[(ERAM_OFFSET * bank) + (addr and 0x1FFFu).toInt()] = value
    }

    companion object {
        private const val ROM_OFFSET = 0x4000
        private const val ERAM_OFFSET = 0x2000
    }
}

// Detected by ROM size plus the Nintendo logo repeating at the second game's bank-0x10 boundary.
private fun UByteArray.isMbc1Multicart(): Boolean {
    if (size != 0x100000) return false
    val logoOffset = 0x40000 + 0x104
    return sliceArray(logoOffset until logoOffset + MBC1M_LOGO.size).contentEquals(MBC1M_LOGO)
}

private val MBC1M_LOGO = ubyteArrayOf(
    0xCEu, 0xEDu, 0x66u, 0x66u, 0xCCu, 0x0Du, 0x00u, 0x0Bu,
    0x03u, 0x73u, 0x00u, 0x83u, 0x00u, 0x0Cu, 0x00u, 0x0Du,
    0x00u, 0x08u, 0x11u, 0x1Fu, 0x88u, 0x89u, 0x00u, 0x0Eu,
    0xDCu, 0xCCu, 0x6Eu, 0xE6u, 0xDDu, 0xDDu, 0xD9u, 0x99u,
    0xBBu, 0xBBu, 0x67u, 0x63u, 0x6Eu, 0x0Eu, 0xECu, 0xCCu,
    0xDDu, 0xDCu, 0x99u, 0x9Fu, 0xBBu, 0xB9u, 0x33u, 0x3Eu,
)
