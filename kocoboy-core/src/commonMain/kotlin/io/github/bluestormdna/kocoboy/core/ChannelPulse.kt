package io.github.bluestormdna.kocoboy.core

import kotlin.experimental.or

@OptIn(ExperimentalUnsignedTypes::class)
class ChannelPulse {
    private val waveForm = ubyteArrayOf(0x01u, 0x81u, 0x87u, 0x7Eu)
    var sample: Byte = 0

    private var isEnabled = false

    private var lastClock: Long = 0
    private var periodCycles: Int = 4
    private var counter: Int = 4
    private var freq: UShort = 0u
    private var wavePatternPosition = 0
    private var envelopeCounter: Int = 0
    private var envelopeVolume: Int = 0
    private var sweepCounter: Int = 0

    var nr10: Byte = 0

    // The sweep works on its own copy of the frequency, taken at trigger
    // Hardware reloads the sweep timer with 8 when the pace is 0
    private val sweepPeriod: Int get() = if (sweepTime > 0) sweepTime else 8

    private var shadowFreq = 0
    private var sweepEnabled = false
    private var negateUsed = false
    private var sweepTime = 0
    private var sweepNegate = false
    private var sweepShift = 0

    var nrx1: Byte = 0
    private var wavePatternDuty = 0
    private var length = 0

    var nrx2: Byte = 0
    private var envelopeInitialVolume = 0
    private var envelopeDirection = 0
    private var envelopeSweep = 0
    private var dacOn = false

    private var nrx3periodLo: UByte = 0u

    var nrx4: Byte = 0
    private var periodHi: UByte = 0u
    private var trigger = false
    private var lengthEnable = false

    fun sweep(value: Byte) {
        nr10 = value or 0x80.toByte()
        val wasNegate = sweepNegate
        sweepTime = (value.toInt() ushr 4) and 0x7
        sweepNegate = (value.toInt() and 0x08) != 0
        sweepShift = value.toInt() and 0x7
        // Leaving negate mode after a calculation used it disables the channel
        if (wasNegate && !sweepNegate && negateUsed) isEnabled = false
    }

    fun setNRx1LengthTimerDutyCycle(value: Byte) {
        nrx1 = value or 0x3F
        wavePatternDuty = (value.toInt() ushr 6) and 0x3
        length = 64 - (value.toInt() and 0x3F)
    }

    fun setNRx2EnvelopeVolume(value: Byte) {
        nrx2 = value
        envelopeInitialVolume = value.toInt() ushr 4
        envelopeDirection = (value.toInt() ushr 3) and 0x1
        envelopeSweep = value.toInt() and 0x7
        dacOn = (value.toInt() and 0xF8) != 0
        if (!dacOn) isEnabled = false
    }

    fun setNRx3PeriodLow(value: Byte) {
        nrx3periodLo = value.toUByte()
        reloadPeriod()
    }

    fun setNRx4PeriodHiControl(value: Byte, nextStepClocksLength: Boolean) {
        nrx4 = value or 0xBF.toByte()
        trigger = (value.toInt() and 0x80) != 0
        val wasEnabled = lengthEnable
        lengthEnable = (value.toInt() and 0x40) != 0
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
        isEnabled = dacOn
        if (length == 0) {
            length = 64
            if (!nextStepClocksLength && lengthEnable) length--
        }
        reloadPeriod()
        counter = periodCycles
        envelopeCounter = envelopeSweep
        envelopeVolume = envelopeInitialVolume

        shadowFreq = freq.toInt()
        sweepCounter = sweepPeriod
        sweepEnabled = sweepTime != 0 || sweepShift != 0
        negateUsed = false
        if (sweepShift > 0) calculateSweep()
    }

    fun tickLength() {
        if (!lengthEnable || length == 0) return
        length--
        if (length == 0) isEnabled = false
    }

    fun tickSweep() {
        if (sweepCounter > 0) sweepCounter--
        if (sweepCounter != 0) return

        // The timer reloads even when nothing will sweep
        sweepCounter = sweepPeriod
        if (!sweepEnabled || sweepTime == 0) return

        val next = calculateSweep()
        if (next <= 2047 && sweepShift > 0) {
            shadowFreq = next
            periodHi = (next ushr 8).toUByte()
            nrx3periodLo = (next and 0xFF).toUByte()
            reloadPeriod()

            // Overflow is checked again on the new value, which sound tests 4, 5 and 7 rely on
            calculateSweep()
        }
    }

    private fun calculateSweep(): Int {
        val delta = shadowFreq shr sweepShift
        if (sweepNegate) negateUsed = true
        val next = if (sweepNegate) shadowFreq - delta else shadowFreq + delta
        if (next > 2047) isEnabled = false
        return next
    }

    fun tickEnvelope() {
        if (envelopeSweep > 0) {
            if (envelopeCounter > 0) envelopeCounter--
            if (envelopeCounter == 0) {
                val direction = if (envelopeDirection == 1) 1 else -1
                envelopeVolume = (envelopeVolume + direction).coerceAtLeast(0) and 0xF
                envelopeCounter = envelopeSweep
            }
        }
    }

    fun advanceTo(clock: Long) {
        counter -= (clock - lastClock).toInt()
        lastClock = clock
        if (counter <= 0) {
            val steps = -counter / periodCycles + 1
            counter += steps * periodCycles
            wavePatternPosition = (wavePatternPosition + steps) and 0x7
        }
        val wave = waveForm[wavePatternDuty]
        val output = (wave.toInt() ushr wavePatternPosition) and 0x1
        sample = if (isEnabled) (output * envelopeVolume).toByte() else 0
    }

    private fun reloadPeriod() {
        freq = (periodHi.toUInt() shl 8 or nrx3periodLo.toUInt()).toUShort()
        periodCycles = ((2048u - freq) * 4u).toInt()
    }

    fun isEnabled(): Boolean = isEnabled

    fun disable() {
        isEnabled = false
    }

    fun resyncTo(clock: Long) {
        lastClock = clock
    }
}
