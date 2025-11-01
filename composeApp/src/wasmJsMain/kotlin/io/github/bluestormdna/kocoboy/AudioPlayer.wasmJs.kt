@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.bluestormdna.kocoboy

import org.khronos.webgl.Float32Array
import org.khronos.webgl.set


actual fun platformAudioPlayer(): AudioPlayer = WebAudioPlayer()

class WebAudioPlayer : AudioPlayer {
    private val audioContext = AudioContext()
    private val audioBuffer = audioContext.createBuffer(2, 4096, 44100)

    override fun play(sampleBuffer: ByteArray) {
        val channelL = audioBuffer.getChannelData(0)
        val channelR = audioBuffer.getChannelData(1)

        val (l, r) = sampleBuffer.toFloat32Array()
        channelL.set(l)
        channelR.set(r)

        val source = audioContext.createBufferSource()
        source.buffer = audioBuffer
        source.connect(audioContext.destination)
        source.start(time = 0.0)
    }
}

external class AudioContext {
    val destination: AudioNode
    fun createBuffer(
        channel: Int = definedExternally,
        size: Int = definedExternally,
        sampleRate: Int = definedExternally
    ): AudioBuffer

    fun createBufferSource(): AudioBufferSourceNode
}

open external class AudioNode {
    fun connect(
        destination: AudioNode,
        output: Int = definedExternally,
        input: Int = definedExternally
    ): AudioNode
}

external class AudioBuffer : JsAny {
    fun getChannelData(channel: Int = definedExternally): Float32Array
}

external class AudioBufferSourceNode : AudioNode {
    fun start(time: Double = definedExternally)
    var buffer: AudioBuffer
}

fun ByteArray.toFloat32Array(): Pair<Float32Array, Float32Array> {
    val l = Float32Array(this.size / 2)
    val r = Float32Array(this.size / 2)
    for (i in 0..<this.size / 2) {
        l[i] = ((this[i * 2].toInt() and 0xFF) - 128) / 255.0f
        r[i] = ((this[i * 2 + 1].toInt() and 0xFF) - 128) / 255.0f
    }
    return l to r
}

