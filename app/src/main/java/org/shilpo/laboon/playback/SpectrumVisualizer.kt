package org.shilpo.laboon.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class SpectrumFrame(
    val bass: Float = 0f,
    val mid: Float = 0f,
    val treble: Float = 0f,
    val amplitude: Float = 0f,
    val beatPulse: Float = 0f,
)

@OptIn(UnstableApi::class)
class SpectrumVisualizer {

    private val _state = MutableStateFlow(SpectrumFrame())
    val state: StateFlow<SpectrumFrame> = _state.asStateFlow()

    private var sampleRate: Int = 44100
    private var channels: Int = 2
    private var encoding: Int = C.ENCODING_PCM_16BIT

    private var smoothedBass = 0f
    private var smoothedMid = 0f
    private var smoothedTreble = 0f
    private var smoothedAmp = 0f
    private var runningBassAvg = 0.1f
    private var beatPulseVal = 0f

    private var lastEmitTimeMs = 0L

    val sink = object : TeeAudioProcessor.AudioBufferSink {
        override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
            this@SpectrumVisualizer.sampleRate = if (sampleRateHz > 0) sampleRateHz else 44100
            this@SpectrumVisualizer.channels = if (channelCount > 0) channelCount else 2
            this@SpectrumVisualizer.encoding = encoding
        }

        override fun handleBuffer(buffer: ByteBuffer) {
            processBuffer(buffer)
        }
    }

    fun reset() {
        smoothedBass = 0f
        smoothedMid = 0f
        smoothedTreble = 0f
        smoothedAmp = 0f
        runningBassAvg = 0.1f
        beatPulseVal = 0f
        _state.value = SpectrumFrame()
    }

    private fun processBuffer(buffer: ByteBuffer) {
        val remaining = buffer.remaining()
        if (remaining == 0) return

        val orderBuffer = buffer.asReadOnlyBuffer().order(ByteOrder.nativeOrder())
        val isFloat = encoding == C.ENCODING_PCM_FLOAT

        val bytesPerSample = if (isFloat) 4 else 2
        val ch = max(1, channels)
        val frameBytes = bytesPerSample * ch
        val totalFrames = remaining / frameBytes
        if (totalFrames <= 0) return

        val framesToProcess = min(totalFrames, 384)
        val sampleChannels = min(ch, 6)

        var bassAcc = 0f
        var midAcc = 0f
        var trebleAcc = 0f
        var ampAcc = 0f

        var lpState = 0f
        var prevSample = 0f
        val lpAlpha = 0.035f

        for (i in 0 until framesToProcess) {
            val byteOffset = i * frameBytes
            if (byteOffset + frameBytes > remaining) break

            var frameSum = 0f
            for (c in 0 until sampleChannels) {
                val chanOffset = byteOffset + c * bytesPerSample
                if (chanOffset + bytesPerSample <= remaining) {
                    val s: Float = if (isFloat) {
                        orderBuffer.getFloat(orderBuffer.position() + chanOffset)
                    } else {
                        val rawShort = orderBuffer.getShort(orderBuffer.position() + chanOffset)
                        rawShort / 32768.0f
                    }
                    frameSum += s
                }
            }
            val sample = frameSum / sampleChannels.toFloat()

            val absSample = if (sample < 0f) -sample else sample
            ampAcc += absSample * absSample

            lpState += lpAlpha * (sample - lpState)
            val bassVal = lpState
            bassAcc += bassVal * bassVal

            val hpVal = sample - prevSample
            trebleAcc += hpVal * hpVal

            val midVal = sample - lpState
            midAcc += midVal * midVal

            prevSample = sample
        }

        val count = max(1, framesToProcess)
        val rawAmp = (sqrt(ampAcc / count) * 3.2f).coerceIn(0f, 1f)
        val rawBass = (sqrt(bassAcc / count) * 4.8f).coerceIn(0f, 1f)
        val rawMid = (sqrt(midAcc / count) * 3.5f).coerceIn(0f, 1f)
        val rawTreble = (sqrt(trebleAcc / count) * 3.0f).coerceIn(0f, 1f)

        smoothedAmp = smoothValue(smoothedAmp, rawAmp, 0.60f, 0.15f)
        smoothedBass = smoothValue(smoothedBass, rawBass, 0.65f, 0.12f)
        smoothedMid = smoothValue(smoothedMid, rawMid, 0.50f, 0.15f)
        smoothedTreble = smoothValue(smoothedTreble, rawTreble, 0.50f, 0.18f)

        runningBassAvg = runningBassAvg * 0.95f + rawBass * 0.05f
        if (rawBass > runningBassAvg * 1.35f && rawBass > 0.18f) {
            val instantPulse = ((rawBass - runningBassAvg) * 2.2f).coerceIn(0.4f, 1.0f)
            beatPulseVal = max(beatPulseVal, instantPulse)
        } else {
            beatPulseVal = (beatPulseVal * 0.85f).coerceAtLeast(0f)
        }

        val now = System.currentTimeMillis()
        if (now - lastEmitTimeMs >= 16L) {
            lastEmitTimeMs = now
            _state.value = SpectrumFrame(
                bass = smoothedBass,
                mid = smoothedMid,
                treble = smoothedTreble,
                amplitude = smoothedAmp,
                beatPulse = beatPulseVal,
            )
        }
    }

    private fun smoothValue(current: Float, target: Float, attack: Float, decay: Float): Float {
        return if (target > current) {
            current + (target - current) * attack
        } else {
            current - (current - target) * decay
        }.coerceIn(0f, 1f)
    }
}
