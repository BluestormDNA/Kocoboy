package io.github.bluestormdna.kocoboy.core.cartridge

 @OptIn(ExperimentalUnsignedTypes::class)
 class MBC0(private val rom: UByteArray) : Cartridge {

     override fun readLoROM(addr: UShort): UByte {
         return rom[addr.toInt()]
     }

     override fun readHiROM(addr: UShort): UByte {
         return rom[addr.toInt()]
     }

     override fun writeROM(addr: UShort, value: UByte) {
         //MBC0 should ignore writes
     }

     override fun readERAM(addr: UShort): UByte {
         return 0xFFu
     }

     override fun writeERAM(addr: UShort, value: UByte) {
         //MBC0 should ignore writes
     }
 }