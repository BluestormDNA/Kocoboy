package io.github.bluestormdna.kocoboy

actual fun platformAudioPlayer(): AudioPlayer = IOSAudioPlayer()

class IOSAudioPlayer: AudioPlayer {
    override fun play(sampleBuffer: ByteArray) {
        // TODO("Not yet implemented")
    }
}