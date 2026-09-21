package io.github.bluestormdna.kocoboy.core

import io.github.bluestormdna.kocoboy.host.Host
import kotlin.experimental.or

class APU(private val host: Host, private val scheduler: Scheduler, private val timer: Timer) {
    private val bufferSize = 4096
    private var bufferPointer = 0
    private val sampleBuffer = ByteArray(bufferSize)

    private var channel1 = ChannelPulse()
    private var channel2 = ChannelPulse()
    private var channel3 = ChannelWave()
    private var channel4 = ChannelNoise()

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

    private var frameSequencerStep = 0

    private val clock: Long get() = scheduler.clock

    // Length is clocked on the even steps
    private val nextStepClocksLength: Boolean get() = (frameSequencerStep and 1) == 0

    private var nextSample: Long = 0

    fun reset() {
        channel1 = ChannelPulse()
        channel2 = ChannelPulse()
        channel3 = ChannelWave()
        channel4 = ChannelNoise()
        nr50 = 0
        vinL = 0
        vinR = 0
        masterVolL = 0
        masterVolR = 0
        nr51 = 0
        channel1L = false
        channel2L = false
        channel3L = false
        channel4L = false
        channel1R = false
        channel2R = false
        channel3R = false
        channel4R = false
        nr52 = 0
        apuEnabled = false
        frameSequencerStep = 0
        bufferPointer = 0
        start()
    }

    fun start() {
        scheduleNextStep()
        nextSample = clock + SAMPLE_PERIOD
        resyncChannels()
    }

    private fun resyncChannels() {
        channel1.resyncTo(clock)
        channel2.resyncTo(clock)
        channel3.resyncTo(clock)
        channel4.resyncTo(clock)
    }

    private fun settleChannels() {
        channel1.advanceTo(clock)
        channel2.advanceTo(clock)
        channel3.advanceTo(clock)
        channel4.advanceTo(clock)
    }

    fun onFrameSequencer() {
        val at = scheduler.firedAt
        // The samples before the step still hear the old length, sweep and envelope
        renderSamples(at)
        // The event fired on an edge, so the next one is a whole period away
        scheduler.scheduleAt(Event.APU_SEQUENCER, at + FRAME_SEQUENCER_PERIOD)
        step(at)
    }

    // Writing DIV zeroes the divider, so a high bit 12 falls there and clocks an extra step
    // Called before the write lands, while the outgoing counter can still be read
    fun onDividerReset() {
        if (!apuEnabled) return
        val at = scheduler.clock
        if (timer.counter and SEQUENCER_BIT != 0L) {
            renderSamples(at)
            step(at)
        }
        scheduler.scheduleAt(Event.APU_SEQUENCER, at + FRAME_SEQUENCER_PERIOD)
    }

    // Only needed when the phase is unknown: at start and at power on
    private fun scheduleNextStep() {
        val counter = timer.counter
        scheduler.scheduleAt(
            Event.APU_SEQUENCER,
            scheduler.clock + (FRAME_SEQUENCER_PERIOD - counter.mod(FRAME_SEQUENCER_PERIOD)),
        )
    }

    private fun step(at: Long) {
        if (nextStepClocksLength) {
            channel1.tickLength()
            channel2.tickLength()
            channel3.tickLength()
            channel4.tickLength()
        }

        if (frameSequencerStep == 2 || frameSequencerStep == 6) {
            channel1.advanceTo(at)
            channel1.tickSweep()
        }

        if (frameSequencerStep == 7) {
            channel1.tickEnvelope()
            channel2.tickEnvelope()
            channel4.tickEnvelope()
        }

        frameSequencerStep = (frameSequencerStep + 1) and 0x7
    }

    // Every sample due by until, each mixed at its own instant
    private fun renderSamples(until: Long) {
        if (!apuEnabled) {
            // Powered off outputs nothing, only the sample phase moves on
            if (nextSample <= until) {
                nextSample += ((until - nextSample) / SAMPLE_PERIOD + 1) * SAMPLE_PERIOD
            }
            return
        }

        while (nextSample <= until) {
            channel1.advanceTo(nextSample)
            channel2.advanceTo(nextSample)
            channel3.advanceTo(nextSample)
            channel4.advanceTo(nextSample)

            val ch1LSample = if (channel1L) channel1.sample else 0
            val ch1RSample = if (channel1R) channel1.sample else 0

            val ch2LSample = if (channel2L) channel2.sample else 0
            val ch2RSample = if (channel2R) channel2.sample else 0

            val ch3LSample = if (channel3L) channel3.sample else 0
            val ch3RSample = if (channel3R) channel3.sample else 0

            val ch4LSample = if (channel4L) channel4.sample else 0
            val ch4RSample = if (channel4R) channel4.sample else 0

            val sumL = ch1LSample + ch2LSample + ch3LSample + ch4LSample
            val sumR = ch1RSample + ch2RSample + ch3RSample + ch4RSample

            val mixedL = sumL * (masterVolL + 1) / 8 + 128
            val mixedR = sumR * (masterVolR + 1) / 8 + 128

            sampleBuffer[bufferPointer++] = mixedL.toByte()
            sampleBuffer[bufferPointer++] = mixedR.toByte()

            if (bufferPointer >= bufferSize) {
                host.play(sampleBuffer)
                bufferPointer = 0
            }

            nextSample += SAMPLE_PERIOD
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun write(addr: Int, value: Byte) {
        if (!apuEnabled && addr < 0x26) return

        // A write changes what later samples hear, so the ones already due go out first
        renderSamples(clock)

        when (addr) {
            in 0x10..0x14 -> channel1.advanceTo(clock)
            in 0x16..0x19 -> channel2.advanceTo(clock)
            in 0x1A..0x1E -> channel3.advanceTo(clock)
            in 0x20..0x23 -> channel4.advanceTo(clock)
        }

        when (addr) {
            0x10 -> channel1.sweep(value)
            0x11 -> channel1.setNRx1LengthTimerDutyCycle(value)
            0x12 -> channel1.setNRx2EnvelopeVolume(value)
            0x13 -> channel1.setNRx3PeriodLow(value)
            0x14 -> channel1.setNRx4PeriodHiControl(value, nextStepClocksLength)

            0x16 -> channel2.setNRx1LengthTimerDutyCycle(value)
            0x17 -> channel2.setNRx2EnvelopeVolume(value)
            0x18 -> channel2.setNRx3PeriodLow(value)
            0x19 -> channel2.setNRx4PeriodHiControl(value, nextStepClocksLength)

            0x1A -> channel3.setNR30DacEnable(value)
            0x1B -> channel3.setNR31Length(value)
            0x1C -> channel3.setNR32OutputLevel(value)
            0x1D -> channel3.setNRx3PeriodLow(value)
            0x1E -> channel3.setNRx4PeriodHiControl(value, nextStepClocksLength)

            0x20 -> channel4.setNR41Length(value)
            0x21 -> channel4.setNRx2EnvelopeVolume(value)
            0x22 -> channel4.setNR43Frequency(value)
            0x23 -> channel4.setNR44Control(value, nextStepClocksLength)

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
        // FF15, FF1F and FF27..FF2F are unmapped and read back as 0xFF
        else -> 0xFF.toByte()
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

        if (!wasEnabled && apuEnabled) {
            // Powering up restarts the sequencer and realigns it to the divider grid
            frameSequencerStep = 0
            scheduleNextStep()
            resyncChannels()
        }
    }

    // todo check if further in-channel clean-up needed
    private fun resetAPU() {
        settleChannels()
        channel1.sweep(0)
        channel1.setNRx1LengthTimerDutyCycle(0)
        channel1.setNRx2EnvelopeVolume(0)
        channel1.setNRx3PeriodLow(0)
        channel1.setNRx4PeriodHiControl(0, false)
        channel1.disable()

        channel2.setNRx1LengthTimerDutyCycle(0)
        channel2.setNRx2EnvelopeVolume(0)
        channel2.setNRx3PeriodLow(0)
        channel2.setNRx4PeriodHiControl(0, false)
        channel2.disable()

        channel3.setNR30DacEnable(0)
        channel3.setNR31Length(0)
        channel3.setNR32OutputLevel(0)
        channel3.setNRx3PeriodLow(0)
        channel3.setNRx4PeriodHiControl(0, false)

        channel4.setNR41Length(0)
        channel4.setNRx2EnvelopeVolume(0)
        channel4.setNR43Frequency(0)
        channel4.setNR44Control(0, false)

        setNR50MasterVolume(0)
        setNR51Panning(0)
    }

    companion object {
        private const val FRAME_SEQUENCER_PERIOD = 8192L
        private const val SEQUENCER_BIT = 0x1000L
        private const val SAMPLE_PERIOD = 95
    }
}
