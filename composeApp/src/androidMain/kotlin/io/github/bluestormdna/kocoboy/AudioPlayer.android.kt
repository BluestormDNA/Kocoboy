package io.github.bluestormdna.kocoboy

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.AudioTrack.PLAYSTATE_PLAYING

actual fun platformAudioPlayer(): AudioPlayer = AndroidAudioPlayer()

class AndroidAudioPlayer : AudioPlayer {

    private val sampleRate = 44100
    private val channelMask = AudioFormat.CHANNEL_OUT_STEREO
    private val encoding = AudioFormat.ENCODING_PCM_8BIT

    private val audioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(encoding)
                .setSampleRate(sampleRate)
                .setChannelMask(channelMask)
                .build(),
        )
        .setBufferSizeInBytes(
            AudioTrack.getMinBufferSize(sampleRate, channelMask, encoding),
        )
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()

    override fun play(sampleBuffer: ByteArray) {
        audioTrack.write(sampleBuffer, 0, sampleBuffer.size, AudioTrack.WRITE_NON_BLOCKING)
        if (audioTrack.playState != PLAYSTATE_PLAYING) {
            audioTrack.play()
        }
    }
}
