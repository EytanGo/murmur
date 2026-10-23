package co.choclement.murmur

import android.content.Intent
import android.util.Log

/** What's different about this edition: Murmur Bubble, the floating hold-to-talk button. */
object Edition {
    const val BUBBLE = true

    /**
     * adb: am start -n co.choclement.murmur.bubble/co.choclement.murmur.MainActivity --es testWav /data/local/tmp/x.wav
     * plays the file through the bubble as if you held it and spoke (into the focused text box).
     */
    fun handleTestIntent(intent: Intent?) {
        val path = intent?.getStringExtra("testWav") ?: return
        val service = MurmurService.instance ?: return run { Log.i("MurmurTest", "service not running") }
        val delay = intent.getLongExtra("delayMs", 1500)
        service.simulateLater(WavSource.load(path), delay) { text, wait -> Log.i("MurmurTest", "waitAfterRelease=${wait}ms text=$text") }
    }
}
