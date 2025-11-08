package io.github.bluestormdna.kocoboy.host

interface Host {
    fun render(frameBuffer: IntArray)
    fun play(sampleBuffer: ByteArray)
}
