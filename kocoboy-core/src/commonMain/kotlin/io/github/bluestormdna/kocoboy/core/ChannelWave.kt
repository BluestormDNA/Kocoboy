package io.github.bluestormdna.kocoboy.core

import kotlin.experimental.or

@OptIn(ExperimentalUnsignedTypes::class)
class ChannelWave {
    var nr30: Byte = 0
    private var isEnabled = false

    private var nr31Length: Byte = 0
    private var length = 0

    var nr32: Byte = 0
    private var volume: Int = 0

    private var nrx3PeriodLo: UByte = 0u

    var nrx4: Byte = 0
    private var periodHi: UByte = 0u
    private var trigger = false
    private var lengthEnable = false

    private var counter: Int = 0
    private var waveIndex: Int = 0
    private var nibble = 1
    private var freq: UShort = 0u

    var wavePatternRAM = UByteArray(16)
    var sample: Byte = 0

    fun setNR30DacEnable(value: Byte) {
        nr30 = value or 0x7F
        isEnabled = value.toUInt() and 0x80u != 0u
    }

    fun setNR31Length(value: Byte) {
        nr31Length = value
    }

    fun setNR32OutputLevel(value: Byte) {
        nr32 = value or 0x9F.toByte()
        volume = (value.toInt() ushr 5) and 0x3
    }

    fun setNRx3PeriodLow(value: Byte) {
        nrx3PeriodLo = value.toUByte()
    }

    fun setNRx4PeriodHiControl(value: Byte) {
        nrx4 = value or 0xBF.toByte()
        trigger = (value.toUInt() and 0x80u) != 0u
        lengthEnable = (value.toUInt() and 0x40u) != 0u
        periodHi = (value.toUInt() and 0x7u).toUByte()

        if (trigger) {
            trigger = false
            handleTrigger()
        }
    }

    private fun handleTrigger() {
        isEnabled = true
        if (length == 0) length = 256
        freq = (periodHi.toUInt() shl 8 or nrx3PeriodLo.toUInt()).toUShort()
        waveIndex = 0
        nibble = 1
    }

    fun tickLength() {
        if (length > 0) length--
        if (length == 0 && lengthEnable) {
            isEnabled = false
        }
    }

    fun tickSampleGenerator(cycles: Int) {
        counter -= cycles

        if (counter <= 0) {
            freq = (periodHi.toUInt() shl 8 or nrx3PeriodLo.toUInt()).toUShort()
            counter = ((2048u - freq) * 2u).toInt()

            val wave = (wavePatternRAM[waveIndex].toInt() ushr (4 * nibble)) and 0xF
            nibble = nibble xor 1
            waveIndex = (waveIndex + nibble) and 0xF

            if (isEnabled) {
                val volumeShift = volume - 1 and 0xF
                sample = (wave shr volumeShift).toByte()
            } else {
                sample = 0
            }
        }
    }

    fun isEnabled(): Boolean = isEnabled
}
