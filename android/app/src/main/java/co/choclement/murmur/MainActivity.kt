package co.choclement.murmur

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Setup and settings. Deliberately one simple screen. */
class MainActivity : Activity() {

    private val d by lazy { resources.displayMetrics.density }
    private val dark by lazy {
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
    private val fg get() = if (dark) Color.WHITE else Color.rgb(20, 20, 24)
    private val muted get() = if (dark) Color.rgb(160, 160, 170) else Color.rgb(110, 110, 120)
    private val cardBg get() = if (dark) Color.rgb(30, 30, 36) else Color.WHITE
    private val pageBg get() = if (dark) Color.rgb(14, 14, 18) else Color.rgb(244, 243, 248)
    private val accent = Color.rgb(110, 77, 184)

    private lateinit var kbEnabledRow: TextView
    private lateinit var kbEnableButton: Button
    private lateinit var kbActiveRow: TextView
    private lateinit var kbActiveButton: Button
    private lateinit var micRow: TextView
    private lateinit var micButton: Button
    private lateinit var axRow: TextView
    private lateinit var axButton: Button
    private lateinit var axHelp: TextView
    private lateinit var status: TextView
    private lateinit var modelGroup: RadioGroup
    private lateinit var lastText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        Transcriber.load(this)
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
            setPadding(px(20), px(56), px(20), px(40))
        }

        col.addView(text("Murmur", 30f, fg, bold = true))
        col.addView(text("Hold the bubble above your keyboard, speak, let go. Your words are typed in. Everything runs on this phone.", 15f, muted).apply {
            setPadding(0, px(6), 0, px(18))
        })

        // The Murmur keyboard
        col.addView(card {
            addView(header("Murmur Keyboard"))
            addView(text("A Gboard-style keyboard with two modes. Typing: regular keys. Voice: no keys; " +
                "tap any text box and it starts listening, your words appear as you speak, then tap Send. " +
                "Switch modes with the Voice / Keyboard button at the top of the keyboard.", 14f, muted).apply {
                setPadding(0, 0, 0, px(8))
            })
            kbEnabledRow = text("", 16f, fg)
            kbEnableButton = button("Turn on") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
            addView(row(kbEnabledRow, kbEnableButton))
            kbActiveRow = text("", 16f, fg)
            kbActiveButton = button("Switch") { getSystemService(InputMethodManager::class.java).showInputMethodPicker() }
            addView(row(kbActiveRow, kbActiveButton))
            addView(text("Opens with", 16f, fg).apply { setPadding(0, px(8), 0, 0) })
            val modes = RadioGroup(this@MainActivity).apply { orientation = RadioGroup.HORIZONTAL }
            listOf("voice" to "Voice mode", "typing" to "Typing mode").forEach { (id, label) ->
                modes.addView(radio(label, id, Prefs.imeMode == id))
            }
            modes.setOnCheckedChangeListener { g, checked -> Prefs.imeMode = g.findViewById<View>(checked).tag as String }
            addView(modes)
        })

        // Setup
        col.addView(card {
            addView(header("Setup"))
            micRow = text("", 16f, fg)
            micButton = button("Allow") { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS), 1) }
            addView(row(micRow, micButton))
            axRow = text("", 16f, fg)
            axButton = button("Turn on") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            addView(row(axRow, axButton))
            axHelp = text(
                "In the list, tap Installed apps › Murmur voice typing › turn it on.\n\n" +
                    "If the switch is greyed out (\"Restricted setting\"): open App info below, tap ⋮ in the top corner, " +
                    "choose Allow restricted settings, then come back and try again.",
                13f, muted,
            ).apply { setPadding(0, px(4), 0, px(6)) }
            addView(axHelp)
            addView(button("Open App info", filled = false) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            })
        })

        // How it listens
        col.addView(card {
            addView(header("How to talk"))
            val group = RadioGroup(this@MainActivity)
            listOf("hold" to "Hold to talk, let go to type", "tap" to "Tap to start, tap again to type").forEach { (id, label) ->
                group.addView(radio(label, id, Prefs.mode == id))
            }
            group.setOnCheckedChangeListener { g, checked -> Prefs.mode = g.findViewById<View>(checked).tag as String }
            addView(group)
            addView(text("Slide your finger away from the bubble to cancel.", 13f, muted).apply {
                setPadding(0, px(6), 0, 0)
            })
        })

        // Where the bubble sits
        col.addView(card {
            addView(header("Bubble position"))
            addView(text("Drag the bubble anywhere to move it: touch it and slide right away. " +
                "Murmur remembers the spot separately for each app, so you can move it off WhatsApp's " +
                "voice-note button without changing it everywhere else.", 14f, muted).apply {
                setPadding(0, 0, 0, px(10))
            })
            addView(text("Default side (apps you haven't adjusted)", 16f, fg))
            val side = RadioGroup(this@MainActivity).apply { orientation = RadioGroup.HORIZONTAL }
            listOf("left" to "Left", "right" to "Right").forEach { (id, label) ->
                side.addView(radio(label, id, (Prefs.bubbleX >= 0.5f) == (id == "right")))
            }
            side.setOnCheckedChangeListener { g, checked ->
                Prefs.bubbleX = if (g.findViewById<View>(checked).tag == "right") 0.9f else 0.1f
            }
            addView(side)
            addView(button("Reset all positions", filled = false) {
                Prefs.resetPositions()
                Toast.makeText(this@MainActivity, "Bubble back to its default spot in every app", Toast.LENGTH_SHORT).show()
            }.apply { layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = px(8) } })
        })

        // Model and language
        col.addView(card {
            addView(header("Accuracy"))
            modelGroup = RadioGroup(this@MainActivity)
            addView(modelGroup)
            status = text("", 13f, muted).apply { setPadding(0, px(8), 0, px(10)) }
            addView(status)
            addView(text("Language", 16f, fg))
            val spinner = Spinner(this@MainActivity)
            spinner.adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, Prefs.languages.map { it.second })
            spinner.setSelection(Prefs.languages.indexOfFirst { it.first == Prefs.language }.coerceAtLeast(0))
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    val code = Prefs.languages[pos].first
                    if (code != Prefs.language) Prefs.language = code
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
            addView(spinner)
        })

        // Feel
        col.addView(card {
            addView(header("Feel"))
            addView(Switch(this@MainActivity).apply {
                text = "Vibrate when it starts and stops"
                textSize = 16f
                setTextColor(fg)
                isChecked = Prefs.haptics
                setOnCheckedChangeListener { _, on -> Prefs.haptics = on }
            })
        })

        // Try it
        col.addView(card {
            addView(header("Try it"))
            addView(EditText(this@MainActivity).apply {
                hint = "Tap here, then hold the bubble above the keyboard"
                setTextColor(fg)
                setHintTextColor(muted)
                minLines = 3
                gravity = Gravity.TOP
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                tag = "tryit"
            })
            lastText = text("", 14f, muted).apply { setPadding(0, px(10), 0, px(4)) }
            addView(lastText)
            addView(button("Copy last dictation", filled = false) {
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Murmur", Prefs.lastText))
                Toast.makeText(this@MainActivity, "Copied", Toast.LENGTH_SHORT).show()
            })
        })

        return ScrollView(this).apply {
            setBackgroundColor(pageBg)
            isFillViewport = true
            addView(col)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refresh() // e.g. after picking a keyboard in the system popup
    }

    private fun refresh() {
        val imm = getSystemService(InputMethodManager::class.java)
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val active = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.startsWith("$packageName/") == true
        kbEnabledRow.text = if (enabled) "✓  Keyboard turned on" else "Turn on Murmur Keyboard"
        kbEnableButton.visibility = if (enabled) View.GONE else View.VISIBLE
        kbActiveRow.text = if (active) "✓  In use as your keyboard" else "Make it your keyboard"
        kbActiveButton.visibility = if (active || !enabled) View.GONE else View.VISIBLE

        val mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        micRow.text = if (mic) "✓  Microphone" else "Microphone"
        micButton.visibility = if (mic) View.GONE else View.VISIBLE
        val ax = serviceEnabled()
        axRow.text = if (ax) "✓  Murmur voice typing" else "Murmur voice typing"
        axButton.visibility = if (ax) View.GONE else View.VISIBLE
        axHelp.visibility = if (ax) View.GONE else View.VISIBLE
        (axHelp.parent as LinearLayout).getChildAt((axHelp.parent as LinearLayout).indexOfChild(axHelp) + 1).visibility =
            if (ax) View.GONE else View.VISIBLE
        rebuildModels()
        lastText.text = if (Prefs.lastText.isEmpty()) "No dictations yet." else "Last: ${Prefs.lastText}"
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

    private fun serviceEnabled(): Boolean {
        val am = getSystemService(AccessibilityManager::class.java)
        return am.getEnabledAccessibilityServiceList(-1).any { it.resolveInfo.serviceInfo.packageName == packageName }
    }

    private fun rebuildModels() {
        modelGroup.setOnCheckedChangeListener(null)
        modelGroup.removeAllViews()
        for (m in Models.all) {
            val ready = Models.isReady(this, m.id)
            val label = when {
                Models.downloading == m.id -> "${m.label} · downloading…"
                ready -> m.label
                else -> "${m.label} · tap to download ${m.sizeMb} MB"
            }
            modelGroup.addView(radio(label, m.id, Prefs.model == m.id && ready))
        }
        modelGroup.setOnCheckedChangeListener { g, checked ->
            val id = g.findViewById<View>(checked)?.tag as? String ?: return@setOnCheckedChangeListener
            if (Models.isReady(this, id)) {
                Prefs.model = id
                status.text = "Switching model…"
                status.postDelayed({ status.text = Transcriber.status }, 2500)
            } else {
                startDownload(id)
            }
        }
    }

    private fun startDownload(id: String) {
        Models.download(this, id,
            onProgress = { pct -> runOnUiThread { status.text = "Downloading ${Models.byId(id).label.substringBefore(" ·")}… $pct%" } },
            onDone = { err ->
                runOnUiThread {
                    if (err == null) {
                        Prefs.model = id
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

    // MARK: - Small view helpers

    private fun px(dp: Int) = (dp * d).toInt()

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun header(s: String) = text(s.uppercase(), 12f, accent, bold = true).apply {
        letterSpacing = 0.08f
        setPadding(0, 0, 0, px(10))
    }

    private fun card(content: LinearLayout.() -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(px(18), px(16), px(18), px(16))
        background = GradientDrawable().apply { cornerRadius = 20 * d; setColor(cardBg) }
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = px(14) }
        content()
    }

    private fun row(label: TextView, action: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, px(4), 0, px(4))
        addView(label, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        addView(action)
    }

    private fun button(s: String, filled: Boolean = true, onClick: () -> Unit) = Button(this).apply {
        text = s
        isAllCaps = false
        textSize = 15f
        setTextColor(if (filled) Color.WHITE else accent)
        background = GradientDrawable().apply {
            cornerRadius = 50 * d
            if (filled) setColor(accent) else { setColor(Color.TRANSPARENT); setStroke(px(1), accent) }
        }
        setPadding(px(18), 0, px(18), 0)
        minHeight = px(40)
        minimumHeight = px(40)
        stateListAnimator = null
        setOnClickListener { onClick() }
    }

    private fun radio(label: String, id: String, checked: Boolean) = RadioButton(this).apply {
        text = label
        tag = id
        this.id = View.generateViewId()
        textSize = 16f
        setTextColor(fg)
        isChecked = checked
        setPadding(px(6), px(8), 0, px(8))
    }

    // MARK: - Testing (debug builds only)

    /** adb: am start -n co.choclement.murmur/.MainActivity --es testWav /data/local/tmp/x.wav */
    private fun handleTestIntent(intent: Intent?) {
        if (!BuildConfig.DEBUG) return
        // Voice-mode keyboard test: the next listening session plays this file instead of the mic.
        intent?.getStringExtra("liveWav")?.let { LiveSession.testAudio = Session.WavSource.load(it); return }
        val path = intent?.getStringExtra("testWav") ?: return
        val delay = intent.getLongExtra("delayMs", 0)
        if (delay > 0) {
            // Plays into whatever app is in front after the delay (switch apps meanwhile).
            val samples = Session.WavSource.load(path)
            MurmurService.instance?.simulateLater(samples, delay) { text, wait ->
                android.util.Log.i("MurmurTest", "waitAfterRelease=${wait}ms text=$text")
            }
            return
        }
        val field = window.decorView.findViewWithTag<EditText>("tryit")
        field.requestFocus()
        field.postDelayed({
            val service = MurmurService.instance ?: return@postDelayed run { android.util.Log.i("MurmurTest", "service not running") }
            val samples = Session.WavSource.load(path)
            service.simulate(samples) { text, wait ->
                android.util.Log.i("MurmurTest", "waitAfterRelease=${wait}ms text=$text field=${field.text}")
            }
        }, 1500)
    }
}
