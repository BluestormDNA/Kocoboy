package io.github.bluestormdna.kocoboy.core.cartridge

interface CartridgeHeader {
    val mbc: String
    val name: String
}

class DefaultCartridgeHeader(rom: ByteArray) : CartridgeHeader {
    override val mbc = rom[0x147].toString()
    override val name = rom
        .decodeToString(0x134, 0x144)
        .filterNot { char -> char.code == 0 || char.code == 0x80 }
}

class EmptyCartridgeHeader : CartridgeHeader {
    override val mbc: String = "---"
    override val name: String = "Empty Slot"
}