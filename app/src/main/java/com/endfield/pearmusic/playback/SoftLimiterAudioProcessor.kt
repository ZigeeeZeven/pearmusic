package com.endfield.pearmusic.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.tanh

/**
 * A custom AudioProcessor that provides pre-amp attenuation and a soft-knee limiter.
 * This ensures enough headroom for system effects and prevents digital clipping
 * in high-dynamic-range floating-point PCM buffers.
 */
@UnstableApi
class SoftLimiterAudioProcessor : BaseAudioProcessor() {

    private var isEnabled = true
    private var isReplayGainEnabled = false
    private var trackGain = 1.0f
    
    // -3dB pre-amp attenuation (approx. 0.707) to provide headroom for effects/DSP
    private var preAmpGain = 0.70794576f 

    // Limiter parameters: start compressing at 0.9, limit strictly at 1.0
    private val threshold = 0.9f

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }

    fun setReplayGainEnabled(enabled: Boolean) {
        isReplayGainEnabled = enabled
    }

    fun setTrackGain(gain: Float) {
        trackGain = gain
    }

    /**
     * Set a custom pre-amp gain in decibels.
     * Example: -3.0f for 3dB attenuation.
     */
    fun setPreAmpDb(db: Float) {
        preAmpGain = Math.pow(10.0, (db / 20.0).toDouble()).toFloat()
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && 
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        // We maintain the input format to keep the pipeline high-fidelity (Float if provided)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val outputBuffer = replaceOutputBuffer(remaining)
        outputBuffer.order(ByteOrder.nativeOrder())
        inputBuffer.order(ByteOrder.nativeOrder())

        if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) {
            processFloat(inputBuffer, outputBuffer)
        } else {
            processShort(inputBuffer, outputBuffer)
        }
        
        outputBuffer.flip()
    }

    private fun processFloat(input: ByteBuffer, output: ByteBuffer) {
        while (input.remaining() >= 4) {
            var sample = input.getFloat()
            
            // 1. Apply Pre-Amp Attenuation first to create headroom
            sample *= preAmpGain
            
            // 2. Apply Replay Gain if available
            if (isReplayGainEnabled) {
                sample *= trackGain
            }

            // 3. Apply Soft Limiting to prevent clipping while preserving dynamics
            if (isEnabled) {
                sample = applySoftLimiter(sample)
            }
            
            // Clamp to legal float range before outputting to AudioTrack
            output.putFloat(sample.coerceIn(-1.0f, 1.0f))
        }
    }

    private fun processShort(input: ByteBuffer, output: ByteBuffer) {
        while (input.remaining() >= 2) {
            val rawSample = input.getShort().toFloat() / 32768f
            var sample = rawSample
            
            // 1. Apply Pre-Amp Attenuation
            sample *= preAmpGain
            
            // 2. Apply Replay Gain
            if (isReplayGainEnabled) {
                sample *= trackGain
            }

            // 3. Apply Soft Limiting
            if (isEnabled) {
                sample = applySoftLimiter(sample)
            }
            
            // Convert back to 16-bit PCM
            val out = (sample * 32767f).coerceIn(-32768f, 32767f).toInt().toShort()
            output.putShort(out)
        }
    }

    /**
     * A smooth soft-knee limiter using a hyperbolic tangent function.
     * Gradually compresses peaks above the threshold to approach 1.0f asymptotically.
     */
    private fun applySoftLimiter(x: Float): Float {
        val absX = abs(x)
        if (absX <= threshold) return x
        
        // Threshold=0.9, Limit=1.0. 
        val headroom = 1.0f - threshold
        val excess = absX - threshold
        val compressed = threshold + headroom * tanh(excess / headroom)
        return if (x > 0) compressed else -compressed
    }
}
