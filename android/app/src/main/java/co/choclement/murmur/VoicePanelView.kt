package co.choclement.murmur

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
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
        fun onHide()
        fun onSettings()
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

    private val toolbarH = 44 * d
    private val panelH = 124 * d
    private val keyboardButton = RectF()
    private val settingsButton = RectF()
    private val hideButton = RectF()
    private val pauseButton = RectF()
    private val sendButton = RectF()
    private var pressed: RectF? = null

    private val green = 0xFF1E8E3E.toInt()
    private val red = 0xFFD93025.toInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), panelH.toInt())

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val t = toolbarH / 2
        keyboardButton.set(7 * d, t - 16 * d, 128 * d, t + 16 * d)
        hideButton.set(w - 49 * d, t - 18 * d, w - 9 * d, t + 18 * d)
        settingsButton.set(hideButton.left - 48 * d, hideButton.top, hideButton.left - 8 * d, hideButton.bottom)
        val bottom = h - 12 * d
        val gap = 10 * d
        val side = 12 * d
        val bw = (w - side * 2 - gap) / 2
        pauseButton.set(side, bottom - 60 * d, side + bw, bottom)
        sendButton.set(side + bw + gap, bottom - 60 * d, w - side, bottom)
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    override fun onDraw(c: Canvas) {
        c.drawColor(look.bg)

        // Toolbar: back to the keys on the left, settings and hide on the right.
        val r = keyboardButton.height() / 2
        paint.color = look.fn
        c.drawRoundRect(keyboardButton, r, r, paint)
        look.keyboard(c, keyboardButton.left + 24 * d, keyboardButton.centerY(), look.text)
        text.color = look.text; text.textSize = 14 * d; text.isFakeBoldText = true; text.textAlign = Paint.Align.LEFT
        c.drawText("Keyboard", keyboardButton.left + 43 * d, keyboardButton.centerY() + 5 * d, text)
        text.textAlign = Paint.Align.CENTER; text.isFakeBoldText = false
        look.gear(c, settingsButton.centerX(), settingsButton.centerY(), look.muted)
        look.chevronDown(c, hideButton.centerX(), hideButton.centerY(), look.muted)

        // Pause / Resume is the live voice animation itself: green while listening, red while paused.
        val fill = when (state) {
            State.LISTENING -> green
            State.WORKING -> look.fn
            State.PAUSED, State.NO_MIC -> red
        }
        roundButton(c, pauseButton, fill)
        if (state == State.NO_MIC) {
            text.color = 0xFFFFFFFF.toInt(); text.textSize = 15 * d; text.isFakeBoldText = true
            c.drawText("Allow microphone", pauseButton.centerX(), pauseButton.centerY() + 5 * d, text)
            text.isFakeBoldText = false
        } else {
            drawBars(c, pauseButton, if (state == State.WORKING) look.text else 0xFFFFFFFF.toInt())
        }

        // Send.
        roundButton(c, sendButton, look.accent)
        text.color = look.onAccent; text.textSize = 17 * d; text.isFakeBoldText = true
        val tw = text.measureText("Send")
        val startX = sendButton.centerX() - (tw + 26 * d) / 2
        look.send(c, startX + 9 * d, sendButton.centerY(), look.onAccent)
        text.textAlign = Paint.Align.LEFT
        c.drawText("Send", startX + 26 * d, sendButton.centerY() + 6 * d, text)
        text.textAlign = Paint.Align.CENTER; text.isFakeBoldText = false

        if (state == State.LISTENING || state == State.WORKING) postInvalidateOnAnimation()
    }

    private fun roundButton(c: Canvas, r: RectF, fill: Int) {
        paint.color = fill
        c.drawRoundRect(r, r.height() / 2, r.height() / 2, paint)
        if (pressed === r) { paint.color = look.pressedTint; c.drawRoundRect(r, r.height() / 2, r.height() / 2, paint) }
    }

    /** The waveform: moves with your voice while listening, a soft wave while finishing, still when paused. */
    private fun drawBars(c: Canvas, area: RectF, color: Int) {
        val n = 15
        val gap = 7.5f * d
        val bw = 3.2f * d
        paint.color = color
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = bw
        val t = System.nanoTime() / 1e9
        val mid = (n - 1) / 2f
        for (i in 0 until n) {
            val env = 1 - abs(i - mid) / (mid + 1) * 0.6f
            val h = when (state) {
                State.LISTENING -> {
                    val wobble = 0.55f + 0.45f * sin(t * 9 + i * 1.7).toFloat()
                    val idle = 0.12f + 0.08f * sin(t * 3 + i).toFloat()
                    4 * d + 30 * d * max(idle, level * env * wobble)
                }
                State.WORKING -> {
                    val wave = max(0.0, sin(t * 6 - i * 0.75)).toFloat()
                    4 * d + 14 * d * wave * wave
                }
                else -> 4 * d + 10 * d * env * (0.5f + 0.5f * abs(sin(i * 1.3f)))
            }
            val x = area.centerX() - (n - 1) * gap / 2 + i * gap
            c.drawLine(x, area.centerY() - h / 2 + bw / 2, x, area.centerY() + h / 2 - bw / 2, paint)
        }
        paint.strokeWidth = 0f
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = e.x; val y = e.y
        val target = listOf(pauseButton, sendButton, keyboardButton, settingsButton, hideButton).firstOrNull {
            RectF(it).apply { inset(-6 * d, -6 * d) }.contains(x, y)
        }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = target
                if (target != null && Prefs.haptics) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
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
                    hideButton -> actions.onHide()
                }
            }
            MotionEvent.ACTION_CANCEL -> { pressed = null; invalidate() }
        }
        return true
    }
}
