package co.choclement.murmur

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.OvershootInterpolator
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/**
 * The floating Murmur button, in the Pebble look: a soft moss-green circle with a waveform when
 * idle; it stretches into a pill with live bars while you talk (clay red when you're about to
 * cancel), and a gentle travelling wave, in a deeper green, while your words are transcribed.
 */
class BubbleView(context: Context) : View(context) {
    enum class Phase { IDLE, RECORDING, WORKING, NOTICE }

    private val look = Look(context)
    private val d = look.d
    private val idleSize = 48 * d
    private val pillWidth = 132 * d
    private val noticePad = 18 * d

    var phase = Phase.IDLE
        private set
    var notice = ""
        private set
    var cancelArmed = false
        set(v) { field = v; invalidate() }

    private var level = 0f
    private var width01 = 0f // 0 = circle, 1 = pill
    private var animator: ValueAnimator? = null
    private var scale = 1f

    private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeCap = Paint.Cap.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 13 * d; typeface = look.body(800); textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()

    /** Width the window should have for the current phase. */
    val desiredWidth: Int
        get() = when (phase) {
            Phase.NOTICE -> (text.measureText(notice) + noticePad * 2).toInt()
            Phase.RECORDING, Phase.WORKING -> pillWidth.toInt()
            Phase.IDLE -> idleSize.toInt()
        } + (16 * d).toInt()
    val desiredHeight get() = (idleSize + 16 * d).toInt()

    fun setPhase(p: Phase, message: String = "") {
        if (p == phase && message == notice) return
        phase = p
        notice = message
        if (p == Phase.IDLE) level = 0f
        val target = if (p == Phase.IDLE) 0f else 1f
        animator?.cancel()
        animator = ValueAnimator.ofFloat(width01, target).apply {
            duration = 260
            interpolator = OvershootInterpolator(1.4f)
            addUpdateListener { width01 = it.animatedValue as Float; invalidate() }
            start()
        }
        requestLayout()
        postInvalidateOnAnimation()
    }

    fun pushLevel(raw: Float) {
        level += (raw - level) * if (raw > level) 0.55f else 0.25f
    }

    fun pressed(down: Boolean) {
        scale = if (down) 0.92f else 1f
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
        setMeasuredDimension(desiredWidth, desiredHeight)

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val full = when (phase) {
            Phase.NOTICE -> text.measureText(notice) + noticePad * 2
            else -> pillWidth
        }
        val w = idleSize + (full - idleSize) * width01
        val h = idleSize
        canvas.save()
        canvas.scale(scale, scale, cx, cy)
        rect.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
        val fill = when {
            cancelArmed -> look.clay
            phase == Phase.WORKING -> look.accentDeep
            else -> look.accent
        }
        look.lift(canvas, rect, h / 2, fill, 2f)

        val t = System.nanoTime() / 1e9
        when (phase) {
            Phase.NOTICE -> canvas.drawText(notice, cx, cy - (text.descent() + text.ascent()) / 2, text)
            Phase.IDLE -> drawBars(canvas, cx, cy, 5, 3.2f * d, 4.2f * d) { i -> listOf(0.35f, 0.7f, 1f, 0.7f, 0.35f)[i] * 16 * d }
            Phase.RECORDING -> {
                val n = 11
                val mid = (n - 1) / 2f
                drawBars(canvas, cx, cy, n, 3 * d, 6.5f * d) { i ->
                    val envelope = 1 - abs(i - mid) / (mid + 1) * 0.65f
                    val wobble = 0.55f + 0.45f * sin(t * 9 + i * 1.7).toFloat()
                    val idle = 0.12f + 0.08f * sin(t * 3 + i).toFloat()
                    4 * d + 22 * d * max(idle, level * envelope * wobble)
                }
            }
            Phase.WORKING -> drawBars(canvas, cx, cy, 11, 3 * d, 6.5f * d) { i ->
                val wave = max(0.0, sin(t * 6 - i * 0.75)).toFloat()
                4 * d + 11 * d * wave * wave
            }
        }
        canvas.restore()
        if (phase == Phase.RECORDING || phase == Phase.WORKING || animator?.isRunning == true) postInvalidateOnAnimation()
    }

    private inline fun drawBars(c: Canvas, cx: Float, cy: Float, n: Int, w: Float, gap: Float, height: (Int) -> Float) {
        bar.strokeWidth = w
        val total = (n - 1) * gap
        for (i in 0 until n) {
            val x = cx - total / 2 + i * gap
            val h = height(i)
            c.drawLine(x, cy - h / 2 + w / 2, x, cy + h / 2 - w / 2, bar)
        }
    }
}
