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
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class SpectrumFrame(
    val bass: Float = 0f,
    val mid: Float = 0f,
    val treble: Float = 0f,
    val amplitude: Float = 0f,
    val beatPulse: Float = 0f,
    val bands: FloatArray = FloatArray(30),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SpectrumFrame
        if (bass != other.bass) return false
        if (mid != other.mid) return false
        if (treble != other.treble) return false
        if (amplitude != other.amplitude) return false
        if (beatPulse != other.beatPulse) return false
        if (!bands.contentEquals(other.bands)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = bass.hashCode()
        result = 31 * result + mid.hashCode()
        result = 31 * result + treble.hashCode()
        result = 31 * result + amplitude.hashCode()
        result = 31 * result + beatPulse.hashCode()
        result = 31 * result + bands.contentHashCode()
        return result
    }
}

private class FastFourierTransform(val n: Int = 512) {
    private val cosTable = FloatArray(n / 2)
    private val sinTable = FloatArray(n / 2)
    private val bitReverse = IntArray(n)
    val hannWindow = FloatArray(n)

    init {
        val pi2 = (2.0 * Math.PI).toFloat()
        for (i in 0 until n / 2) {
            cosTable[i] = cos(pi2 * i / n)
            sinTable[i] = sin(pi2 * i / n)
        }
        for (i in 0 until n) {
            hannWindow[i] = (0.5 * (1.0 - cos(pi2 * i / (n - 1)))).toFloat()
        }
        val shift = 1 + Integer.numberOfLeadingZeros(n)
        for (i in 0 until n) {
            bitReverse[i] = Integer.reverse(i) ushr shift
        }
    }

    fun computeMagnitudes(real: FloatArray, imag: FloatArray, magnitudes: FloatArray) {
        for (i in 0 until n) {
            val j = bitReverse[i]
            if (j > i) {
                val tempR = real[i]
                real[i] = real[j]
                real[j] = tempR
                val tempI = imag[i]
                imag[i] = imag[j]
                imag[j] = tempI
            }
        }

        var size = 2
        while (size <= n) {
            val halfSize = size / 2
            val step = n / size
            var k = 0
            while (k < n) {
                var j = 0
                while (j < halfSize) {
                    val tableIdx = j * step
                    val c = cosTable[tableIdx]
                    val s = sinTable[tableIdx]
                    val oddIdx = k + j + halfSize
                    val evenIdx = k + j
                    val tr = real[oddIdx] * c + imag[oddIdx] * s
                    val ti = imag[oddIdx] * c - real[oddIdx] * s
                    real[oddIdx] = real[evenIdx] - tr
                    imag[oddIdx] = imag[evenIdx] - ti
                    real[evenIdx] += tr
                    imag[evenIdx] += ti
                    j++
                }
                k += size
            }
            size *= 2
        }

        val halfN = n / 2
        for (i in 0 until halfN) {
            magnitudes[i] = sqrt(real[i] * real[i] + imag[i] * imag[i])
        }
    }
}

@OptIn(UnstableApi::class)
class SpectrumVisualizer {

    private val _state = MutableStateFlow(SpectrumFrame())
    val state: StateFlow<SpectrumFrame> = _state.asStateFlow()

    private var channels: Int = 2
    private var encoding: Int = C.ENCODING_PCM_16BIT

    private val fftSize = 512
    private val fft = FastFourierTransform(fftSize)
    private val realBuffer = FloatArray(fftSize)
    private val imagBuffer = FloatArray(fftSize)
    private val magBuffer = FloatArray(fftSize / 2)

    private val rawBands = FloatArray(30)
    private val smoothedBands = FloatArray(30)

    private var smoothedBass = 0f
    private var smoothedMid = 0f
    private var smoothedTreble = 0f
    private var smoothedAmp = 0f
    private var runningBassAvg = 0.12f
    private var beatPulseVal = 0f

    private var normalizationCeiling = 0.45f
    private val normalizationFloor = 0.08f
    private var lastEmitTimeMs = 0L

    private val bandBinRanges = arrayOf(
        1..1, 2..2, 3..3, 4..4, 5..7,
        8..11, 12..17, 18..25, 26..37, 38..53,
        54..75, 76..105, 106..146, 147..193, 194..255,
    )

    val sink = object : TeeAudioProcessor.AudioBufferSink {
        override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
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
        runningBassAvg = 0.12f
        beatPulseVal = 0f
        normalizationCeiling = 0.45f
        for (i in smoothedBands.indices) {
            smoothedBands[i] = 0f
            rawBands[i] = 0f
        }
        lastEmitTimeMs = 0L
        _state.value = SpectrumFrame()
    }

    private fun processBuffer(buffer: ByteBuffer) {
        val remaining = buffer.remaining()
        if (remaining == 0) return

        val orderBuffer = buffer.asReadOnlyBuffer().order(ByteOrder.nativeOrder())
        val bytesPerSample = when (encoding) {
            C.ENCODING_PCM_8BIT -> 1
            C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 2
            C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> 3
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN, C.ENCODING_PCM_FLOAT -> 4
            else -> return
        }
        if (encoding == C.ENCODING_PCM_16BIT_BIG_ENDIAN ||
            encoding == C.ENCODING_PCM_24BIT_BIG_ENDIAN ||
            encoding == C.ENCODING_PCM_32BIT_BIG_ENDIAN
        ) {
            orderBuffer.order(ByteOrder.BIG_ENDIAN)
        }
        val ch = max(1, channels)
        val frameBytes = bytesPerSample * ch
        val totalFrames = remaining / frameBytes
        if (totalFrames <= 0) return

        val framesToTake = min(totalFrames, fftSize)
        val startFrame = totalFrames - framesToTake
        val bufPos = orderBuffer.position()
        var sumSq = 0f
        rawBands.fill(0f)

        for (channel in 0 until 2) {
            val inputChannel = min(channel, ch - 1)
            for (i in 0 until fftSize) {
                val sample = if (i < framesToTake) {
                    val byteOffset =
                        bufPos + (startFrame + i) * frameBytes + inputChannel * bytesPerSample
                    readSample(orderBuffer, byteOffset)
                } else {
                    0f
                }
                sumSq += sample * sample
                realBuffer[i] = sample * fft.hannWindow[i]
                imagBuffer[i] = 0f
            }
            fft.computeMagnitudes(realBuffer, imagBuffer, magBuffer)

            for (b in bandBinRanges.indices) {
                val range = bandBinRanges[b]
                var sum = 0f
                for (bin in range) sum += magBuffer[bin]
                val level = sum / range.count() * (1f + b * 0.16f)
                val outputIndex = if (channel == 0) 14 - b else 15 + b
                rawBands[outputIndex] = level
            }
        }

        val peakBand = rawBands.maxOrNull() ?: 0f
        val targetCeiling = max(normalizationFloor, peakBand * 1.15f)
        if (targetCeiling >= normalizationCeiling) {
            normalizationCeiling = targetCeiling
        } else {
            normalizationCeiling = max(normalizationFloor, normalizationCeiling * 0.995f)
        }

        for (b in smoothedBands.indices) {
            val normalized = (rawBands[b] / normalizationCeiling).coerceIn(0f, 1f)
            smoothedBands[b] = smoothValue(smoothedBands[b], normalized, 0.70f, 0.18f)
        }

        smoothedBass = averageFrequencyBands(0..3)
        smoothedMid = averageFrequencyBands(4..9)
        smoothedTreble = averageFrequencyBands(10..14)

        val rawRms = sqrt(sumSq / max(1, framesToTake * 2)) * 2.8f
        smoothedAmp = smoothValue(smoothedAmp, rawRms.coerceIn(0f, 1f), 0.65f, 0.15f)

        runningBassAvg = runningBassAvg * 0.94f + smoothedBass * 0.06f
        if (smoothedBass > runningBassAvg * 1.25f && smoothedBass > 0.16f) {
            val instantPulse = ((smoothedBass - runningBassAvg) * 3.2f).coerceIn(0.35f, 1.0f)
            beatPulseVal = max(beatPulseVal, instantPulse)
        } else {
            beatPulseVal = (beatPulseVal * 0.82f).coerceAtLeast(0f)
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
                bands = smoothedBands.copyOf(),
            )
        }
    }

    private fun averageFrequencyBands(range: IntRange): Float {
        var sum = 0f
        for (band in range) sum += smoothedBands[14 - band] + smoothedBands[15 + band]
        return sum / (range.count() * 2)
    }

    private fun readSample(buffer: ByteBuffer, offset: Int): Float = when (encoding) {
        C.ENCODING_PCM_FLOAT -> buffer.getFloat(offset).let { if (it.isFinite()) it else 0f }
        C.ENCODING_PCM_8BIT -> ((buffer.get(offset).toInt() and 0xff) - 128) / 128f
        C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> buffer.getShort(offset) / 32768f
        C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> {
            val bigEndian = encoding == C.ENCODING_PCM_24BIT_BIG_ENDIAN
            val low = buffer.get(offset + if (bigEndian) 2 else 0).toInt() and 0xff
            val middle = buffer.get(offset + 1).toInt() and 0xff
            val high = buffer.get(offset + if (bigEndian) 0 else 2).toInt()
            ((high shl 16) or (middle shl 8) or low) / 8388608f
        }

        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN -> buffer.getInt(offset) / 2147483648f
        else -> 0f
    }

    private fun smoothValue(current: Float, target: Float, attack: Float, decay: Float): Float {
        return if (target > current) {
            current + (target - current) * attack
        } else {
            current - (current - target) * decay
        }.coerceIn(0f, 1f)
    }
}
