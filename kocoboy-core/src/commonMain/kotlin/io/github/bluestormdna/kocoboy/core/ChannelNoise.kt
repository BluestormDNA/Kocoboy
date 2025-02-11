package io.github.bluestormdna.kocoboy.core

import kotlin.experimental.or

class ChannelNoise {
    private var isEnabled = false

    private var nr41: Byte = 0
    private var length = 0

    var nr42: Byte = 0
    private var envelopeInitialVolume = 0
    private var envelopeDirection = 0
    private var envelopeSweep = 0

    var nr43: Byte = 0
    private var clockShift = 0
    private var lfsrWidth = 0
    private var clockDivider = 0

    private var period = 0

    var nr44: Byte = 0
    private var trigger = false
    private var lengthEnable = false

    private var envelopeCounter = 0
    private var envelopeVolume = 0

    private var counter: Int = 0

    private var lfsr = 0x7FFF

    var sample: Byte = 0

    fun setNR41Length(value: Byte) {
        nr41 = value
        length = value.toInt() and 0x3F
    }

    fun setNRx2EnvelopeVolume(value: Byte) {
        nr42 = value
        envelopeInitialVolume = value.toInt() ushr 4
        envelopeDirection = (value.toInt() ushr 3) and 0x1
        envelopeSweep = value.toInt() and 0x7
    }

    fun setNR43Frequency(value: Byte) {
        nr43 = value
        clockShift = (value.toInt() shr 4) and 0xFF
        lfsrWidth = (value.toInt() shr 3) and 0x1
        clockDivider = value.toInt() and 0x7

        val div = (clockDivider shl 4).coerceAtLeast(8)
        period = div shl clockShift
    }

    fun setNR44Control(value: Byte) {
        nr44 = value or 0xBF.toByte()
        trigger = (value.toInt() and 0x80) != 0
        lengthEnable = (value.toInt() and 0x40) != 0

        if (trigger) {
            isEnabled = true
            counter = period
            envelopeCounter = envelopeSweep
            envelopeVolume = envelopeInitialVolume
            lfsr = 0x7FFF
        }
    }

    fun tickLength() {
        if (length > 0) length--
        if (length == 0 && lengthEnable) {
            isEnabled = false
        }
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

    fun isEnabled(): Boolean {
        return isEnabled
    }

    fun tickSampleGenerator(cycles: Int) {
        counter -= cycles

        if (counter <= 0) {
            counter = period
            tickLFSR()
            sample = if (isEnabled) (((lfsr and 0x1) xor 1) * envelopeVolume).toByte() else 0
        }
    }

    private fun tickLFSR() {
        val b = (lfsr and 0x1) xor (lfsr ushr 1 and 0x1)
        lfsr = lfsr ushr 1
        lfsr = lfsr or (b shl 14)
        if (lfsrWidth != 0) {
            lfsr = lfsr and (1 shl 6).inv()
            lfsr = lfsr or (b shl 6)
        }
    }

}