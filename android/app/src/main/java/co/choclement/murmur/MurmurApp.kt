package co.choclement.murmur

import android.app.Application
import android.content.SharedPreferences

/** Loads settings before anything else in the app runs, and reloads the model when it changes. */
class MurmurApp : Application() {
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "model" || key == "language") Transcriber.load(this)
    }

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Prefs.listen(prefsListener)
    }
}
