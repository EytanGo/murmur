package co.choclement.murmur

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.sin

/**
 * Voice mode: shown instead of the keys. Listens as soon as a text box is tapped, with
 * Pause / Resume and Send. Your words appear in the text box itself as you speak.
 */
class VoicePanelView(context: Context, private val actions: Actions) : View(context) {

    interface Actions {
        fun onPauseResume()
        fun onSend()
        fun onTypingMode()
        fun onSettings()
        fun onPaste()
        fun onDeleteWord()
        fun onClearAll()
    }

    enum class State { LISTENING, PAUSED, WORKING, NO_MIC }

    private val look = Look(context)
    private val d = look.d

    var state = State.PAUSED
        set(v) { field = v; if (v != State.LISTENING) level = 0f; invalidate() }

    private var level = 0f
    fun pushLevel(raw: Float) {
        level += (raw - level) * if (raw > level) 0.55f else 0.25f
    }

    private val pad = 16 * d
    private val toolH = 46 * d
    private val bigH = 96 * d
    private val panelH = 14 * d + toolH + 16 * d + bigH + 22 * d
    private val keyboardButton = RectF()
    private val settingsButton = RectF()
    private val deleteWordButton = RectF()
    private val pasteButton = RectF()
    private val pauseButton = RectF()
    private val sendButton = RectF()
    private var pressed: RectF? = null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), panelH.toInt())

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val top = 14 * d
        keyboardButton.set(pad, top, pad + toolH, top + toolH)
        var right = w - pad
        for (r in listOf(settingsButton, deleteWordButton, pasteButton)) {
            r.set(right - toolH, top, right, top + toolH)
            right -= toolH + 8 * d
        }
        val bigTop = top + toolH + 16 * d
        val bw = (w - pad * 2 - 12 * d) / 2
        pauseButton.set(pad, bigTop, pad + bw, bigTop + bigH)
        sendButton.set(pad + bw + 12 * d, bigTop, w - pad, bigTop + bigH)
    }

    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; color = 0xFFFFFFFF.toInt() }

    override fun onDraw(c: Canvas) {
        c.drawColor(look.key)
        val t = System.nanoTime() / 1e9

        // Toolbar: soft raised squircles on the panel.
        tool(c, keyboardButton); look.keyboard(c, keyboardButton.centerX(), keyboardButton.centerY(), look.text)
        tool(c, pasteButton); look.clipboard(c, pasteButton.centerX(), pasteButton.centerY(), look.text)
        tool(c, deleteWordButton); look.deleteWord(c, deleteWordButton.centerX(), deleteWordButton.centerY(), look.text)
        tool(c, settingsButton); look.sliders(c, settingsButton.centerX(), settingsButton.centerY(), look.text)

        // The listen button: moss green and gently breathing while listening, clay when paused.
        val fill = when (state) {
            State.LISTENING -> look.accent
            State.WORKING -> look.accentDeep
            State.PAUSED, State.NO_MIC -> look.clay
        }
        val breathe = if (state == State.LISTENING) 1f + 0.015f * (1 + sin(t * 2 * Math.PI / 2.4).toFloat()) else 1f
        c.save()
        c.scale(breathe, breathe, pauseButton.centerX(), pauseButton.centerY())
        big(c, pauseButton, fill)
        if (state == State.NO_MIC) {
            label(c, pauseButton, "Allow microphone", pauseButton.centerY() + 5 * d)
        } else {
            drawBars(c, pauseButton, t)
            label(c, pauseButton, when (state) {
                State.LISTENING -> "Listening · tap to pause"
                State.WORKING -> "Finishing…"
                else -> "Paused · tap to resume"
            }, pauseButton.centerY() + 30 * d)
        }
        c.restore()

        // Send: apricot.
        big(c, sendButton, look.apricot)
        val ink = 0xFF1F2A22.toInt()
        text.color = ink; text.textSize = 19 * d; text.typeface = look.display(800); text.textAlign = Paint.Align.LEFT
        val tw = text.measureText("Send")
        val startX = sendButton.centerX() - (tw + 32 * d) / 2
        look.send(c, startX + 10 * d, sendButton.centerY(), ink, 10f)
        c.drawText("Send", startX + 32 * d, sendButton.centerY() + 7 * d, text)
        text.textAlign = Paint.Align.CENTER

        if (state == State.LISTENING || state == State.WORKING) postInvalidateOnAnimation()
    }

    private fun tool(c: Canvas, r: RectF) {
        look.lift(c, r, 16 * d, look.bg, 3f)
        if (pressed === r) { c.drawRoundRect(r, 16 * d, 16 * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = look.pressedTint }) }
    }

    private fun big(c: Canvas, r: RectF, fill: Int) {
        if (pressed === r) look.press(c, r, 30 * d, fill, 4f) else look.lift(c, r, 30 * d, fill, 6f)
    }

    private fun label(c: Canvas, r: RectF, s: String, y: Float) {
        text.color = 0xFFFFFFFF.toInt(); text.textSize = 13 * d; text.typeface = look.body(800)
        c.drawText(s, r.centerX(), y, text)
    }

    /** Nine soft bars: they ride your voice while listening, ripple while finishing, rest when paused. */
    private fun drawBars(c: Canvas, area: RectF, t: Double) {
        val n = 9
        val gap = 11 * d
        bar.strokeWidth = 6 * d
        bar.alpha = if (state == State.PAUSED) 205 else 255
        val cy = area.centerY() - 10 * d
        for (i in 0 until n) {
            val phase = (t / 1.2 + i * 0.19 / 1.2) * 2 * Math.PI
            val wave = (0.5 + 0.5 * sin(phase)).toFloat()
            val h = when (state) {
                State.LISTENING -> 6 * d + 24 * d * max(wave * 0.35f, level * (0.45f + 0.55f * wave))
                State.WORKING -> 6 * d + 14 * d * wave * wave
                else -> 6 * d
            }
            val x = area.centerX() - (n - 1) * gap / 2 + i * gap
            c.drawLine(x, cy - h / 2 + 3 * d, x, cy + h / 2 - 3 * d, bar)
        }
    }

    /** Holding delete-word clears the whole text box. */
    private var cleared = false
    private val clearAll = Runnable {
        if (pressed !== deleteWordButton) return@Runnable
        cleared = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        actions.onClearAll()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = e.x; val y = e.y
        val target = listOf(pauseButton, sendButton, keyboardButton, settingsButton, deleteWordButton, pasteButton).firstOrNull {
            RectF(it).apply { inset(-6 * d, -6 * d) }.contains(x, y)
        }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = target
                if (target != null && Prefs.haptics) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                // Delete-word: a tap deletes a word (on release), holding clears everything.
                if (target === deleteWordButton) { cleared = false; postDelayed(clearAll, 500) }
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val hit = pressed?.takeIf { it === target }
                pressed = null
                invalidate()
                when (hit) {
                    pauseButton -> actions.onPauseResume()
                    sendButton -> actions.onSend()
                    keyboardButton -> actions.onTypingMode()
                    settingsButton -> actions.onSettings()
                    pasteButton -> actions.onPaste()
                    deleteWordButton -> if (!cleared) actions.onDeleteWord()
                }
                removeCallbacks(clearAll)
            }
            MotionEvent.ACTION_CANCEL -> { pressed = null; removeCallbacks(clearAll); invalidate() }
        }
        return true
    }
}
