package co.choclement.murmur

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

/**
 * The typing keyboard, sized and spaced like Gboard (in Murmur's own colors and fonts): QWERTY or Hebrew (the globe key switches)
 * with number hints (long-press), shift and caps lock, two symbol pages, and a toolbar with the
 * voice-mode button.
 */
class KeyboardView(context: Context, private val actions: Actions) : View(context) {

    interface Actions {
        fun onText(text: String)
        fun onDelete()
        fun onSpace()
        fun onEnter()
        fun onVoiceMode()
        fun onSettings()
        fun onPickKeyboard()
        fun onPaste()
        fun onDeleteWord()
        fun onClearAll()
    }

    enum class Page { LETTERS, SYMBOLS, MORE }
    enum class Shift { OFF, ON, LOCK }

    /** A key: [id] is the character it types, or a special name like "shift". */
    private data class Key(val id: String, val w: Float = 1f, val hint: String? = null) {
        val isChar get() = id.length == 1
    }

    private class Placed(val key: Key, val rect: RectF)

    private val look = Look(context)
    private val d = look.d

    var shift = Shift.OFF
        private set
    var page = Page.LETTERS
        private set
    var enter = Look.Enter.RETURN
        set(v) { field = v; invalidate() }
    private val hebrew get() = Prefs.keyboardLanguage == "he"

    // MARK: - Layouts

    // Gboard's bottom row: ?123 · , · globe · space · . · enter.
    private val bottomLetters = listOf(Key("sym", 1.5f), Key(","), Key("lang"), Key("space", 4f), Key("."), Key("enter", 1.5f))
    private val bottomSymbols = listOf(Key("abc", 1.5f), Key(","), Key("lang"), Key("space", 4f), Key("."), Key("enter", 1.5f))

    private val letters = listOf(
        "qwertyuiop".mapIndexed { i, c -> Key(c.toString(), hint = ((i + 1) % 10).toString()) },
        "asdfghjkl".map { Key(it.toString()) },
        listOf(Key("shift", 1.5f)) + "zxcvbnm".map { Key(it.toString()) } + Key("del", 1.5f),
        bottomLetters,
    )
    // The Israeli standard layout, as on Gboard: no shift, so delete moves up to the top row.
    private val hebrewLetters = listOf(
        "קראטוןםפ".mapIndexed { i, c -> Key(c.toString(), hint = (i + 1).toString()) } + Key("del", 2f),
        "שדגכעיחלךף".map { Key(it.toString()) },
        "זסבהנמצתץ".map { Key(it.toString()) },
        bottomLetters,
    )
    private val symbols = listOf(
        "1234567890".map { Key(it.toString()) },
        "@#\$_&-+()/".map { Key(it.toString()) },
        listOf(Key("more", 1.5f)) + "*\"':;!?".map { Key(it.toString()) } + Key("del", 1.5f),
        bottomSymbols,
    )
    private val more = listOf(
        "~`|•√π÷×¶∆".map { Key(it.toString()) },
        "£₪€¥^°={}\\".map { Key(it.toString()) },
        listOf(Key("sym", 1.5f)) + "%©®™✓[]".map { Key(it.toString()) } + Key("del", 1.5f),
        bottomSymbols,
    )

    // MARK: - Geometry

    // Gboard's proportions: 46 dp keys, 6 dp between keys, 12 dp between rows, 4 dp at the sides.
    private val toolbarH = 48 * d
    private val rowH = 58 * d
    private val sidePad = 4 * d
    private val bottomPad = 8 * d
    private val gapX = 3 * d
    private val gapY = 6 * d
    private var placed = listOf<Placed>()
    private val voiceButton = RectF()
    private val settingsButton = RectF()
    private val deleteWordButton = RectF()
    private val pasteButton = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (toolbarH + rowH * 4 + bottomPad).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = layoutKeys()

    private fun layoutKeys() {
        val rows = when (page) { Page.LETTERS -> if (hebrew) hebrewLetters else letters; Page.SYMBOLS -> symbols; Page.MORE -> more }
        val unit = (width - sidePad * 2) / 10f
        val out = ArrayList<Placed>()
        rows.forEachIndexed { r, row ->
            val rowUnits = row.sumOf { it.w.toDouble() }.toFloat()
            var x = sidePad + (10f - rowUnits) * unit / 2 // the 9-key row sits centred, like Gboard
            val top = toolbarH + r * rowH
            for (k in row) {
                out += Placed(k, RectF(x + gapX, top + gapY, x + k.w * unit - gapX, top + rowH - gapY))
                x += k.w * unit
            }
        }
        placed = out
        val t = toolbarH / 2
        val s = 19 * d // half a toolbar button
        voiceButton.set(sidePad + 6 * d, t - s, sidePad + 102 * d, t + s)
        var right = width - sidePad - 6 * d
        for (r in listOf(settingsButton, deleteWordButton, pasteButton)) {
            r.set(right - 2 * s, t - s, right, t + s)
            right -= 2 * s + 10 * d
        }
        invalidate()
    }

    fun setPage(p: Page) {
        page = p
        layoutKeys()
    }

    /** Switches the letters between English and Hebrew, and remembers the choice. */
    fun toggleLanguage() {
        Prefs.keyboardLanguage = if (hebrew) "en" else "he"
        shift = Shift.OFF
        setPage(Page.LETTERS)
    }

    /** Called by the keyboard service when the cursor is at the start of a sentence (or not). */
    fun autoShift(on: Boolean) {
        if (shift == Shift.LOCK || page != Page.LETTERS || hebrew) return
        val next = if (on) Shift.ON else Shift.OFF
        if (next != shift) { shift = next; invalidate() }
    }

    // MARK: - Drawing

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private var pressed: Placed? = null

    override fun onDraw(c: Canvas) {
        c.drawColor(look.bg)
        drawToolbar(c)
        for (p in placed) drawKey(c, p)
        pressed?.let { drawPreview(c, it) }
    }

    private fun drawToolbar(c: Canvas) {
        val r = 14 * d
        look.lift(c, voiceButton, r, look.key, 2f)
        look.mic(c, voiceButton.left + 22 * d, voiceButton.centerY(), look.text, 0.85f)
        label.color = look.text
        label.textSize = 14 * d
        label.typeface = look.body(800)
        label.textAlign = Paint.Align.LEFT
        c.drawText("Voice", voiceButton.left + 38 * d, voiceButton.centerY() + 5 * d, label)
        label.textAlign = Paint.Align.CENTER
        // Like Gboard, the toolbar icons sit flat; a soft circle shows under your finger.
        toolbarDown?.let { look.press(c, it, it.height() / 2, look.bg, 2f) }
        look.clipboard(c, pasteButton.centerX(), pasteButton.centerY(), look.text)
        look.deleteWord(c, deleteWordButton.centerX(), deleteWordButton.centerY(), look.text)
        look.sliders(c, settingsButton.centerX(), settingsButton.centerY(), look.text)
    }


    private fun isFunction(k: Key) = k.id in setOf("shift", "del", "sym", "abc", "more", "lang")

    private fun drawKey(c: Canvas, p: Placed) {
        val k = p.key
        val r = p.rect
        val radius = 7 * d
        val fill = when {
            k.id == "enter" -> look.accent
            isFunction(k) -> look.fn
            else -> look.key
        }
        // Keys sit raised; the one under your finger sinks in.
        if (pressed === p) look.press(c, r, radius, fill, 2f) else look.lift(c, r, radius, fill, 1.5f)

        val cx = r.centerX(); val cy = r.centerY()
        when (k.id) {
            "shift" -> look.shift(c, cx, cy, look.text, shift == Shift.ON, shift == Shift.LOCK)
            "del" -> look.backspace(c, cx, cy, look.text)
            "enter" -> look.enter(c, cx, cy, look.onAccent, enter)
            "lang" -> look.globe(c, cx, cy, look.text)
            "space" -> if (page == Page.LETTERS) {
                // Which language the letters are in, quietly on the space bar like Gboard.
                label.color = look.muted; label.textSize = 13 * d; label.typeface = look.body(700)
                c.drawText(if (hebrew) "עברית" else "English", cx, cy + 4.5f * d, label)
            }
            "sym", "abc", "more" -> {
                label.color = look.text; label.textSize = 15 * d; label.typeface = look.body(800)
                val text = when (k.id) { "sym" -> "?123"; "abc" -> if (hebrew) "אבג" else "ABC"; else -> "=\\<" }
                c.drawText(text, cx, cy + 5 * d, label)
            }
            else -> {
                label.color = look.text
                label.typeface = look.body(700)
                label.textSize = (if (k.id[0].isLetter()) 23 else 21) * d
                val text = if (shift != Shift.OFF && page == Page.LETTERS) k.id.uppercase() else k.id
                c.drawText(text, cx, cy - (label.descent() + label.ascent()) / 2, label)
                k.hint?.let {
                    label.color = look.muted; label.textSize = 10.5f * d; label.typeface = look.body(800)
                    c.drawText(it, r.right - 6 * d, r.top + 12 * d, label)
                }
            }
        }
    }

    /** The enlarged letter that pops up above your finger. */
    private fun drawPreview(c: Canvas, p: Placed) {
        if (!p.key.isChar) return
        val r = p.rect
        val w = r.width() + 10 * d
        val h = rowH * 1.05f
        val box = RectF(r.centerX() - w / 2, r.top - h + 2 * d, r.centerX() + w / 2, r.top + 2 * d)
        look.lift(c, box, 14 * d, look.preview, 5f)
        label.color = look.text
        label.textSize = 30 * d
        label.typeface = look.display(700)
        val text = if (shift != Shift.OFF && page == Page.LETTERS) p.key.id.uppercase() else p.key.id
        c.drawText(text, box.centerX(), box.centerY() - (label.descent() + label.ascent()) / 2, label)
    }

    // MARK: - Touch

    private val main = Handler(Looper.getMainLooper())
    private var activePointer = -1
    private var consumed = false     // a long-press already handled this touch
    private var lastShiftTap = 0L
    private var lastSpaceAt = 0L

    private val longPress = Runnable {
        val p = pressed ?: return@Runnable
        when {
            p.key.id == "del" -> { consumed = true; repeatDelete.run() }
            p.key.id == "space" -> { consumed = true; actions.onPickKeyboard(); pressed = null; invalidate() }
            p.key.hint != null -> { consumed = true; actions.onText(p.key.hint); buzz(); pressed = null; invalidate() }
        }
    }

    private val repeatDelete: Runnable = object : Runnable {
        override fun run() {
            if (pressed?.key?.id != "del") return
            actions.onDelete()
            main.postDelayed(this, 55)
        }
    }

    private fun keyAt(x: Float, y: Float): Placed? {
        // Generous hit areas: nearest key in the row, so the gaps between keys still count.
        if (y < toolbarH) return null
        val row = placed.filter { y >= it.rect.top - gapY && y <= it.rect.bottom + gapY }
        return row.minByOrNull { if (x < it.rect.left) it.rect.left - x else if (x > it.rect.right) x - it.rect.right else 0f }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val x = e.getX(i); val y = e.getY(i)
                if (y < toolbarH) {
                    if (e.actionMasked == MotionEvent.ACTION_DOWN) toolbarPress(x, y)
                    return true
                }
                // Fast typing: a second finger landing commits the first key straight away.
                pressed?.let { if (!consumed) fire(it) }
                cancelTimers()
                activePointer = e.getPointerId(i)
                press(keyAt(x, y))
            }
            MotionEvent.ACTION_MOVE -> {
                val i = e.findPointerIndex(activePointer)
                if (i < 0 || consumed) return true
                val over = keyAt(e.getX(i), e.getY(i))
                if (over != null && over !== pressed && pressed?.key?.id != "del") {
                    cancelTimers()
                    press(over, slide = true)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (toolbarDown != null && e.actionMasked == MotionEvent.ACTION_UP) return toolbarRelease(e.x, e.y)
                if (e.getPointerId(e.actionIndex) != activePointer) return true
                cancelTimers()
                pressed?.let { if (!consumed && it.key.id != "del") fire(it) }
                release()
            }
            MotionEvent.ACTION_CANCEL -> { cancelTimers(); release(); toolbarDown = null }
        }
        return true
    }

    private fun press(p: Placed?, slide: Boolean = false) {
        pressed = p
        consumed = false
        invalidate()
        p ?: return
        if (!slide) buzz()
        when {
            p.key.id == "del" -> { actions.onDelete(); main.postDelayed(longPress, 400) }
            p.key.id == "space" || p.key.hint != null -> main.postDelayed(longPress, 380)
        }
    }

    private fun release() {
        pressed = null
        activePointer = -1
        consumed = false
        invalidate()
    }

    private fun cancelTimers() {
        main.removeCallbacks(longPress)
        main.removeCallbacks(repeatDelete)
    }

    // Toolbar buttons act when you lift your finger; holding delete-word clears the whole text box.
    private var toolbarDown: RectF? = null
    private var cleared = false
    private val clearAll = Runnable {
        if (toolbarDown !== deleteWordButton) return@Runnable
        cleared = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        actions.onClearAll()
    }

    private fun toolbarPress(x: Float, y: Float) {
        val hit = listOf(voiceButton, pasteButton, deleteWordButton, settingsButton)
            .firstOrNull { RectF(it).apply { inset(-6 * d, -6 * d) }.contains(x, y) } ?: return
        buzz()
        toolbarDown = hit
        cleared = false
        if (hit === deleteWordButton) main.postDelayed(clearAll, 500)
        invalidate()
    }

    private fun toolbarRelease(x: Float, y: Float): Boolean {
        val hit = toolbarDown
        toolbarDown = null
        main.removeCallbacks(clearAll)
        invalidate()
        if (hit == null || !RectF(hit).apply { inset(-12 * d, -12 * d) }.contains(x, y)) return true
        when (hit) {
            voiceButton -> actions.onVoiceMode()
            settingsButton -> actions.onSettings()
            deleteWordButton -> if (!cleared) actions.onDeleteWord()
            pasteButton -> actions.onPaste()
        }
        return true
    }

    private fun fire(p: Placed) {
        val k = p.key
        when (k.id) {
            "shift" -> {
                val now = SystemClock.uptimeMillis()
                shift = when {
                    now - lastShiftTap < 300 -> Shift.LOCK
                    shift == Shift.OFF -> Shift.ON
                    else -> Shift.OFF
                }
                lastShiftTap = now
            }
            "sym" -> setPage(Page.SYMBOLS)
            "abc" -> setPage(Page.LETTERS)
            "more" -> setPage(Page.MORE)
            "lang" -> toggleLanguage()
            "enter" -> actions.onEnter()
            "space" -> {
                actions.onSpace()
                lastSpaceAt = SystemClock.uptimeMillis()
            }
            else -> {
                val text = if (shift != Shift.OFF && page == Page.LETTERS) k.id.uppercase() else k.id
                actions.onText(text)
                if (shift == Shift.ON) shift = Shift.OFF
            }
        }
        invalidate()
    }

    /** Was the previous key a space typed just now? (Double-space types a period, like Gboard.) */
    fun spaceJustTyped() = SystemClock.uptimeMillis() - lastSpaceAt < 700

    private fun buzz() {
        if (Prefs.haptics) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
}
