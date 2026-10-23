package co.choclement.murmur

import android.content.Intent

/** What's different about this edition: Murmur, the keyboard. */
object Edition {
    const val BUBBLE = false

    /** adb: am start -n co.choclement.murmur/.MainActivity --es liveWav /data/local/tmp/x.wav — the next voice-mode session plays it. */
    fun handleTestIntent(intent: Intent?) {
        intent?.getStringExtra("liveWav")?.let { LiveSession.testAudio = WavSource.load(it) }
    }
}
