package com.nearexpiry.manager.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.sin

/**
 * Generates short PCM beep tones via [AudioTrack] — far more reliable than
 * [android.media.ToneGenerator] which is silenced on many devices when the
 * notification stream is muted or the volume is low.
 *
 * Single beep  → new (unique) barcode
 * Double beep  → duplicate barcode
 */
@Singleton
class SoundManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    // PCM parameters
    private val sampleRate = 44_100
    private val beepFreq   = 1_800.0   // Hz – classic scanner tone
    private val beepMs     = 140       // ms per beep
    private val gapMs      = 120L      // ms gap between double-beep pulses

    /** Build a sine-wave PCM buffer with fade-in/out to avoid clicks. */
    private fun buildBeepBuffer(): ShortArray {
        val numSamples = sampleRate * beepMs / 1000
        val buf        = ShortArray(numSamples)
        val fadeLen    = minOf(numSamples / 10, 200)
        for (i in 0 until numSamples) {
            val angle  = 2.0 * PI * beepFreq * i / sampleRate
            var sample = sin(angle)
            if (i < fadeLen)                  sample *= i.toDouble() / fadeLen
            if (i > numSamples - fadeLen)     sample *= (numSamples - i).toDouble() / fadeLen
            buf[i] = (sample * Short.MAX_VALUE * 0.85).toInt().toShort()
        }
        return buf
    }

    private val beepBuffer: ShortArray by lazy { buildBeepBuffer() }

    /** Play one beep immediately on the IO dispatcher. */
    private fun playOnce() {
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val format = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val track = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(format)
                .setBufferSizeInBytes(maxOf(minBuf, beepBuffer.size * 2))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(beepBuffer, 0, beepBuffer.size)
            track.play()
            // Release after playback finishes
            scope.launch {
                delay(beepMs.toLong() + 60)
                runCatching { track.stop(); track.release() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Single short beep – used for new (unique) barcodes. */
    fun playSingleBeep() {
        scope.launch { playOnce() }
    }

    /** Double beep – used for duplicate barcodes. */
    fun playDoubleBeep() {
        scope.launch {
            playOnce()
            delay(beepMs.toLong() + gapMs)
            playOnce()
        }
    }

    /** Legacy alias. */
    fun playBeep() = playSingleBeep()

    fun release() { /* AudioTrack instances self-release after each play */ }
}
