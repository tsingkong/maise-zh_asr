package com.danemadsen.maise.asr

import com.k2fsa.sherpa.onnx.*
import android.content.Context
import android.util.Log
import com.danemadsen.maise.copyAssetToFileReturnString
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.exp
import kotlin.math.PI

private const val TAG = "WhisperASR"

// Audio / STFT constants matching OpenAI Whisper
private const val WHISPER_SAMPLE_RATE = 16000
private const val N_FFT             = 400       // window length (25 ms at 16 kHz)
private const val HOP_LENGTH        = 160       // hop size (10 ms at 16 kHz)
private const val N_MELS            = 80        // mel filter count
private const val CHUNK_SECONDS     = 30
private const val N_SAMPLES         = WHISPER_SAMPLE_RATE * CHUNK_SECONDS   // 480,000
private const val N_FRAMES          = N_SAMPLES / HOP_LENGTH                 // 3,000
private const val F_MIN             = 0.0
private const val F_MAX             = 8000.0

// We zero-pad each N_FFT-sample window to the next power of 2 for a fast FFT.
// Frequency resolution differs slightly from Whisper's exact 400-pt FFT, but
// the mel filterbank is built to match these frequencies in Hz.
private const val FFT_SIZE          = 512       // next power-of-2 >= N_FFT
private const val N_FREQ_BINS       = FFT_SIZE / 2 + 1   // 257

// Encoder output dimensions for distil-small.en
private const val ENCODER_SEQ_LEN  = 1500
private const val ENCODER_HIDDEN   = 384

// Decoding limits
private const val MAX_NEW_TOKENS    = 448

/**
 * Core Whisper ASR engine for distil-small.en.
 *
 * Required assets (place in whisper/src/main/assets/):
 *   - model.int8.onnx
 *   - tokens.txt
 *
 * Usage:
 *   val asr = WhisperASR(context)
 *   val text = asr.transcribe(pcmSamples, inputSampleRate)
 *   asr.close()
 */
class WhisperASR(private val context: Context) {

    private var recognizer: OfflineRecognizer? = null
    // private val tokenizer: WhisperTokenizer

    fun init() {
        if (recognizer != null) return

        val cores = Runtime.getRuntime().availableProcessors()

        val modelFile = copyAssetToFileReturnString(context, "model.int8.onnx")
        val tokensFile = copyAssetToFileReturnString(context, "tokens.txt")

        // 2. 配置并创建识别器
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
            modelConfig = OfflineModelConfig(
                paraformer = OfflineParaformerModelConfig(
                    model = modelFile,
                ),
                tokens = tokensFile,
                numThreads = 2,
                debug = false,
                provider = "cpu",
            ),
            decodingMethod = "greedy_search",
        )

        recognizer = OfflineRecognizer(assetManager = null, config = config)

        // tokenizer      = WhisperTokenizer(context)
        Log.i(TAG, "WhisperASR initialized (cores=$cores)")
    }

    /**
     * Transcribe [audioSamples] (16-bit PCM, any sample rate) to text.
     * The audio is resampled linearly to 16 kHz and clamped to 30 seconds.
     *
     * [onPartial], if provided, is invoked with the accumulated transcript at each
     * word boundary as the decoder progresses, so callers can stream live text.
     *
     * Returns the transcribed text, or an empty string on failure.
     */
    fun transcribe(
        audioSamples: ShortArray,
        inputSampleRate: Int,
        onPartial: ((String) -> Unit)? = null
    ): String {
        init()
        val floatAudio = resampleToFloat(audioSamples, inputSampleRate)
        val rec = recognizer ?: return ""

        val stream = rec.createStream()
        stream.acceptWaveform(floatAudio, inputSampleRate)
        rec.decode(stream)

        val result = rec.getResult(stream)
        stream.release()
        return result.text
    }

    // -------------------------------------------------------------------------
    // Resampling
    // -------------------------------------------------------------------------

    private fun resampleToFloat(samples: ShortArray, srcRate: Int): FloatArray {
        // Convert to float [-1, 1]
        val floatSrc = FloatArray(samples.size) { i -> samples[i] / 32768f }

        if (srcRate == WHISPER_SAMPLE_RATE) return floatSrc

        // Linear interpolation resampling
        val ratio   = srcRate.toDouble() / WHISPER_SAMPLE_RATE
        val outLen  = (floatSrc.size / ratio).toInt()
        val out     = FloatArray(outLen)
        for (i in 0 until outLen) {
            val src = i * ratio
            val lo  = src.toInt().coerceIn(0, floatSrc.size - 1)
            val hi  = (lo + 1).coerceIn(0, floatSrc.size - 1)
            val frac = (src - lo).toFloat()
            out[i]  = floatSrc[lo] * (1f - frac) + floatSrc[hi] * frac
        }
        return out
    }

    fun close() {
        recognizer?.release()
        recognizer = null
    }
}
