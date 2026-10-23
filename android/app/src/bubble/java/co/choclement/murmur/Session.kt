package co.choclement.murmur

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import kotlin.concurrent.thread
import kotlin.math.sqrt

/**
 * One dictation. Streams the mic, cuts it into chunks at natural pauses, and hands each
 * chunk to Whisper while you're still talking, so only the last bit is left when you let go.
 *
 * [source] supplies 16 kHz mono audio; by default it's the microphone.
 */
class Session(
    private val context: Context,
    private val onLevel: (Float) -> Unit,
    private val source: AudioSource = MicSource(),
) {
    private val main = Handler(Looper.getMainLooper())
    private val parts = ConcurrentHashMap<Int, Future<String>>()
    private var audio = FloatArray(Transcriber.RATE * 30) // everything recorded so far
    @Volatile private var running = false
    @Volatile private var cancelled = false
    private var chunks = 0
    private var lastEnd = 0
    private var totalSamples = 0
    private var recorder: Thread? = null
    private val startedAt = System.currentTimeMillis()

    val seconds get() = (System.currentTimeMillis() - startedAt) / 1000f

    fun start(): Boolean {
        if (!source.start()) return false
        Cloud.warmUp()
        running = true
        recorder = thread(name = "murmur-mic") { record() }
        return true
    }

    private fun record() {
        val vad = Transcriber.newVad(context)
        val buf = FloatArray(512) // 32 ms
        fun take(n: Int) {
            val chunk = buf.copyOf(n)
            if (totalSamples + n > audio.size) audio = audio.copyOf(audio.size * 2)
            chunk.copyInto(audio, totalSamples)
            totalSamples += n
            var sum = 0f
            for (x in chunk) sum += x * x
            val level = (sqrt(sum / n) * 9f).coerceIn(0f, 1f)
            main.post { onLevel(level) }
            vad.acceptWaveform(chunk)
            drain(vad)
        }
        while (running) {
            val n = source.read(buf)
            if (n < 0) break
            if (n > 0) take(n)
        }
        // Don't lose the last words still sitting in the mic buffer when you let go.
        while (true) {
            val n = source.readRemaining(buf)
            if (n <= 0) break
            take(n)
        }
        source.stop()
        vad.flush()
        drain(vad)
        vad.release()
    }

    private fun drain(vad: com.k2fsa.sherpa.onnx.Vad) {
        while (!vad.empty()) {
            val segment = vad.front()
            vad.pop()
            // The detector reacts a moment late (and needs a moment to warm up), so the first
            // chunk always starts at the very beginning and later ones borrow up to 0.3 s of the
            // pause before them. Either way no word gets clipped.
            val from = if (chunks == 0) 0 else maxOf(lastEnd, segment.start - Transcriber.RATE * 3 / 10)
            val to = minOf(totalSamples, segment.start + segment.samples.size)
            lastEnd = to
            submit(if (to > from) audio.copyOfRange(from, to) else segment.samples)
        }
    }

    /** Each chunk goes off for its final text as soon as it's cut (cloud chunks run side by side). */
    private fun submit(samples: FloatArray) {
        val index = chunks++
        if (!cancelled) parts[index] = Transcriber.decodeFinal(samples, context)
    }

    /** Stops listening and delivers the full transcript on the main thread. */
    fun finish(onText: (String) -> Unit) {
        running = false
        thread(name = "murmur-finish") {
            recorder?.join()
            // The detector found no speech at all (very quiet voice?): transcribe everything once.
            if (chunks == 0 && totalSamples > Transcriber.RATE / 2) {
                submit(audio.copyOf(totalSamples))
            }
            val texts = (0 until chunks).mapNotNull { runCatching { parts[it]?.get() }.getOrNull() }
            val text = join(texts.filter { it.isNotBlank() })
            if (!cancelled) main.post { onText(text) }
        }
    }

    fun cancel() {
        cancelled = true
        running = false
    }

    /** Joins chunks; a chunk that starts a new sentence gets its capital letter back. */
    private fun join(texts: List<String>): String {
        val out = StringBuilder()
        for (t in texts) {
            val startsSentence = out.isEmpty() || out.trimEnd().lastOrNull() in listOf('.', '!', '?')
            val piece = if (startsSentence) t.replaceFirstChar { it.uppercaseChar() } else t
            if (out.isNotEmpty()) out.append(' ')
            out.append(piece)
        }
        return out.toString()
    }

    /** The phone's microphone at 16 kHz. */
}
