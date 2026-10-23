package co.choclement.murmur

import android.content.Context
import android.content.SharedPreferences

/** All user settings. */
object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        if (!::sp.isInitialized) sp = context.applicationContext.getSharedPreferences("murmur", Context.MODE_PRIVATE)
    }

    var model: String
        get() = sp.getString("model", "base")!!
        set(v) = sp.edit().putString("model", v).apply()

    /** "local" = Whisper on the phone, "cloud" = OpenAI with your API key. */
    var engine: String
        get() = sp.getString("engine", "local")!!
        set(v) = sp.edit().putString("engine", v).apply()

    /** Your OpenAI API key. Stays on this phone and is only sent to OpenAI. */
    var apiKey: String
        get() = sp.getString("apiKey", "")!!
        set(v) = sp.edit().putString("apiKey", v).apply()

    var cloudModel: String
        get() = sp.getString("cloudModel", "gpt-4o-transcribe")!!
        set(v) = sp.edit().putString("cloudModel", v).apply()

    /** AI cleanup of each dictation (needs the OpenAI key). */
    var aiCleanup: Boolean
        get() = sp.getBoolean("aiCleanup", false)
        set(v) = sp.edit().putBoolean("aiCleanup", v).apply()

    /** Stored lists (clipboard history, pins, snippets) as JSON. */
    fun json(name: String): String = sp.getString(name, "[]")!!
    fun setJson(name: String, v: String) = sp.edit().putString(name, v).apply()

    /** "" = auto-detect. */
    var language: String
        get() = sp.getString("language", "")!!
        set(v) = sp.edit().putString("language", v).apply()

    /** Murmur keyboard mode: "typing" (keys) or "voice" (listen as soon as a text box is tapped). */
    var imeMode: String
        get() = sp.getString("imeMode", "voice")!!
        set(v) = sp.edit().putString("imeMode", v).apply()

    /** Letters on the typing keyboard: "en" (QWERTY) or "he" (Hebrew). */
    var keyboardLanguage: String
        get() = sp.getString("keyboardLanguage", "en")!!
        set(v) = sp.edit().putString("keyboardLanguage", v).apply()

    var haptics: Boolean
        get() = sp.getBoolean("haptics", true)
        set(v) = sp.edit().putBoolean("haptics", v).apply()

    fun listen(l: SharedPreferences.OnSharedPreferenceChangeListener) = sp.registerOnSharedPreferenceChangeListener(l)
    fun unlisten(l: SharedPreferences.OnSharedPreferenceChangeListener) = sp.unregisterOnSharedPreferenceChangeListener(l)

    val languages = listOf(
        "" to "Auto-detect", "en" to "English", "he" to "Hebrew", "es" to "Spanish", "fr" to "French",
        "de" to "German", "it" to "Italian", "pt" to "Portuguese", "ru" to "Russian", "ar" to "Arabic",
        "yi" to "Yiddish", "zh" to "Chinese", "ja" to "Japanese",
    )
}
