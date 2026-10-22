package co.choclement.murmur

import android.content.Context
import android.content.SharedPreferences

/** All user settings. */
object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        if (!::sp.isInitialized) sp = context.applicationContext.getSharedPreferences("murmur", Context.MODE_PRIVATE)
    }

    /** "hold" = hold to talk, "tap" = tap to start, tap to stop. */
    var mode: String
        get() = sp.getString("mode", "hold")!!
        set(v) = sp.edit().putString("mode", v).apply()

    var model: String
        get() = sp.getString("model", Models.BUILT_IN)!!
        set(v) = sp.edit().putString("model", v).apply()

    /** "" = auto-detect. */
    var language: String
        get() = sp.getString("language", "")!!
        set(v) = sp.edit().putString("language", v).apply()

    /** Murmur keyboard mode: "typing" (keys) or "voice" (listen as soon as a text box is tapped). */
    var imeMode: String
        get() = sp.getString("imeMode", "voice")!!
        set(v) = sp.edit().putString("imeMode", v).apply()

    var haptics: Boolean
        get() = sp.getBoolean("haptics", true)
        set(v) = sp.edit().putBoolean("haptics", v).apply()

    /** Where the bubble sits along the top of the keyboard, 0 = left edge, 1 = right edge. */
    var bubbleX: Float
        get() = sp.getFloat("bubbleX", 0.9f)
        set(v) = sp.edit().putFloat("bubbleX", v).apply()

    /**
     * Where you've dragged the bubble in a given app: horizontal spot (0–1) and how far it's
     * lifted (dp) from its automatic place. Apps you haven't adjusted use the default side.
     */
    fun position(pkg: String): Pair<Float, Float> {
        val saved = sp.getString("pos.$pkg", null)?.split(",")?.mapNotNull { it.toFloatOrNull() }
        return if (saved?.size == 2) saved[0] to saved[1] else bubbleX to 0f
    }

    fun savePosition(pkg: String, x: Float, liftDp: Float) =
        sp.edit().putString("pos.$pkg", "$x,$liftDp").apply()

    fun resetPositions() {
        val e = sp.edit()
        sp.all.keys.filter { it.startsWith("pos.") }.forEach { e.remove(it) }
        e.apply()
    }

    var lastText: String
        get() = sp.getString("lastText", "")!!
        set(v) = sp.edit().putString("lastText", v).apply()

    val isHold get() = mode == "hold"

    fun listen(l: SharedPreferences.OnSharedPreferenceChangeListener) = sp.registerOnSharedPreferenceChangeListener(l)
    fun unlisten(l: SharedPreferences.OnSharedPreferenceChangeListener) = sp.unregisterOnSharedPreferenceChangeListener(l)

    val languages = listOf(
        "" to "Auto-detect", "en" to "English", "he" to "Hebrew", "es" to "Spanish", "fr" to "French",
        "de" to "German", "it" to "Italian", "pt" to "Portuguese", "ru" to "Russian", "ar" to "Arabic",
        "yi" to "Yiddish", "zh" to "Chinese", "ja" to "Japanese",
    )
}
