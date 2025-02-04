package io.github.bluestormdna.kocoboy.core.cartridge

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime


@OptIn(ExperimentalUnsignedTypes::class)
class MBC3(private val rom: UByteArray): Cartridge {

    private val eRam = UByteArray(0x8000)

    private var eRamEnabled = false
    private var romBank = 1
    private var ramBank = 0

    // RTC
    private var rtcS = 0
    private var rtcM = 0
    private var rtcH = 0
    private var rtcDl = 0
    private var rtcDh = 0

    override fun readLoROM(addr: UShort): UByte {
        return rom[addr.toInt()]
    }

    override fun readHiROM(addr: UShort): UByte {
        return rom[(ROM_OFFSET * romBank) + (addr and 0x3FFFu).toInt()]
    }

    override fun writeROM(addr: UShort, value: UByte) {
        when (addr) {
            in 0x0000u..0x1FFFu -> eRamEnabled = value == 0x0A.toUByte()
            in 0x2000u..0x3FFFu -> {
                romBank = value.toInt() and 0x7F
                if (romBank == 0x00) {
                    romBank++
                }
            }

            in 0x4000u..0x5FFFu -> {
                when (value) {
                    in 0x00u..0x03u,
                    in 0x08u..0xC0u -> ramBank = value.toInt() and 0xFF
                }
            }

            in 0x6000u..0x7FFFu -> { // Latch Clock Data
                val now: Instant = Clock.System.now()
                val dateTime: LocalDateTime = now.toLocalDateTime(TimeZone.currentSystemDefault())
                rtcS = dateTime.second
                rtcM = dateTime.minute
                rtcH = dateTime.hour
                rtcDl = dateTime.dayOfYear and 0xFF
                rtcDh = dateTime.dayOfYear shr 8 and 0x1 // MSB of 9 bit dh | dl
            }
        }
    }

    override fun readERAM(addr: UShort): UByte {
        if (!eRamEnabled) return 0xFFu

        return when(ramBank) {
            in 0x00..0x03 -> eRam[(ERAM_OFFSET * ramBank + (addr and 0x1FFFu).toInt())]
            0x08 -> rtcS.toUByte()
            0x09 -> rtcM.toUByte()
            0x0A -> rtcH.toUByte()
            0x0B -> rtcDl.toUByte()
            0x0C -> rtcDh.toUByte()
            else -> 0xFFu
        }
    }

    override fun writeERAM(addr: UShort, value: UByte) {
        if (!eRamEnabled) return

        when(ramBank) {
            in 0x00..0x03 -> eRam[(ERAM_OFFSET * ramBank) + (addr and 0x1FFFu).toInt()] = value
            0x08 -> rtcS = value.toInt() and 0xFF
            0x09 -> rtcM = value.toInt() and 0xFF
            0x0A -> rtcH = value.toInt() and 0xFF
            0x0B -> rtcDl = value.toInt() and 0xFF
            0x0C -> rtcDh = value.toInt() and 0xFF
        }

    }

    companion object {
        private const val ROM_OFFSET = 0x4000
        private const val ERAM_OFFSET = 0x2000
    }
}