package co.choclement.murmur

import org.json.JSONArray
import org.json.JSONObject

/** Say a short phrase ("my address"), get the full text typed instead. Set up in settings. */
object Snippets {
    data class Snippet(val trigger: String, val text: String)

    val all: List<Snippet>
        get() = runCatching {
            val a = JSONArray(Prefs.json("snippets"))
            List(a.length()) { a.getJSONObject(it).let { o -> Snippet(o.getString("say"), o.getString("text")) } }
        }.getOrDefault(emptyList())

    fun add(trigger: String, text: String) {
        val t = trigger.trim()
        if (t.isEmpty() || text.isBlank()) return
        save(all.filter { !it.trigger.equals(t, ignoreCase = true) } + Snippet(t, text.trim()))
    }

    fun remove(trigger: String) = save(all.filter { it.trigger != trigger })

    /**
     * Swaps spoken triggers for their text. If the whole phrase is just the trigger ("My address."),
     * the result is exactly the snippet, without the period Whisper added.
     */
    fun expand(text: String): String {
        val list = all.sortedByDescending { it.trigger.length }
        if (list.isEmpty() || text.isBlank()) return text
        val bare = text.trim().trimEnd('.', '!', '?', ',')
        list.firstOrNull { it.trigger.equals(bare, ignoreCase = true) }?.let { return it.text }
        var out = text
        for (s in list) {
            val pattern = Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(s.trigger) + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
            out = pattern.replace(out) { s.text }
        }
        return out
    }

    private fun save(list: List<Snippet>) =
        Prefs.setJson("snippets", JSONArray(list.map { JSONObject().put("say", it.trigger).put("text", it.text) }).toString())
}
