package co.choclement.murmur

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/** Colors and hand-drawn icons for the Murmur keyboard, in the style of Gboard. */
class Look(context: Context) {
    val d = context.resources.displayMetrics.density
    val dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    val bg = if (dark) Color.rgb(27, 27, 31) else Color.rgb(239, 241, 246)
    val key = if (dark) Color.rgb(58, 59, 63) else Color.WHITE
    val fn = if (dark) Color.rgb(46, 56, 74) else Color.rgb(214, 226, 247)
    val accent = Color.rgb(168, 199, 250)
    val onAccent = Color.rgb(4, 30, 73)
    val text = if (dark) Color.rgb(232, 234, 237) else Color.rgb(31, 31, 31)
    val muted = if (dark) Color.rgb(154, 160, 166) else Color.rgb(95, 99, 104)
    val pressedTint = if (dark) Color.argb(60, 255, 255, 255) else Color.argb(40, 0, 0, 0)
    val preview = if (dark) Color.rgb(80, 82, 88) else Color.WHITE

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private fun pen(color: Int, width: Float = 2f) = stroke.apply { this.color = color; strokeWidth = width * d }
    private fun brush(color: Int) = fill.apply { this.color = color }

    /** Shift arrow; filled when on, filled + underline when caps lock. */
    fun shift(c: Canvas, cx: Float, cy: Float, color: Int, on: Boolean, lock: Boolean) {
        val s = 9 * d
        val p = Path().apply {
            moveTo(cx, cy - s); lineTo(cx + s, cy); lineTo(cx + s * 0.45f, cy); lineTo(cx + s * 0.45f, cy + s * 0.8f)
            lineTo(cx - s * 0.45f, cy + s * 0.8f); lineTo(cx - s * 0.45f, cy); lineTo(cx - s, cy); close()
        }
        if (on || lock) c.drawPath(p, brush(color)) else c.drawPath(p, pen(color, 1.8f))
        if (lock) c.drawLine(cx - s * 0.5f, cy + s * 1.25f, cx + s * 0.5f, cy + s * 1.25f, pen(color, 2f))
    }

    fun backspace(c: Canvas, cx: Float, cy: Float, color: Int) {
        val w = 11 * d; val h = 8 * d
        val p = Path().apply {
            moveTo(cx - w, cy); lineTo(cx - w * 0.45f, cy - h); lineTo(cx + w, cy - h)
            lineTo(cx + w, cy + h); lineTo(cx - w * 0.45f, cy + h); close()
        }
        c.drawPath(p, pen(color, 1.8f))
        val x = cx + w * 0.28f; val k = 3.2f * d
        c.drawLine(x - k, cy - k, x + k, cy + k, pen(color, 1.8f))
        c.drawLine(x - k, cy + k, x + k, cy - k, pen(color, 1.8f))
    }

    enum class Enter { RETURN, SEARCH, SEND, GO, NEXT, DONE }

    fun enter(c: Canvas, cx: Float, cy: Float, color: Int, kind: Enter) {
        val s = 8 * d
        when (kind) {
            Enter.RETURN -> {
                c.drawPath(Path().apply { moveTo(cx + s, cy - s); lineTo(cx + s, cy + s * 0.2f); lineTo(cx - s, cy + s * 0.2f) }, pen(color))
                c.drawPath(Path().apply { moveTo(cx - s * 0.45f, cy - s * 0.35f); lineTo(cx - s, cy + s * 0.2f); lineTo(cx - s * 0.45f, cy + s * 0.75f) }, pen(color))
            }
            Enter.SEARCH -> {
                c.drawCircle(cx - s * 0.2f, cy - s * 0.2f, s * 0.65f, pen(color))
                c.drawLine(cx + s * 0.28f, cy + s * 0.28f, cx + s, cy + s, pen(color, 2.4f))
            }
            Enter.SEND -> send(c, cx, cy, color)
            Enter.GO, Enter.NEXT -> {
                c.drawLine(cx - s, cy, cx + s, cy, pen(color))
                c.drawPath(Path().apply { moveTo(cx + s * 0.3f, cy - s * 0.7f); lineTo(cx + s, cy); lineTo(cx + s * 0.3f, cy + s * 0.7f) }, pen(color))
            }
            Enter.DONE -> c.drawPath(Path().apply { moveTo(cx - s, cy); lineTo(cx - s * 0.25f, cy + s * 0.75f); lineTo(cx + s, cy - s * 0.7f) }, pen(color, 2.2f))
        }
    }

    fun send(c: Canvas, cx: Float, cy: Float, color: Int, size: Float = 9f) {
        val s = size * d
        val p = Path().apply {
            moveTo(cx - s, cy - s * 0.9f); lineTo(cx + s, cy); lineTo(cx - s, cy + s * 0.9f)
            lineTo(cx - s * 0.6f, cy); close()
        }
        c.drawPath(p, brush(color))
    }

    fun mic(c: Canvas, cx: Float, cy: Float, color: Int, size: Float = 1f) {
        val w = 3.6f * d * size; val h = 6.5f * d * size
        c.drawRoundRect(RectF(cx - w, cy - h * 1.15f, cx + w, cy + h * 0.35f), w, w, brush(color))
        c.drawArc(RectF(cx - w * 1.9f, cy - h * 0.9f, cx + w * 1.9f, cy + h * 0.8f), 0f, 180f, false, pen(color, 1.8f * size))
        c.drawLine(cx, cy + h * 0.8f, cx, cy + h * 1.3f, pen(color, 1.8f * size))
    }

    fun keyboard(c: Canvas, cx: Float, cy: Float, color: Int) {
        val w = 11 * d; val h = 7.5f * d
        c.drawRoundRect(RectF(cx - w, cy - h, cx + w, cy + h), 2.5f * d, 2.5f * d, pen(color, 1.8f))
        val dot = brush(color)
        for (row in 0..1) for (i in 0..4) c.drawCircle(cx - w * 0.64f + i * w * 0.32f, cy - h * 0.4f + row * h * 0.55f, 1.2f * d, dot)
        c.drawLine(cx - w * 0.45f, cy + h * 0.55f, cx + w * 0.45f, cy + h * 0.55f, pen(color, 1.8f))
    }

    fun gear(c: Canvas, cx: Float, cy: Float, color: Int) {
        val r = 7.5f * d
        val p = pen(color, 1.8f)
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            c.drawLine(cx + (r * 0.8f * Math.cos(a)).toFloat(), cy + (r * 0.8f * Math.sin(a)).toFloat(),
                cx + (r * 1.15f * Math.cos(a)).toFloat(), cy + (r * 1.15f * Math.sin(a)).toFloat(), pen(color, 2.6f))
        }
        c.drawCircle(cx, cy, r * 0.75f, p)
        c.drawCircle(cx, cy, r * 0.3f, pen(color, 1.8f))
    }

    fun chevronDown(c: Canvas, cx: Float, cy: Float, color: Int) {
        val s = 7 * d
        c.drawPath(Path().apply { moveTo(cx - s, cy - s * 0.4f); lineTo(cx, cy + s * 0.5f); lineTo(cx + s, cy - s * 0.4f) }, pen(color, 2f))
    }

    fun pause(c: Canvas, cx: Float, cy: Float, color: Int) {
        val w = 2.4f * d; val h = 8 * d; val gap = 3.2f * d
        c.drawRoundRect(RectF(cx - gap - w * 2, cy - h, cx - gap, cy + h), w, w, brush(color))
        c.drawRoundRect(RectF(cx + gap, cy - h, cx + gap + w * 2, cy + h), w, w, brush(color))
    }

    fun play(c: Canvas, cx: Float, cy: Float, color: Int) {
        val s = 8 * d
        c.drawPath(Path().apply { moveTo(cx - s * 0.6f, cy - s); lineTo(cx + s, cy); lineTo(cx - s * 0.6f, cy + s); close() }, brush(color))
    }
}
