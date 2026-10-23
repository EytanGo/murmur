package co.choclement.murmur

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

/** Setup and settings, in the Pebble look. Deliberately one simple screen. */
class MainActivity : Activity() {

    private val p by lazy { Pebble(this) }
    private val look get() = p.look

    private lateinit var kbEnabled: LinearLayout
    private lateinit var kbActive: LinearLayout
    private lateinit var micRow: LinearLayout
    private lateinit var axRow: LinearLayout
    private lateinit var axHelp: LinearLayout
    private lateinit var status: TextView
    private lateinit var modelList: LinearLayout
    private lateinit var cloudBox: LinearLayout
    private lateinit var cloudStatus: TextView
    private lateinit var cloudModels: LinearLayout
    private lateinit var cleanupSwitch: Pebble.Switch
    private lateinit var cleanupNote: TextView
    private lateinit var snippetList: LinearLayout
    private lateinit var snippetForm: LinearLayout
    private lateinit var lastText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        Transcriber.load(this)
        window.statusBarColor = look.bg
        window.navigationBarColor = look.bg
        if (!look.dark) window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContentView(build())
        handleTestIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleTestIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    // MARK: - Layout

    private fun build(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(p.px(12f), p.px(48f), p.px(12f), p.px(32f))
        }

        // App mark and name.
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(p.px(12f), 0, 0, p.px(10f))
            addView(object : View(this@MainActivity) {
                override fun onDraw(c: Canvas) {
                    val m = 8 * look.d
                    val r = RectF(m, m, width - m, height - m)
                    look.lift(c, r, 22 * look.d, look.accent, 5f)
                    val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = look.key; strokeWidth = 2.6f * look.d; strokeCap = Paint.Cap.ROUND
                    }
                    val s = r.width() / 24 * 0.55f
                    for ((x, h) in listOf(4f to 2f, 8f to 5f, 12f to 8f, 16f to 4f, 20f to 1f)) {
                        val cx = r.centerX() + (x - 12) * s
                        c.drawLine(cx, r.centerY() - h * s, cx, r.centerY() + h * s, pen)
                    }
                }
            }, LinearLayout.LayoutParams(p.px(74f), p.px(74f)).apply { marginEnd = p.px(8f) })
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(p.text(if (Edition.BUBBLE) "Murmur Bubble" else "Murmur", if (Edition.BUBBLE) 28f else 32f, weight = 800, display = true))
                addView(p.text(if (Edition.BUBBLE) "Hold the bubble. Talk softly. It types." else "Talk softly. It types.", 14f, look.muted, 600))
                addView(p.text("Version ${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}", 12f, look.muted, 700))
            })
        })

        // The Murmur keyboard (keyboard edition)
        if (!Edition.BUBBLE) col.addView(p.card {
            addView(p.heading("Murmur Keyboard") { c, x, y, color -> look.keyboard(c, x, y, color) })
            addView(p.text("Voice mode has no keys: tap any text box and it listens, your words appear as you speak, " +
                "then tap Send. Typing mode is a regular keyboard.", 13f, look.muted, 600).apply { setPadding(0, 0, 0, p.px(6f)) })
            kbEnabled = statusRow("Turned on", "Turn on") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
            addView(kbEnabled)
            kbActive = statusRow("In use", "Switch") { getSystemService(InputMethodManager::class.java).showInputMethodPicker() }
            addView(kbActive)
            addView(p.text("Opens with", 12f, look.muted, 700).apply { setPadding(0, p.px(8f), 0, p.px(6f)) })
            addView(p.segmented(listOf("voice" to "Voice", "typing" to "Typing"), Prefs.imeMode) { Prefs.imeMode = it })
        })

        // Setup
        col.addView(p.card {
            addView(p.heading("Setup") { c, x, y, color -> look.mic(c, x, y, color, 0.8f) })
            micRow = statusRow("Microphone", "Allow") {
                // The bubble also shows a quiet "Murmur is listening" notification while you hold it.
                requestPermissions(if (Edition.BUBBLE) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
                    else arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            }
            addView(micRow)
            axRow = statusRow(if (Edition.BUBBLE) "Murmur Bubble" else "Send in chat apps", "Turn on") {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            addView(axRow)
            axHelp = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(p.text((if (Edition.BUBBLE) "Shows the bubble above your keyboard and types your words in. " +
                    "In the list, tap Installed apps › Murmur Bubble › turn it on.\n\n"
                    else "Lets Send in voice mode press WhatsApp's (and other chat apps') own send button. " +
                    "In the list, tap Installed apps › Murmur send button › turn it on.\n\n") +
                    "If the switch is greyed out (\"Restricted setting\"): open App info, tap ⋮ in the top corner, " +
                    "choose Allow restricted settings, then come back and try again.", 13f, look.muted, 600).apply {
                    setPadding(0, p.px(4f), 0, p.px(6f))
                })
                addView(p.button("Open App info") {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                })
            }
            addView(axHelp)
        })

        // How to talk, and where the bubble sits (bubble edition)
        if (Edition.BUBBLE) col.addView(p.card {
            addView(p.heading("How to talk"))
            addView(p.segmented(listOf("hold" to "Hold to talk", "tap" to "Tap to talk"), Prefs.mode) { Prefs.mode = it })
            addView(p.text("Slide your finger away from the bubble to cancel.", 12f, look.muted, 600).apply {
                setPadding(p.px(4f), p.px(6f), 0, p.px(10f))
            })
            addView(p.heading("Bubble side"))
            addView(p.segmented(listOf("left" to "Left", "right" to "Right"), if (Prefs.bubbleX >= 0.5f) "right" else "left") {
                Prefs.bubbleX = if (it == "right") 0.9f else 0.1f
            })
            addView(p.text("Drag the bubble to move it: touch it and slide right away. Murmur remembers the spot for each app.",
                12f, look.muted, 600).apply { setPadding(p.px(4f), p.px(6f), 0, p.px(4f)) })
            addView(p.button("Reset positions") {
                Prefs.resetPositions()
                Toast.makeText(this@MainActivity, "Bubble back to its default spot in every app", Toast.LENGTH_SHORT).show()
            }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        })

        // Where the speech is turned into text
        col.addView(p.card {
            addView(p.heading("Transcription") { c, x, y, color -> cloud(c, x, y, color) })
            addView(p.segmented(listOf("local" to "On this phone", "cloud" to "Cloud · OpenAI"), Prefs.engine) {
                Prefs.engine = it
                refreshCloud()
            })
            cloudBox = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, p.px(10f), 0, 0)
                addView(p.text("OpenAI API key", 13f, look.muted, 700))
                val key = p.field("sk-…", password = true).apply { setText(Prefs.apiKey) }
                addView(key)
                addView(p.button("Save and check key", primary = true) {
                    Prefs.apiKey = key.text.toString().trim()
                    cloudStatus.text = "Checking…"
                    Thread {
                        val err = Cloud.check(Prefs.apiKey)
                        runOnUiThread {
                            cloudStatus.text = if (err == null) "Key works · dictation now uses OpenAI" else "✗  $err"
                            refresh()
                        }
                    }.start()
                }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = p.px(6f) })
                cloudStatus = p.text("", 13f, look.accentDeep, 700).apply { setPadding(p.px(4f), p.px(4f), 0, p.px(4f)) }
                addView(cloudStatus)
                cloudModels = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
                addView(cloudModels)
                addView(p.text("Your speech is sent to OpenAI; the key stays on this phone. With no signal, " +
                    "a downloaded phone model takes over.", 12f, look.muted, 600).apply { setPadding(p.px(4f), p.px(8f), 0, 0) })
            }
            addView(cloudBox)
        })

        // AI cleanup, on a warm card
        col.addView(p.card(look.peach) {
            cleanupSwitch = p.Switch(Prefs.aiCleanup) { Prefs.aiCleanup = it }
            addView(p.row(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(p.text("AI cleanup", 17f, weight = 700, display = true))
                cleanupNote = p.text("", 12f, look.muted, 600)
                addView(cleanupNote)
            }, cleanupSwitch))
        })

        // Snippets
        col.addView(p.card {
            addView(p.row(p.heading("Snippets"), p.button("+ Add") {
                snippetForm.visibility = if (snippetForm.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }))
            addView(p.text(if (Edition.BUBBLE) "Say a short phrase and Murmur types the full text."
                else "Say a short phrase and Murmur types the full text. They're also in the keyboard's clipboard.",
                12f, look.muted, 600).apply { setPadding(0, 0, 0, p.px(4f)) })
            snippetList = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            addView(snippetList)
            snippetForm = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
                val say = p.field("When I say… (e.g. my address)")
                val types = p.field("Type this…", multiLine = true)
                addView(say)
                addView(types)
                addView(p.button("Save snippet", primary = true) {
                    if (say.text.isBlank() || types.text.isBlank()) {
                        Toast.makeText(this@MainActivity, "Fill in both boxes", Toast.LENGTH_SHORT).show()
                        return@button
                    }
                    Snippets.add(say.text.toString(), types.text.toString())
                    say.text.clear(); types.text.clear()
                    visibility = View.GONE
                    rebuildSnippets()
                }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = p.px(6f) })
            }
            addView(snippetForm)
        })

        // Phone model
        col.addView(p.card {
            addView(p.heading("Phone model"))
            addView(p.text("Optional download. Lets Murmur work offline, and shows your words live while you talk.",
                12f, look.muted, 600).apply { setPadding(0, 0, 0, p.px(4f)) })
            modelList = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            addView(modelList)
            status = p.text("", 12f, look.muted, 700).apply { setPadding(p.px(4f), p.px(8f), 0, 0) }
            addView(status)
        })

        // Language and feel
        col.addView(p.card {
            val spinner = Spinner(this@MainActivity).apply {
                background = p.Soft(look.bg, 999f, Pebble.Depth.PRESS, 0f)
                setPadding(p.px(14f), p.px(6f), p.px(14f), p.px(6f))
                adapter = object : ArrayAdapter<String>(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                    Prefs.languages.map { it.second }) {
                    override fun getView(pos: Int, v: View?, parent: android.view.ViewGroup) =
                        (super.getView(pos, v, parent) as TextView).apply {
                            typeface = look.body(700); setTextColor(look.text); textSize = 14f
                        }
                }
                setSelection(Prefs.languages.indexOfFirst { it.first == Prefs.language }.coerceAtLeast(0))
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                        val code = Prefs.languages[pos].first
                        if (code != Prefs.language) Prefs.language = code
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
            }
            addView(p.row(p.text("Language", 15f, weight = 700), spinner))
            addView(p.text("Picking the language you speak is a little faster and more accurate than auto-detect.",
                12f, look.muted, 600).apply { setPadding(0, 0, 0, p.px(8f)) })
            addView(p.row(p.text("Vibrate on start and stop", 15f, weight = 700), p.Switch(Prefs.haptics) { Prefs.haptics = it }))
        })

        // Try it
        col.addView(p.card {
            addView(p.heading("Try it"))
            addView(p.field(if (Edition.BUBBLE) "Tap here, then hold the bubble above the keyboard" else "Tap here, then speak",
                multiLine = true).apply { tag = "tryit"; minLines = 3 })
            if (Edition.BUBBLE) {
                lastText = p.text("", 13f, look.muted, 600).apply { setPadding(p.px(4f), p.px(10f), 0, p.px(4f)) }
                addView(lastText)
                addView(p.button("Copy last dictation") {
                    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Murmur", Prefs.lastText))
                    Toast.makeText(this@MainActivity, "Copied", Toast.LENGTH_SHORT).show()
                }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            }
        })

        return ScrollView(this).apply {
            setBackgroundColor(look.bg)
            isFillViewport = true
            addView(col)
        }
    }

    /** A setup line: the label, then a green "Yes"-style chip when done or a button to do it. */
    private fun statusRow(label: String, action: String, onClick: () -> Unit) = p.row(
        p.text(label, 15f, weight = 700),
        LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(p.chip("Yes"))
            addView(p.button(action) { onClick() })
        },
    )

    /** Shows the chip ("Yes") or the button, depending on [done]. */
    private fun setStatus(row: LinearLayout, done: Boolean, chipText: String = "Yes") {
        val end = row.getChildAt(1) as LinearLayout
        (end.getChildAt(0) as TextView).apply { text = chipText; visibility = if (done) View.VISIBLE else View.GONE }
        end.getChildAt(1).visibility = if (done) View.GONE else View.VISIBLE
    }

    private fun cloud(c: Canvas, x: Float, y: Float, color: Int) {
        val s = look.d * 0.8f
        c.drawPath(android.graphics.Path().apply {
            moveTo(x - 5 * s, y + 6 * s)
            arcTo(RectF(x - 10 * s, y - 4 * s, x, y + 6 * s), 90f, 180f)
            arcTo(RectF(x - 6 * s, y - 8 * s, x + 6 * s, y + 4 * s), 200f, 140f)
            arcTo(RectF(x + 1 * s, y - 2 * s, x + 9 * s, y + 6 * s), 270f, 180f)
            close()
        }, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2 * look.d; this.color = color; strokeJoin = Paint.Join.ROUND
        })
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refresh() // e.g. after picking a keyboard in the system popup
    }

    private fun refresh() {
        val imm = getSystemService(InputMethodManager::class.java)
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val active = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.startsWith("$packageName/") == true
        if (!Edition.BUBBLE) {
            setStatus(kbEnabled, enabled)
            setStatus(kbActive, active)
            if (!enabled) (kbActive.getChildAt(1) as LinearLayout).getChildAt(1).visibility = View.GONE
        } else {
            lastText.text = if (Prefs.lastText.isEmpty()) "No dictations yet." else "Last: ${Prefs.lastText}"
        }
        setStatus(micRow, checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED, "Allowed")
        val ax = serviceEnabled()
        setStatus(axRow, ax, "On")
        axHelp.visibility = if (ax) View.GONE else View.VISIBLE
        rebuildModels()
        refreshCloud()
        rebuildSnippets()
        val hasKey = Prefs.apiKey.isNotBlank()
        cleanupSwitch.isEnabled = hasKey
        cleanupNote.text = if (hasKey) "Smooths out “um”, fixes grammar, keeps only your corrections. Adds about half a second."
            else "Add an OpenAI key under Transcription to use this."
        status.text = Transcriber.status
        status.removeCallbacks(statusTicker)
        status.post(statusTicker)
    }

    /** Keeps the model status line current while the screen is open. */
    private val statusTicker = object : Runnable {
        override fun run() {
            if (isFinishing || Models.downloading != null) return
            if (!status.text.startsWith("Switching")) status.text = Transcriber.status
            status.postDelayed(this, 1000)
        }
    }

    private fun refreshCloud() {
        cloudBox.visibility = if (Prefs.engine == "cloud") View.VISIBLE else View.GONE
        cloudStatus.text = when {
            Prefs.apiKey.isBlank() -> "Paste your key from platform.openai.com › API keys."
            Cloud.lastError != null -> "✗  Last dictation fell back to the phone: ${Cloud.lastError}"
            cloudStatus.text.startsWith("Key works") -> cloudStatus.text
            else -> "Key saved"
        }
        cloudModels.removeAllViews()
        Cloud.models.forEach { (id, label) ->
            val (title, sub) = label.split(" (", limit = 2).let { it[0] to it.getOrNull(1)?.removeSuffix(")") }
            cloudModels.addView(p.option(title, sub, null, Prefs.cloudModel == id) { Prefs.cloudModel = id; refreshCloud() })
        }
    }

    private fun rebuildSnippets() {
        snippetList.removeAllViews()
        for (s in Snippets.all) {
            snippetList.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = p.Soft(look.bg, 18f, Pebble.Depth.PRESS, 0f)
                setPadding(p.px(14f), p.px(10f), p.px(6f), p.px(10f))
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = p.px(6f) }
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(p.text("“${s.trigger}”", 15f, weight = 800))
                    addView(p.text(s.text, 12f, look.muted, 600).apply { maxLines = 3 })
                }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(p.text("Delete", 13f, look.clay, 800).apply {
                    setPadding(p.px(12f), p.px(10f), p.px(12f), p.px(10f))
                    setOnClickListener { Snippets.remove(s.trigger); rebuildSnippets() }
                })
            })
        }
    }

    private fun serviceEnabled(): Boolean {
        val am = getSystemService(AccessibilityManager::class.java)
        return am.getEnabledAccessibilityServiceList(-1).any { it.resolveInfo.serviceInfo.packageName == packageName }
    }

    private fun rebuildModels() {
        modelList.removeAllViews()
        val sub = mapOf("tiny" to "fastest", "base" to "balanced", "small" to "most accurate")
        for (m in Models.all) {
            val ready = Models.isReady(this, m.id)
            val chip = when {
                Models.downloading == m.id -> p.chip("Downloading…")
                ready -> p.chip("On phone")
                else -> p.chip("${m.sizeMb} MB", look.bg, look.muted)
            }
            val on = ready && Models.current(this) == m.id
            modelList.addView(p.option(m.label.substringBefore(" ·"), sub[m.id], chip, on) {
                if (Models.isReady(this, m.id)) {
                    Prefs.model = m.id
                    status.text = "Switching model…"
                    status.postDelayed({ status.text = Transcriber.status }, 2500)
                    rebuildModels()
                } else if (Models.downloading == null) {
                    startDownload(m.id)
                }
            })
        }
    }

    private fun startDownload(id: String) {
        Models.download(this, id,
            onProgress = { pct -> runOnUiThread { status.text = "Downloading ${Models.byId(id).label.substringBefore(" ·")}… $pct%" } },
            onDone = { err ->
                runOnUiThread {
                    if (err == null) {
                        Prefs.model = id
                        Transcriber.load(this)
                        status.text = "Downloaded. Loading…"
                        status.removeCallbacks(statusTicker)
                        status.postDelayed(statusTicker, 2000)
                    } else {
                        status.text = "Download failed: $err"
                    }
                    rebuildModels()
                }
            })
        rebuildModels()
    }

    // MARK: - Testing (debug builds only)

    private fun handleTestIntent(intent: Intent?) {
        if (BuildConfig.DEBUG) Edition.handleTestIntent(intent)
    }
}
