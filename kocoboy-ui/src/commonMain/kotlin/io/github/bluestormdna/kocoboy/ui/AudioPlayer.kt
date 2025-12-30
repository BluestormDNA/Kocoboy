package io.github.bluestormdna.kocoboy.ui

interface AudioPlayer {
    fun play(sampleBuffer: ByteArray)
}

expect fun platformAudioPlayer(): AudioPlayer
