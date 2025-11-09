package io.github.bluestormdna.kocoboy

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine

actual fun platformAudioPlayer(): AudioPlayer = JvmAudioPlayer()

class JvmAudioPlayer : AudioPlayer {

    private val format = AudioFormat(
        44100f, // Sample Rate Hz
        8, // 8-bit audio
        2, // Stereo
        false, // Signed
        false, // Little-endian
    )

    private val info = DataLine.Info(SourceDataLine::class.java, format)
    private var sourceLine: SourceDataLine = AudioSystem.getLine(info) as SourceDataLine

    init {
        sourceLine.open(format)
        sourceLine.start()
    }

    override fun play(sampleBuffer: ByteArray) {
        sourceLine.write(sampleBuffer, 0, sampleBuffer.size)
    }

    fun close() {
        sourceLine.drain()
        sourceLine.close()
    }
}
