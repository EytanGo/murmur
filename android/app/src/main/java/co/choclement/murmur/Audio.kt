package co.choclement.murmur

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/** Where voice mode's 16 kHz mono audio comes from: the microphone, or a WAV file in tests. */
interface AudioSource {
    fun start(): Boolean
    /** Fills [buf], returns how many samples were read, or -1 when finished. */
    fun read(buf: FloatArray): Int
    /** After you let go: whatever audio is still buffered, without waiting. 0 or -1 when empty. */
    fun readRemaining(buf: FloatArray): Int
    fun stop()
}

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
