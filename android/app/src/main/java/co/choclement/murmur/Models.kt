package co.choclement.murmur

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/** The Whisper models. Base ships inside the app; the others download once. */
object Models {
    const val BUILT_IN = "base"

    data class Model(val id: String, val label: String, val sizeMb: Int) {
        val bundled get() = id == BUILT_IN
    }

    val all = listOf(
        Model("tiny", "Tiny · fastest, less accurate", 105),
        Model("base", "Base · fast and accurate (built in)", 0),
        Model("small", "Small · most accurate, slower", 375),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id } ?: all[1]

    private fun files(id: String) = listOf("$id-encoder.int8.onnx", "$id-decoder.int8.onnx", "$id-tokens.txt")

    fun dir(context: Context, id: String) = File(context.filesDir, "models/$id")

    fun isReady(context: Context, id: String) = id == BUILT_IN || File(dir(context, id), ".done").exists()

    /** Encoder, decoder and tokens paths. For the built-in model these are asset paths. */
    fun paths(context: Context, id: String): Triple<String, String, String> {
        val f = files(id)
        return if (id == BUILT_IN) Triple("models/$id/${f[0]}", "models/$id/${f[1]}", "models/$id/${f[2]}")
        else dir(context, id).let { d -> Triple(File(d, f[0]).path, File(d, f[1]).path, File(d, f[2]).path) }
    }

    @Volatile var downloading: String? = null
        private set

    /** Downloads a model in the background. Progress is 0–100; callbacks run on that background thread. */
    fun download(context: Context, id: String, onProgress: (Int) -> Unit, onDone: (String?) -> Unit) {
        if (downloading != null) return
        downloading = id
        thread(name = "model-download") {
            val error = runCatching {
                val d = dir(context, id).apply { mkdirs() }
                val names = files(id)
                val total = byId(id).sizeMb * 1_000_000L
                var done = 0L
                for (name in names) {
                    val url = URL("https://huggingface.co/csukuangfj/sherpa-onnx-whisper-$id/resolve/main/$name")
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = true
                        connectTimeout = 20_000
                        readTimeout = 30_000
                    }
                    if (conn.responseCode !in 200..299) error("Download failed (${conn.responseCode})")
                    val part = File(d, "$name.part")
                    conn.inputStream.use { input ->
                        part.outputStream().use { out ->
                            val buf = ByteArray(256 * 1024)
                            var lastPct = -1
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                val pct = (done * 100 / total).toInt().coerceIn(0, 99)
                                if (pct != lastPct) { lastPct = pct; onProgress(pct) }
                            }
                        }
                    }
                    part.renameTo(File(d, name))
                }
                File(d, ".done").createNewFile()
            }.exceptionOrNull()
            downloading = null
            if (error != null) dir(context, id).deleteRecursively()
            onDone(error?.message ?: if (error != null) "Download failed" else null)
        }
    }

    fun delete(context: Context, id: String) {
        if (id != BUILT_IN) dir(context, id).deleteRecursively()
    }
}
