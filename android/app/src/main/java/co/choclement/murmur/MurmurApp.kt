package co.choclement.murmur

import android.app.Application

/** Loads settings before anything else in the app runs. */
class MurmurApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
    }
}
