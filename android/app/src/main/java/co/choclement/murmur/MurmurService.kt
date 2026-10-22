package co.choclement.murmur

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlin.math.abs
import kotlin.math.hypot

/**
 * The heart of Murmur on Android. Shows the floating button just above whatever keyboard
 * you use (Gboard stays your keyboard), records while you hold it, and types the result
 * into the text box you're in.
 */
class MurmurService : AccessibilityService() {

    companion object {
        @Volatile var instance: MurmurService? = null
            private set
        private const val CHANNEL = "recording"
        private const val NOTIF_ID = 1
    }

    private val main = Handler(Looper.getMainLooper())
    private val io by lazy { Handler(HandlerThread("murmur-insert").apply { start() }.looper) }
    private lateinit var wm: WindowManager
    private var bubble: BubbleView? = null
    private var params: WindowManager.LayoutParams? = null
    private var keyboardTop = 0
    private var anchorTop = 0               // the bubble's automatic resting line (see autoAnchor)
    private var app = ""                    // package of the app you're typing in
    private var dragPos: Pair<Float, Float>? = null // live position while you drag it
    private var session: Session? = null
    private var busy = false   // recording or transcribing: keep the bubble up no matter what
    private var foreground = false

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "model" || key == "language") Transcriber.load(this)
    }

    override fun onServiceConnected() {
        instance = this
        Prefs.init(this)
        wm = getSystemService(WindowManager::class.java)
        createChannel()
        Transcriber.load(this)
        Prefs.listen(prefsListener)
        refreshSoon()
    }

    override fun onDestroy() {
        instance = null
        Prefs.unlisten(prefsListener)
        session?.cancel()
        removeBubble()
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = refreshSoon()

    // MARK: - Showing the bubble while a keyboard is up

    private val refresher = Runnable { refreshBubble() }

    private fun refreshSoon() {
        main.removeCallbacks(refresher)
        main.postDelayed(refresher, 80)
    }

    private fun refreshBubble() {
        if (busy) return
        val ime = runCatching { windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } }.getOrNull()
        val bounds = ime?.let { Rect().also(it::getBoundsInScreen) }
        val field = focusedField()
        // With the Murmur keyboard you speak through the keyboard itself; the bubble is for other keyboards.
        if (bounds == null || bounds.height() < 50 || field == null || murmurKeyboardActive()) {
            removeBubble()
            return
        }
        keyboardTop = bounds.top
        app = field.packageName?.toString() ?: ""
        anchorTop = autoAnchor(field, bounds.top)
        showBubble()
    }

    /**
     * Where the bubble sits by default: just above the keyboard, or, when the text box is a
     * chat bar right on top of the keyboard (WhatsApp, Messages…), just above that whole bar,
     * so it never covers the send or voice-note buttons next to it.
     */
    private fun autoAnchor(field: AccessibilityNodeInfo, keyboardTop: Int): Int {
        val d = resources.displayMetrics.density
        val r = Rect().also(field::getBoundsInScreen)
        val isChatBar = r.height() in 1..(220 * d).toInt() && r.bottom > keyboardTop - 120 * d && r.top < keyboardTop
        return if (isChatBar) minOf(keyboardTop, r.top - (8 * d).toInt()) else keyboardTop
    }

    private fun murmurKeyboardActive() =
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.startsWith("$packageName/") == true

    /**
     * Taps the app's own Send button next to the text box you're in (WhatsApp, Messages…),
     * for chat apps where the enter key only adds a new line. Returns false if there's none.
     */
    fun clickSendButton(): Boolean {
        val root = rootInActiveWindow ?: return false
        val field = Rect().also { r -> focusedField()?.getBoundsInScreen(r) }
        val label = Regex("""^\s*(send|send message|send sms|send text|submit|שלח|שליחה)\b""", RegexOption.IGNORE_CASE)
        val found = ArrayList<AccessibilityNodeInfo>()
        val queue = ArrayDeque(listOf(root))
        var visited = 0
        while (queue.isNotEmpty() && visited < 3000) {
            val n = queue.removeFirst()
            visited++
            val words = listOfNotNull(n.contentDescription, n.text).joinToString(" ")
            val id = n.viewIdResourceName?.substringAfter(":id/") ?: ""
            if (n.isVisibleToUser && (label.containsMatchIn(words) || id == "send" || id.startsWith("send_") || id.endsWith("_send"))) found += n
            for (i in 0 until n.childCount) n.getChild(i)?.let(queue::addLast)
        }
        // The one closest to the text box (send buttons sit right next to it).
        val best = found.minByOrNull { n ->
            val r = Rect().also(n::getBoundsInScreen)
            if (field.isEmpty) 0 else abs(r.centerY() - field.centerY()) + abs(r.left - field.right) / 4
        } ?: return false
        var target: AccessibilityNodeInfo? = best
        while (target != null && !target.isClickable) target = target.parent
        return (target ?: best).performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun focusedField(): AccessibilityNodeInfo? = runCatching {
        findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.looksEditable() }
    }.getOrNull()

    private fun AccessibilityNodeInfo.looksEditable(): Boolean {
        if (isEditable) return true
        if (className?.contains("EditText") == true) return true
        return actionList.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_TEXT)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showBubble() {
        val view = bubble ?: BubbleView(this).also { v ->
            v.setOnTouchListener { _, e -> onTouch(e); true }
            bubble = v
        }
        val p = params ?: WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }.also { params = it }
        position(p, view)
        if (view.parent == null) runCatching { wm.addView(view, p) } else runCatching { wm.updateViewLayout(view, p) }
    }

    /** Places the bubble at this app's saved spot (or the default), relative to its automatic place. */
    private fun position(p: WindowManager.LayoutParams, view: BubbleView) {
        val metrics = resources.displayMetrics
        val w = view.desiredWidth
        val h = view.desiredHeight
        val (x, lift) = dragPos ?: Prefs.position(app)
        val centreX = (x * metrics.widthPixels).toInt()
        val centreY = anchorTop - (6 * metrics.density).toInt() - h / 2 - (lift * metrics.density).toInt()
        p.x = (centreX - w / 2).coerceIn(0, maxOf(0, metrics.widthPixels - w))
        p.y = (centreY - h / 2).coerceIn(0, maxOf(0, metrics.heightPixels - h))
        p.width = w
        p.height = h
    }

    private fun relayout() {
        val v = bubble ?: return
        val p = params ?: return
        position(p, v)
        if (v.parent != null) runCatching { wm.updateViewLayout(v, p) }
    }

    private fun removeBubble() {
        bubble?.let { if (it.parent != null) runCatching { wm.removeView(it) } }
    }

    // MARK: - Touch: hold to talk, tap to toggle, drag to move (remembered per app), slide away to cancel

    private var downAt = 0L
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var startedOnThisTouch = false
    private val slop get() = 14 * resources.displayMetrics.density
    private val cancelDistance get() = 90 * resources.displayMetrics.density

    private fun onTouch(e: MotionEvent) {
        val v = bubble ?: return
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downAt = SystemClock.uptimeMillis()
                downX = e.rawX; downY = e.rawY
                dragging = false
                startedOnThisTouch = false
                v.pressed(true)
                // Hold mode starts listening instantly so your first word isn't lost.
                if (Prefs.isHold && session == null && !busyTranscribing) startedOnThisTouch = startRecording()
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - downX
                val dy = e.rawY - downY
                val early = SystemClock.uptimeMillis() - downAt < 300
                if (!dragging && hypot(dx, dy) > slop && (early || session == null)) {
                    // Moving your finger right away means "move the bubble", not "dictate".
                    if (startedOnThisTouch) cancelRecording(silent = true)
                    dragging = true
                }
                if (dragging) {
                    val metrics = resources.displayMetrics
                    val x = (e.rawX / metrics.widthPixels).coerceIn(0.04f, 0.96f)
                    val restingCentre = anchorTop - 6 * metrics.density - v.desiredHeight / 2f
                    dragPos = x to (restingCentre - e.rawY) / metrics.density
                    relayout()
                } else if (session != null) {
                    v.cancelArmed = hypot(dx, dy) > cancelDistance
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.pressed(false)
                val armed = v.cancelArmed
                v.cancelArmed = false
                if (dragging) {
                    dragPos?.let { (x, lift) -> Prefs.savePosition(app, x, lift) }
                    dragPos = null
                    return
                }
                val quick = SystemClock.uptimeMillis() - downAt < 400
                when {
                    session != null && armed -> cancelRecording(silent = false)
                    Prefs.isHold && session != null -> finishRecording()
                    !Prefs.isHold && quick && session != null -> finishRecording()
                    !Prefs.isHold && quick && !busyTranscribing -> startRecording()
                }
            }
        }
    }

    private var busyTranscribing = false

    // MARK: - Recording

    private fun startRecording(): Boolean {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            flash("Allow the microphone in the Murmur app")
            return false
        }
        val v = bubble ?: return false
        startMicForeground()
        val s = Session(this, onLevel = { v.pushLevel(it) })
        if (!s.start()) {
            stopMicForeground()
            flash("Microphone unavailable")
            return false
        }
        session = s
        busy = true
        buzz()
        v.setPhase(BubbleView.Phase.RECORDING)
        relayout()
        return true
    }

    private fun finishRecording() {
        val s = session ?: return
        session = null
        if (s.seconds < 0.35f) {
            s.cancel()
            stopMicForeground()
            if (Prefs.isHold) flash("Hold to talk") else idle()
            return
        }
        buzz()
        busyTranscribing = true
        bubble?.setPhase(BubbleView.Phase.WORKING)
        relayout()
        s.finish { text ->
            stopMicForeground()
            busyTranscribing = false
            if (text.isBlank()) { idle(); return@finish }
            Prefs.lastText = text
            io.post {
                val ok = insert(text)
                main.post { if (ok) idle() else flash("Copied · long-press to paste", 2600) }
            }
        }
    }

    private fun cancelRecording(silent: Boolean) {
        session?.cancel()
        session = null
        stopMicForeground()
        if (silent) idle() else { buzz(); flash("Cancelled", 900) }
    }

    private fun idle() {
        busy = false
        bubble?.setPhase(BubbleView.Phase.IDLE)
        relayout()
        refreshSoon()
    }

    private fun flash(message: String, millis: Long = 1400) {
        busy = true
        bubble?.setPhase(BubbleView.Phase.NOTICE, message)
        relayout()
        main.postDelayed({ if (bubble?.phase == BubbleView.Phase.NOTICE) idle() }, millis)
    }

    private fun buzz() {
        if (!Prefs.haptics) return
        bubble?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
    }

    // MARK: - Typing the text in

    /**
     * Types [raw] at the cursor of the focused field. Tries, in order: the keyboard-style input
     * connection (Android 13+), rewriting the field's text, and pasting. If all fail, the text
     * stays on the clipboard. Runs on a background thread.
     */
    private fun insert(raw: String): Boolean {
        val node = focusedField()
        val before = node?.realText()
        val text = withSpacing(raw, node, before)

        // 1. Exactly like a keyboard would.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val ic = runCatching { inputMethod?.currentInputConnection }.getOrNull()
            if (ic != null && runCatching { ic.commitText(text, 1, null) }.isSuccess) {
                if (node == null || landed(node, before, text)) return true
            }
        }
        if (node == null) return copyOnly(text)

        // 2. Rewrite the field with the text spliced in at the cursor.
        val current = node.realText()
        val start = node.textSelectionStart.takeIf { it in 0..current.length } ?: current.length
        val end = node.textSelectionEnd.takeIf { it in start..current.length } ?: start
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                current.substring(0, start) + text + current.substring(end))
        }
        if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) && landed(node, current, text)) {
            val cursor = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, start + text.length)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, start + text.length)
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, cursor)
            return true
        }

        // 3. Paste (works in web pages and custom editors that ignore the above).
        copyToClipboard(text)
        if (node.performAction(AccessibilityNodeInfo.ACTION_PASTE) && landed(node, current, text)) return true
        return false
    }

    /** Waits briefly for the field to show the new text. */
    private fun landed(node: AccessibilityNodeInfo, before: String?, text: String): Boolean {
        val deadline = SystemClock.uptimeMillis() + 400
        val needle = text.trim().take(24)
        while (SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(50)
            if (!runCatching { node.refresh() }.getOrDefault(false)) return true // field gone: assume it went in
            val now = node.realText()
            if (now != before && (now.contains(needle) || now.length > (before?.length ?: 0))) return true
        }
        return false
    }

    /** Adds a space before the dictation when you're continuing after a word. */
    private fun withSpacing(text: String, node: AccessibilityNodeInfo?, current: String?): String {
        val fromConnection: Char? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                inputMethod?.currentInputConnection?.getSurroundingText(1, 0, 0)?.let { s ->
                    s.text.getOrNull(s.selectionStart - 1)
                }
            }.getOrNull()
        } else null
        val prev = fromConnection ?: run {
            val t = current ?: return text
            val at = node?.textSelectionStart?.takeIf { it in 1..t.length } ?: t.length
            t.getOrNull(at - 1)
        } ?: return text
        return if (prev.isWhitespace() || prev in "([{\"'/") text else " $text"
    }

    /** The field's text, treating a placeholder like "Message" as empty. */
    private fun AccessibilityNodeInfo.realText(): String {
        if (isShowingHintText) return ""
        val t = text?.toString() ?: return ""
        val hint = hintText?.toString()?.trim()
        return if (!hint.isNullOrEmpty() && hint == t.trim()) "" else t
    }

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(ClipboardManager::class.java) ?: return
        main.post { runCatching { cm.setPrimaryClip(ClipData.newPlainText("Murmur", text)) } }
        SystemClock.sleep(60)
    }

    private fun copyOnly(text: String): Boolean {
        copyToClipboard(text)
        return false
    }

    // MARK: - Microphone foreground service (lets us record while another app is in front)

    private fun startMicForeground() {
        if (foreground) return
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Murmur is listening")
            .setOngoing(true)
            .build()
        foreground = runCatching {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        }.isSuccess
    }

    private fun stopMicForeground() {
        if (!foreground) return
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        foreground = false
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "While listening", NotificationManager.IMPORTANCE_LOW))
        }
    }

    // MARK: - Testing

    fun simulateLater(samples: FloatArray, delayMs: Long, onDone: (String, Long) -> Unit) =
        main.postDelayed({ simulate(samples, onDone) }, delayMs)

    /** Runs a WAV file through the full pipeline as if spoken, then types the result (debug builds). */
    fun simulate(samples: FloatArray, onDone: (String, Long) -> Unit) {
        refreshBubble()
        val v = bubble ?: BubbleView(this).also { bubble = it }
        busy = true
        v.setPhase(BubbleView.Phase.RECORDING)
        relayout()
        val s = Session(this, onLevel = { v.pushLevel(it) }, source = Session.WavSource(samples))
        s.start()
        main.postDelayed({
            val released = SystemClock.uptimeMillis()
            v.setPhase(BubbleView.Phase.WORKING)
            relayout()
            s.finish { text ->
                val wait = SystemClock.uptimeMillis() - released
                io.post {
                    insert(text)
                    main.post { idle(); onDone(text, wait) }
                }
            }
        }, samples.size * 1000L / Transcriber.RATE + 50)
    }
}
