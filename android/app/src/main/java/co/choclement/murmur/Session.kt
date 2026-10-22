package co.choclement.murmur

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ConcurrentHashMap
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
    interface AudioSource {
        fun start(): Boolean
        /** Fills [buf], returns how many samples were read, or -1 when finished. */
        fun read(buf: FloatArray): Int
        /** After you let go: whatever audio is still buffered, without waiting. 0 or -1 when empty. */
        fun readRemaining(buf: FloatArray): Int
        fun stop()
    }

    private val main = Handler(Looper.getMainLooper())
    private val parts = ConcurrentHashMap<Int, String>()
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

    private fun submit(samples: FloatArray) {
        val index = chunks++
        Transcriber.worker.execute {
            if (!cancelled) parts[index] = Transcriber.decodeNow(samples, context)
        }
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
            val count = chunks
            // The worker runs jobs in order, so this runs after every chunk is done.
            Transcriber.worker.execute {
                val text = join((0 until count).mapNotNull { parts[it] }.filter { it.isNotBlank() })
                if (!cancelled) main.post { onText(text) }
            }
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
    class MicSource : AudioSource {
        private var record: AudioRecord? = null

        @SuppressLint("MissingPermission")
        override fun start(): Boolean {
            val min = AudioRecord.getMinBufferSize(Transcriber.RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
            val r = runCatching {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION, Transcriber.RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT,
                    maxOf(min, Transcriber.RATE), // ~0.25 s of buffer
                )
            }.getOrNull() ?: return false
            if (r.state != AudioRecord.STATE_INITIALIZED) { r.release(); return false }
            r.startRecording()
            record = r
            return true
        }

        override fun read(buf: FloatArray) = record?.read(buf, 0, buf.size, AudioRecord.READ_BLOCKING) ?: -1

        override fun readRemaining(buf: FloatArray) = record?.read(buf, 0, buf.size, AudioRecord.READ_NON_BLOCKING) ?: -1

        override fun stop() {
            record?.run { runCatching { stop() }; release() }
            record = null
        }
    }

    /** Plays a 16 kHz WAV file at real-time speed, as if it were spoken (used for testing). */
    class WavSource(private val samples: FloatArray) : AudioSource {
        private var at = 0
        override fun start() = true
        override fun read(buf: FloatArray): Int {
            if (at >= samples.size) return -1
            val n = minOf(buf.size, samples.size - at)
            samples.copyInto(buf, 0, at, at + n)
            at += n
            Thread.sleep(n * 1000L / Transcriber.RATE)
            return n
        }
        override fun readRemaining(buf: FloatArray): Int {
            if (at >= samples.size) return -1
            val n = minOf(buf.size, samples.size - at)
            samples.copyInto(buf, 0, at, at + n)
            at += n
            return n
        }
        override fun stop() {}

        companion object {
            /** Reads a 16-bit mono 16 kHz PCM WAV. */
            fun load(path: String): FloatArray {
                val bytes = java.io.File(path).readBytes()
                var p = 12
                while (p + 8 <= bytes.size) {
                    val id = String(bytes, p, 4)
                    val size = (bytes[p + 4].toInt() and 0xff) or ((bytes[p + 5].toInt() and 0xff) shl 8) or
                        ((bytes[p + 6].toInt() and 0xff) shl 16) or ((bytes[p + 7].toInt() and 0xff) shl 24)
                    if (id == "data") {
                        val n = minOf(size, bytes.size - p - 8) / 2
                        return FloatArray(n) { i ->
                            val lo = bytes[p + 8 + 2 * i].toInt() and 0xff
                            val hi = bytes[p + 9 + 2 * i].toInt()
                            ((hi shl 8) or lo) / 32768f
                        }
                    }
                    p += 8 + size
                }
                return FloatArray(0)
            }
        }
    }
}
