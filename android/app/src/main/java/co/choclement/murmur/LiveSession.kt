package co.choclement.murmur

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.math.sqrt

/**
 * Voice typing with live text, for the Murmur keyboard.
 *
 * Like [Session], the recording is cut into phrases at natural pauses and each finished phrase
 * gets a proper transcription ([Listener.onFinal]). On top of that, the phrase you're still in the
 * middle of is re-transcribed every ~0.6 s ([Listener.onPartial]), so words appear as you speak.
 * Phrases are numbered; a partial always belongs to the phrase that's still open.
 */
class LiveSession(
    private val context: Context,
    private val listener: Listener,
    private val source: Session.AudioSource = Session.MicSource(),
    /** Phrase numbers start here, so a new session never collides with one still finishing. */
    private val firstPhrase: Int = 0,
) {
    interface Listener {
        fun onLevel(level: Float)
        fun onPartial(phrase: Int, text: String)
        fun onFinal(phrase: Int, text: String)
    }

    companion object {
        /** Test hook (debug builds): the next session plays this audio instead of using the mic. */
        @Volatile var testAudio: FloatArray? = null

        private const val PARTIAL_EVERY = Transcriber.RATE * 6 / 10  // 0.6 s of new audio
        private const val PARTIAL_MAX = Transcriber.RATE * 12        // never re-read more than 12 s
        private val pending = AtomicInteger()                       // jobs waiting on the engine
    }

    private val main = Handler(Looper.getMainLooper())
    private var audio = FloatArray(Transcriber.RATE * 30)
    private var total = 0
    private var lastEnd = 0
    @Volatile private var phrases = firstPhrase // number of the open phrase; earlier ones are finished
    private var lastPartialAt = 0
    @Volatile private var running = false
    @Volatile private var cancelled = false
    private var recorder: Thread? = null

    fun start(): Boolean {
        if (!source.start()) return false
        running = true
        recorder = thread(name = "murmur-live") { record() }
        return true
    }

    /** Stops listening, finishes every phrase, then calls [onDone] on the main thread. */
    fun stop(onDone: () -> Unit) {
        running = false
        thread(name = "murmur-live-stop") {
            recorder?.join()
            // Unlike the bubble, there's no "transcribe everything" fallback here: voice mode starts
            // listening on every tap, and Whisper invents words from silence.
            Transcriber.worker.execute { if (!cancelled) main.post(onDone) }
        }
    }

    /** Stops immediately; whatever is still being transcribed is dropped. */
    fun cancel() {
        cancelled = true
        running = false
    }

    private fun record() {
        val vad = Transcriber.newVad(context)
        val buf = FloatArray(512)
        fun take(n: Int) {
            if (total + n > audio.size) audio = audio.copyOf(audio.size * 2)
            buf.copyInto(audio, total, 0, n)
            total += n
            var sum = 0f
            for (i in 0 until n) sum += buf[i] * buf[i]
            val level = (sqrt(sum / n) * 9f).coerceIn(0f, 1f)
            main.post { listener.onLevel(level) }
            vad.acceptWaveform(buf.copyOf(n))
            while (!vad.empty()) {
                val seg = vad.front()
                vad.pop()
                // First phrase starts at the very beginning (the detector reacts late);
                // later ones borrow up to 0.3 s of the pause before them.
                val from = if (phrases == firstPhrase) 0 else maxOf(lastEnd, seg.start - Transcriber.RATE * 3 / 10)
                val to = minOf(total, seg.start + seg.samples.size)
                finalize(from, to)
            }
            maybePartial(vad.isSpeechDetected())
        }
        while (running) {
            val n = source.read(buf)
            if (n < 0) break
            if (n > 0) take(n)
        }
        while (true) {
            val n = source.readRemaining(buf)
            if (n <= 0) break
            take(n)
        }
        source.stop()
        vad.flush()
        while (!vad.empty()) {
            val seg = vad.front()
            vad.pop()
            val from = if (phrases == firstPhrase) 0 else maxOf(lastEnd, seg.start - Transcriber.RATE * 3 / 10)
            finalize(from, minOf(total, seg.start + seg.samples.size))
        }
        vad.release()
    }

    private fun finalize(from: Int, to: Int) {
        val phrase = phrases++
        lastEnd = to
        lastPartialAt = to
        val samples = if (to > from) audio.copyOfRange(from, to) else return
        pending.incrementAndGet()
        Transcriber.worker.execute {
            val text = if (cancelled) "" else Transcriber.decodeNow(samples, context)
            pending.decrementAndGet()
            if (!cancelled) main.post { listener.onFinal(phrase, text) }
        }
    }

    /** While you're mid-phrase, re-transcribe it so far, if the engine isn't busy. */
    private fun maybePartial(speaking: Boolean) {
        if (!speaking || total - lastPartialAt < PARTIAL_EVERY || pending.get() > 0) return
        lastPartialAt = total
        val from = maxOf(if (phrases == firstPhrase) 0 else lastEnd, total - PARTIAL_MAX)
        val samples = audio.copyOfRange(from, total)
        val phrase = phrases
        pending.incrementAndGet()
        Transcriber.worker.execute {
            // Skip if this phrase was finished meanwhile: its final text is on the way.
            val text = if (cancelled || phrase < phrases) "" else Transcriber.decodeNow(samples, context)
            pending.decrementAndGet()
            if (!cancelled && text.isNotBlank()) main.post { listener.onPartial(phrase, text) }
        }
    }
}
