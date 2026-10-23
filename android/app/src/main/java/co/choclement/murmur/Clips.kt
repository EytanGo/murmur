package co.choclement.murmur

import org.json.JSONArray

/** What you've copied: the last 10, plus up to 10 you've pinned (which stay until you unpin them). */
object Clips {
    const val MAX = 10

    val recent get() = load("clipsRecent")
    val pinned get() = load("clipsPinned")

    /** A new copy goes to the top of the recent list (unless it's pinned already). */
    fun add(text: String) {
        val t = text.trim()
        if (t.isEmpty() || t in pinned) return
        save("clipsRecent", (listOf(t) + recent.filter { it != t }).take(MAX))
    }

    /** Pins a clip. Returns false if 10 are pinned already. */
    fun pin(text: String): Boolean {
        val p = pinned
        if (text in p) return true
        if (p.size >= MAX) return false
        save("clipsPinned", listOf(text) + p)
        save("clipsRecent", recent.filter { it != text })
        return true
    }

    fun unpin(text: String) {
        save("clipsPinned", pinned.filter { it != text })
        save("clipsRecent", (listOf(text) + recent.filter { it != text }).take(MAX))
    }

    fun remove(text: String) {
        save("clipsPinned", pinned.filter { it != text })
        save("clipsRecent", recent.filter { it != text })
    }

    private fun load(name: String): List<String> = runCatching {
        val a = JSONArray(Prefs.json(name))
        List(a.length()) { a.getString(it) }
    }.getOrDefault(emptyList())

    private fun save(name: String, list: List<String>) = Prefs.setJson(name, JSONArray(list).toString())
}
