package io.github.bluestormdna.kocoboy

interface AudioPlayer {
    fun play(sampleBuffer: ByteArray)
}

expect fun platformAudioPlayer(): AudioPlayer