package co.choclement.murmur

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.util.concurrent.Executors

/**
 * The on-device Whisper engine (sherpa-onnx). Everything that touches the model runs on
 * [worker], one job at a time, so chunks are transcribed in the order they were spoken.
 */
object Transcriber {
    const val RATE = 16000

    val worker = Executors.newSingleThreadExecutor { Thread(it, "whisper").apply { priority = Thread.MAX_PRIORITY } }

    @Volatile var status = "Loading…"
        private set

    private var recognizer: OfflineRecognizer? = null
    private var loadedKey = ""

    /** Loads (or reloads) the model for the current settings. Safe to call often. */
    fun load(context: Context) = worker.execute { ensureLoaded(context.applicationContext) }

    private fun ensureLoaded(context: Context) {
        val id = Prefs.model.takeIf { Models.isReady(context, it) } ?: Models.BUILT_IN
        val key = "$id|${Prefs.language}"
        if (key == loadedKey && recognizer != null) return

        status = "Loading ${Models.byId(id).label.substringBefore(" ·")} model…"
        recognizer?.release()
        recognizer = null
        val (encoder, decoder, tokens) = Models.paths(context, id)
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = RATE, featureDim = 80),
            modelConfig = OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = encoder,
                    decoder = decoder,
                    language = Prefs.language,
                    task = "transcribe",
                ),
                tokens = tokens,
                numThreads = 4,
                modelType = "whisper",
            ),
            decodingMethod = "greedy_search",
        )
        recognizer = runCatching {
            OfflineRecognizer(if (id == Models.BUILT_IN) context.assets else null, config)
        }.onFailure { status = "Couldn't load model: ${it.message}" }.getOrNull() ?: return
        loadedKey = key
        // Warm up so the first real dictation isn't slower than the rest.
        decodeNow(FloatArray(RATE / 2))
        status = "Ready · ${Models.byId(id).label.substringBefore(" ·")} model"
    }

    /** Transcribes one chunk. Call only on [worker]. */
    fun decodeNow(samples: FloatArray, context: Context? = null): String {
        if (context != null) ensureLoaded(context)
        val r = recognizer ?: return ""
        val stream = r.createStream()
        stream.acceptWaveform(samples, RATE)
        r.decode(stream)
        val text = r.getResult(stream).text.trim()
        stream.release()
        return clean(text)
    }

    /** Whisper sometimes "transcribes" noise as [BLANK_AUDIO], (music) and the like. */
    private fun clean(text: String): String {
        val t = text.replace(Regex("""\[[^\]]*]"""), "").trim()
        val withoutAsides = t.replace(Regex("""\([^)]*\)"""), "")
        return if (withoutAsides.none { it.isLetterOrDigit() }) "" else t
    }

    /** Voice-activity detector used to cut the recording at pauses. Use on one thread only. */
    fun newVad(context: Context) = Vad(
        context.assets,
        VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = "models/silero_vad.onnx",
                threshold = 0.45f,
                minSilenceDuration = 0.5f,   // a half-second pause ends a chunk (same as the Mac app)
                minSpeechDuration = 0.2f,
                windowSize = 512,
                maxSpeechDuration = 12f,     // force a cut after 12 s of nonstop talk
            ),
            sampleRate = RATE,
            numThreads = 1,
        ),
    )
}
