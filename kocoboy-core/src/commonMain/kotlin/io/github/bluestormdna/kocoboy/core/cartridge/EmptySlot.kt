package io.github.bluestormdna.kocoboy.core.cartridge

/**
 * A cartridge empty slot representation as it is possible on real hardware.
 * It returns 0xFF from the bus and it produces the typical black square boot logo
 */
class EmptySlot: Cartridge {
    override fun readLoROM(addr: UShort): UByte {
        return 0xFFu
    }

    override fun readHiROM(addr: UShort): UByte {
        return 0xFFu
    }

    override fun writeROM(addr: UShort, value: UByte) {
        // Nop
    }

    override fun readERAM(addr: UShort): UByte {
        return 0xFFu
    }

    override fun writeERAM(addr: UShort, value: UByte) {
        // Nop
    }
}