package io.github.bluestormdna.kocoboy.core

import io.github.bluestormdna.kocoboy.host.Host
import kotlin.experimental.or

class APU(private val host: Host) {
    private val bufferSize = 4096
    private var bufferPointer = 0
    private val sampleBuffer = ByteArray(bufferSize)

    private val channel1 = ChannelPulse()
    private val channel2 = ChannelPulse()
    private val channel3 = ChannelWave()
    private val channel4 = ChannelNoise()

    private var nr50: Byte = 0
    private var vinL = 0
    private var vinR = 0
    private var masterVolL = 0
    private var masterVolR = 0

    private var nr51: Byte = 0
    private var channel4L = false
    private var channel3L = false
    private var channel2L = false
    private var channel1L = false
    private var channel4R = false
    private var channel3R = false
    private var channel2R = false
    private var channel1R = false

    private var nr52: Byte = 0
    private var apuEnabled = false

    private var sampleCounter = 0
    private var frameSequencerCounter = 0
    private var frameSequencerStep = 0

    fun update(cycles: Int) {
        sampleCounter -= cycles
        frameSequencerCounter -= cycles

        channel1.tickSampleGenerator(cycles)
        channel2.tickSampleGenerator(cycles)
        channel3.tickSampleGenerator(cycles)
        channel4.tickSampleGenerator(cycles)

        if (frameSequencerCounter <= 0) {
            frameSequencerCounter += 8192 // DMG / 515HZ

            if ((frameSequencerStep and 0x1) == 0) {
                channel1.tickLength()
                channel2.tickLength()
                channel3.tickLength()
                channel4.tickLength()
            }

            if (frameSequencerStep == 2 || frameSequencerStep == 6) {
                channel1.tickSweep()
            }

            if (frameSequencerStep == 7) {
                channel1.tickEnvelope()
                channel2.tickEnvelope()
                channel4.tickEnvelope()
            }

            frameSequencerStep = (frameSequencerStep + 1) and 0x7
        }

        if (sampleCounter <= 0) {
            sampleCounter += 95 // DMG / 44100Hz

            if (!apuEnabled) return

            val ch1LSample = if (channel1L) channel1.sample else 0
            val ch1RSample = if (channel1R) channel1.sample else 0

            val ch2LSample = if (channel2L) channel2.sample else 0
            val ch2RSample = if (channel2R) channel2.sample else 0

            val ch3LSample = if (channel3L) channel3.sample else 0
            val ch3RSample = if (channel3R) channel3.sample else 0

            val ch4LSample = if (channel4L) channel4.sample else 0
            val ch4RSample = if (channel4R) channel4.sample else 0

            val mixedL = ch1LSample + ch2LSample + ch3LSample + ch4LSample + 128
            val mixedR = ch1RSample + ch2RSample + ch3RSample + ch4RSample + 128

            // todo handle Main Volume
            sampleBuffer[bufferPointer++] = mixedL.toByte()
            sampleBuffer[bufferPointer++] = mixedR.toByte()

            if (bufferPointer >= bufferSize) {
                host.play(sampleBuffer)
                bufferPointer = 0
            }
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun write(addr: Int, value: Byte) {
        if (!apuEnabled && addr < 0x26) return

        when (addr) {
            0x10 -> channel1.sweep(value)
            0x11 -> channel1.setNRx1LengthTimerDutyCycle(value)
            0x12 -> channel1.setNRx2EnvelopeVolume(value)
            0x13 -> channel1.setNRx3PeriodLow(value)
            0x14 -> channel1.setNRx4PeriodHiControl(value)

            0x16 -> channel2.setNRx1LengthTimerDutyCycle(value)
            0x17 -> channel2.setNRx2EnvelopeVolume(value)
            0x18 -> channel2.setNRx3PeriodLow(value)
            0x19 -> channel2.setNRx4PeriodHiControl(value)

            0x1A -> channel3.setNR30DacEnable(value)
            0x1B -> channel3.setNR31Length(value)
            0x1C -> channel3.setNR32OutputLevel(value)
            0x1D -> channel3.setNRx3PeriodLow(value)
            0x1E -> channel3.setNRx4PeriodHiControl(value)

            0x20 -> channel4.setNR41Length(value)
            0x21 -> channel4.setNRx2EnvelopeVolume(value)
            0x22 -> channel4.setNR43Frequency(value)
            0x23 -> channel4.setNR44Control(value)

            0x24 -> setNR50MasterVolume(value)
            0x25 -> setNR51Panning(value)
            0x26 -> setNR52MasterControl(value)

            in 0x30..0x3F -> channel3.wavePatternRAM[addr and 0xF] = value.toUByte()
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun read(addr: Int): Byte = when (addr) {
        0x10 -> channel1.nr10
        0x11 -> channel1.nrx1
        0x12 -> channel1.nrx2
        0x13 -> 0xFF.toByte()
        0x14 -> channel1.nrx4

        0x16 -> channel2.nrx1
        0x17 -> channel2.nrx2
        0x18 -> 0xFF.toByte()
        0x19 -> channel2.nrx4

        0x1A -> channel3.nr30
        0x1B -> 0xFF.toByte()
        0x1C -> channel3.nr32
        0x1D -> 0xFF.toByte()
        0x1E -> channel3.nrx4

        0x20 -> 0xFF.toByte()
        0x21 -> channel4.nr42
        0x22 -> channel4.nr43
        0x23 -> channel4.nr44

        0x24 -> nr50
        0x25 -> nr51
        0x26 -> getNR52MasterControl()
        in 0x30..0x3F -> channel3.wavePatternRAM[addr and 0xF].toByte()
        else -> 0xFF.toByte().also {
            println("Attempting to read SPU $addr")
        }
    }

    private fun getNR52MasterControl(): Byte {
        val channel4 = if (channel4.isEnabled()) 0x8 else 0
        val channel3 = if (channel3.isEnabled()) 0x4 else 0
        val channel2 = if (channel2.isEnabled()) 0x2 else 0
        val channel1 = if (channel1.isEnabled()) 0x1 else 0

        val channelStatus = (channel4 or channel3 or channel2 or channel1).toByte()
        return nr52 or channelStatus
    }

    private fun setNR50MasterVolume(value: Byte) {
        nr50 = value
        vinR = value.toInt() ushr 3 and 0x1
        vinL = value.toInt() ushr 7 and 0x1
        masterVolR = value.toInt() and 0x7
        masterVolL = value.toInt() ushr 4 and 0x7
    }

    private fun setNR51Panning(value: Byte) {
        nr51 = value
        channel4L = ((value.toInt() shr 7) and 0x1) != 0
        channel3L = ((value.toInt() shr 6) and 0x1) != 0
        channel2L = ((value.toInt() shr 5) and 0x1) != 0
        channel1L = ((value.toInt() shr 4) and 0x1) != 0
        channel4R = ((value.toInt() shr 3) and 0x1) != 0
        channel3R = ((value.toInt() shr 2) and 0x1) != 0
        channel2R = ((value.toInt() shr 1) and 0x1) != 0
        channel1R = (value.toInt() and 0x1) != 0
    }

    private fun setNR52MasterControl(value: Byte) {
        nr52 = (value.toInt() or 0x70 and 0xF.inv()).toByte()
        val wasEnabled = apuEnabled
        apuEnabled = (value.toInt() and 0x80) != 0

        if (wasEnabled && !apuEnabled) {
            resetAPU()
        }
    }

    // todo check if further in-channel clean-up needed
    private fun resetAPU() {
        channel1.sweep(0)
        channel1.setNRx1LengthTimerDutyCycle(0)
        channel1.setNRx2EnvelopeVolume(0)
        channel1.setNRx3PeriodLow(0)
        channel1.setNRx4PeriodHiControl(0)
        channel1.disable()

        channel2.setNRx1LengthTimerDutyCycle(0)
        channel2.setNRx2EnvelopeVolume(0)
        channel2.setNRx3PeriodLow(0)
        channel2.setNRx4PeriodHiControl(0)
        channel2.disable()

        channel3.setNR30DacEnable(0)
        channel3.setNR31Length(0)
        channel3.setNR32OutputLevel(0)
        channel3.setNRx3PeriodLow(0)
        channel3.setNRx4PeriodHiControl(0)

        channel4.setNR41Length(0)
        channel4.setNRx2EnvelopeVolume(0)
        channel4.setNR43Frequency(0)
        channel4.setNR44Control(0)

        setNR50MasterVolume(0)
        setNR51Panning(0)
    }
}
