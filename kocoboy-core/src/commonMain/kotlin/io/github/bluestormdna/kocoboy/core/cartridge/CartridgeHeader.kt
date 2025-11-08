package io.github.bluestormdna.kocoboy.core.cartridge

interface CartridgeHeader {
    val type: String
    val name: String
}

class DefaultCartridgeHeader(rom: ByteArray) : CartridgeHeader {
    override val type = types[rom[0x147].toInt()].orEmpty()
    override val name = rom
        .decodeToString(0x134, 0x144)
        // decodeToString will introduce this for terminator 0x80
        .filterNot { char -> char.code == 0 || char.code == 65533 }
}

class EmptyCartridgeHeader : CartridgeHeader {
    override val type: String = ""
    override val name: String = "Empty Slot"
}

val types = mapOf(
    0x00 to "ROM_ONLY",
    0x01 to "MBC1",
    0x02 to "MBC1+RAM",
    0x03 to "MBC1+RAM+BATTERY",
    0x05 to "MBC2",
    0x06 to "MBC2+BATTERY",
    0x08 to "ROM+RAM",
    0x09 to "ROM+RAM+BATTERY",
    0x0B to "MMM01",
    0x0C to "MMM01+RAM",
    0x0D to "MMM01+RAM+BATTERY",
    0x0F to "MBC3+TIMER+BATTERY",
    0x10 to "MBC3+TIMER+RAM+BATTERY",
    0x11 to "MBC3",
    0x12 to "MBC3+RAM",
    0x13 to "MBC3+RAM+BATTERY",
    0x19 to "MBC5",
    0x1A to "MBC5+RAM",
    0x1B to "MBC5+RAM+BATTERY",
    0x1C to "MBC5+RUMBLE",
    0x1D to "MBC5+RUMBLE+RAM",
    0x1E to "MBC5+RUMBLE+RAM+BATTERY",
    0x20 to "MBC6",
    0x22 to "MBC7+SENSOR+RUMBLE+RAM+BATTERY",
    0xFC to "POCKET CAMERA",
    0xFD to "BANDAI TAMA5",
    0xFE to "HuC3",
    0xFF to "HuC1+RAM+BATTERY",
)
