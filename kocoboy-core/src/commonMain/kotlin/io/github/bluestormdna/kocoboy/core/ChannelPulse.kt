package io.github.bluestormdna.kocoboy.core

import kotlin.experimental.or

@OptIn(ExperimentalUnsignedTypes::class)
class ChannelPulse {
    private val waveForm = ubyteArrayOf(0x01u, 0x81u, 0x87u, 0x7Eu)
    var sample: Byte = 0

    private var isEnabled = false

    private var counter: Long = 0
    private var freq: UShort = 0u
    private var wavePatternPosition = 0
    private var envelopeCounter: Int = 0
    private var envelopeVolume: Int = 0
    private var sweepCounter: Int = 0

    var nr10: Byte = 0
    private var sweepTime = 0
    private var sweepStep = 0
    private var sweepShift = 0

    var nrx1: Byte = 0
    private var wavePatternDuty = 0
    private var length = 0

    var nrx2: Byte = 0
    private var envelopeInitialVolume = 0
    private var envelopeDirection = 0
    private var envelopeSweep = 0

    private var nrx3periodLo: UByte = 0u

    var nrx4: Byte = 0
    private var periodHi: UByte = 0u
    private var trigger = false
    private var lengthEnable = false

    fun sweep(value: Byte) {
        nr10 = value or 0x80.toByte()
        sweepTime = (value.toInt() ushr 4) and 0x7
        sweepStep = (value.toInt() ushr 3) and 0x1
        sweepShift = value.toInt() and 0x7
    }

    fun setNRx1LengthTimerDutyCycle(value: Byte) {
        nrx1 = value or 0x3F
        wavePatternDuty = (value.toInt() ushr 6) and 0x3
        length = value.toInt() and 0x3F
    }

    fun setNRx2EnvelopeVolume(value: Byte) {
        nrx2 = value
        envelopeInitialVolume = value.toInt() ushr 4
        envelopeDirection = (value.toInt() ushr 3) and 0x1
        envelopeSweep = value.toInt() and 0x7
    }

    fun setNRx3PeriodLow(value: Byte) {
        nrx3periodLo = value.toUByte()
    }

    fun setNRx4PeriodHiControl(value: Byte) {
        nrx4 = value or 0xBF.toByte()
        trigger = (value.toInt() and 0x80) != 0
        lengthEnable = (value.toInt() and 0x40) != 0
        periodHi = (value.toUInt() and 0x7u).toUByte()

        if (trigger) {
            trigger = false

            handleTrigger()
        }
    }

    private fun handleTrigger() {
        isEnabled = true
        if (length == 0) length = 64
        freq = (periodHi.toUInt() shl 8 or nrx3periodLo.toUInt()).toUShort()
        envelopeCounter = envelopeSweep
        envelopeVolume = envelopeInitialVolume

        sweepCounter = sweepTime
        if (sweepShift > 0) sweep()
    }

    fun tickLength() {
        if (length > 0) length--
        if (length == 0 && lengthEnable) {
            isEnabled = false
        }
    }

    fun tickSweep() {
        if (sweepCounter > 0) sweepCounter--
        if (sweepTime > 0) {
            if (sweepShift > 0 && sweepCounter == 0) {
                val sweep = sweep()
                if (sweep <= 2047 && sweepShift > 0) {
                    periodHi = (sweep ushr 8).toUByte()
                    nrx3periodLo = (sweep and 0xFF).toUByte()

                    sweep()
                }

                sweepCounter = sweepTime
            }
        }
    }

    private fun sweep(): Int {
        val step = if (sweepStep == 1) -1 else 1
        val sweep: UShort = (freq + ((freq.toUInt() shr sweepShift) * step.toUShort())).toUShort()
        if (sweep > 2047u) isEnabled = false
        return sweep.toInt()
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

    fun tickSampleGenerator(cycles: Int) {
        counter -= cycles

        if (counter <= 0) {
            freq = (periodHi.toUInt() shl 8 or nrx3periodLo.toUInt()).toUShort()
            counter = ((2048u - freq) * 2u).toLong()

            wavePatternPosition = (wavePatternPosition + 1) and 0x7
            val wave = waveForm[wavePatternDuty]
            val output = (wave.toInt() ushr wavePatternPosition) and 0x1

            sample = if (isEnabled) (output * envelopeVolume).toByte() else 0
        }
    }

    fun isEnabled(): Boolean {
        return isEnabled
    }

    fun disable() {
        isEnabled = false
    }
}