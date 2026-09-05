@file:OptIn(ExperimentalUnsignedTypes::class)

package io.github.bluestormdna.kocoboy.core.cartridge

interface Cartridge {
    fun readLoROM(addr: UShort): UByte
    fun readHiROM(addr: UShort): UByte
    fun writeROM(addr: UShort, value: UByte)
    fun readERAM(addr: UShort): UByte
    fun writeERAM(addr: UShort, value: UByte)
}

fun resolveCartridgeType(rom: ByteArray): Cartridge {
    if (rom.isEmpty()) return EmptySlot()
    return when (rom[0x147].toInt()) {
        0x00, 0x08, 0x09 -> MBC0(rom.toUByteArray())
        0x01, 0x02, 0x03 -> MBC1(rom.toUByteArray())
        0x05, 0x06 -> MBC2(rom.toUByteArray())
        0x0F, 0x10, 0x11, 0x12, 0x13 -> MBC3(rom.toUByteArray())
        0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E -> MBC5(rom.toUByteArray())
        else -> EmptySlot()
    }
}

// See Pan Docs "0149 - RAM size": how many 8 KiB banks of external RAM the cart declares.
internal val UByteArray.ramBankCount: Int
    get() = when (getOrElse(0x149) { 0u }.toInt()) {
        0x02 -> 1
        0x03 -> 4
        0x04 -> 16
        0x05 -> 8
        else -> 0
    }
