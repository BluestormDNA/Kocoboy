package io.github.bluestormdna.kocoboy.core

import kotlin.experimental.or

@OptIn(ExperimentalUnsignedTypes::class)
class ChannelWave {
    var nr30: Byte = 0
    private var isEnabled = false
    private var dacOn = false

    private var nr31Length: Byte = 0
    private var length = 0

    var nr32: Byte = 0
    private var volume: Int = 0

    private var nrx3PeriodLo: UByte = 0u

    var nrx4: Byte = 0
    private var periodHi: UByte = 0u
    private var trigger = false
    private var lengthEnable = false

    private var lastClock: Long = 0
    private var periodCycles: Int = 2
    private var counter: Int = 2
    private var wavePos: Int = 0
    private var lastFetchClock = 0L

    private val wavePatternRAM = UByteArray(16)
    var sample: Byte = 0

    fun setNR30DacEnable(value: Byte) {
        nr30 = value or 0x7F
        dacOn = value.toUInt() and 0x80u != 0u
        if (!dacOn) isEnabled = false
    }

    fun setNR31Length(value: Byte) {
        nr31Length = value
        length = 256 - (value.toInt() and 0xFF)
    }

    fun setNR32OutputLevel(value: Byte) {
        nr32 = value or 0x9F.toByte()
        volume = (value.toInt() ushr 5) and 0x3
    }

    fun setNRx3PeriodLow(value: Byte) {
        nrx3PeriodLo = value.toUByte()
        reloadPeriod()
    }

    fun setNRx4PeriodHiControl(value: Byte, nextStepClocksLength: Boolean) {
        nrx4 = value or 0xBF.toByte()
        trigger = (value.toUInt() and 0x80u) != 0u
        val wasEnabled = lengthEnable
        lengthEnable = (value.toUInt() and 0x40u) != 0u
        periodHi = (value.toUInt() and 0x7u).toUByte()
        reloadPeriod()

        if (!nextStepClocksLength && !wasEnabled && lengthEnable && length > 0) {
            length--
            if (length == 0 && !trigger) isEnabled = false
        }

        if (trigger) {
            trigger = false
            handleTrigger(nextStepClocksLength)
        }
    }

    private fun handleTrigger(nextStepClocksLength: Boolean) {
        if (isEnabled && counter <= 2) corruptOnRetrigger()
        isEnabled = dacOn
        if (length == 0) {
            length = 256
            if (!nextStepClocksLength && lengthEnable) length--
        }
        reloadPeriod()
        // After a trigger the first fetch comes 6 dots later than a normal period
        counter = periodCycles + 6
        wavePos = 0
        // A fetch before the trigger is outside the window
        lastFetchClock = lastClock - 2
    }

    // Retriggering within 2 dots of a fetch overwrites the start of wave RAM
    private fun corruptOnRetrigger() {
        val next = ((wavePos + 1) ushr 1) and 0xF
        if (next < 4) {
            wavePatternRAM[0] = wavePatternRAM[next]
        } else {
            wavePatternRAM.copyInto(wavePatternRAM, 0, next and 0xC, (next and 0xC) + 4)
        }
    }

    // While the channel plays, the CPU only reaches wave RAM in the 2 dots after a fetch, and gets that byte
    private fun justFetched(): Boolean = lastClock - lastFetchClock in 0..1

    fun readRam(index: Int): Int = when {
        !isEnabled -> wavePatternRAM[index].toInt()
        justFetched() -> wavePatternRAM[wavePos ushr 1].toInt()
        else -> 0xFF
    }

    fun writeRam(index: Int, value: UByte) {
        when {
            !isEnabled -> wavePatternRAM[index] = value
            justFetched() -> wavePatternRAM[wavePos ushr 1] = value
        }
    }

    // While powered off DMG still takes the length, but not the rest of the register
    fun setLengthOnly(value: Byte) {
        length = 256 - (value.toInt() and 0xFF)
    }

    // DMG keeps the length counter when the APU is powered off
    fun powerOff() {
        val keptLength = length
        setNR30DacEnable(0)
        setNR31Length(0)
        setNR32OutputLevel(0)
        setNRx3PeriodLow(0)
        setNRx4PeriodHiControl(0, false)
        length = keptLength
        isEnabled = false
    }

    fun tickLength() {
        if (!lengthEnable || length == 0) return
        length--
        if (length == 0) isEnabled = false
    }

    fun advanceTo(clock: Long) {
        counter -= (clock - lastClock).toInt()
        lastClock = clock
        if (counter <= 0) {
            val steps = -counter / periodCycles + 1
            counter += steps * periodCycles
            wavePos = (wavePos + steps) and 0x1F
            lastFetchClock = clock + counter - periodCycles
        }
        if (isEnabled) {
            val shift = if (wavePos and 1 == 0) 4 else 0
            val wave = (wavePatternRAM[wavePos ushr 1].toInt() ushr shift) and 0xF
            val volumeShift = volume - 1 and 0xF
            sample = (wave shr volumeShift).toByte()
        } else {
            sample = 0
        }
    }

    private fun reloadPeriod() {
        val freq = (periodHi.toUInt() shl 8 or nrx3PeriodLo.toUInt()).toUShort()
        periodCycles = ((2048u - freq) * 2u).toInt()
    }

    fun resyncTo(clock: Long) {
        lastClock = clock
    }

    fun isEnabled(): Boolean = isEnabled
}
