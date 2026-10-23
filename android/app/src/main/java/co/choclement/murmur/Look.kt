package co.choclement.murmur

import android.content.Context
import android.content.res.Configuration
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface

/**
 * The Pebble look: soft sage surfaces that are raised (buttons, cards) or pressed in (fields,
 * selected options), Bricolage Grotesque for titles and Nunito for text. Colors, fonts, the
 * soft-shadow drawing, and the hand-drawn icons all live here.
 */
class Look(val context: Context) {
    val d = context.resources.displayMetrics.density
    val dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    val bg = if (dark) Color.rgb(0x15, 0x1A, 0x16) else Color.rgb(0xE6, 0xEB, 0xE3)
    val key = if (dark) Color.rgb(0x1E, 0x25, 0x20) else Color.rgb(0xF4, 0xF6, 0xF1)   // raised surface
    val fn = if (dark) Color.rgb(0x2A, 0x36, 0x2D) else Color.rgb(0xDC, 0xE8, 0xD9)    // function keys, chips
    val accent = Color.rgb(0x3E, 0x6B, 0x48)                                             // moss green
    val accentDeep = Color.rgb(0x2C, 0x4F, 0x34)
    val onAccent = Color.WHITE
    val clay = Color.rgb(0xB4, 0x47, 0x2F)                                               // paused
    val apricot = Color.rgb(0xF2, 0xA6, 0x5A)                                            // Send
    val peach = if (dark) Color.rgb(0x3A, 0x2E, 0x22) else Color.rgb(0xF7, 0xE3, 0xCC)  // pinned, AI cleanup
    val text = if (dark) Color.rgb(0xE8, 0xED, 0xE6) else Color.rgb(0x1F, 0x2A, 0x22)
    val muted = if (dark) Color.rgb(0xA4, 0xB0, 0xA6) else Color.rgb(0x56, 0x63, 0x5A)
    val pressedTint = if (dark) Color.argb(50, 255, 255, 255) else Color.argb(28, 31, 42, 34)
    val preview = key
    private val shade = if (dark) Color.argb(115, 0, 0, 0) else Color.argb(36, 31, 42, 34)
    private val light = if (dark) Color.argb(10, 255, 255, 255) else Color.WHITE

    /** Bricolage Grotesque, for titles and big labels. */
    fun display(weight: Int = 800) = font("bricolage.ttf", weight)

    /** Nunito, for everything else. */
    fun body(weight: Int = 700) = font("nunito.ttf", weight)

    private fun font(file: String, weight: Int) = fonts.getOrPut("$file$weight") {
        runCatching {
            Typeface.Builder(context.assets, "fonts/$file").setFontVariationSettings("'wght' $weight").build()
        }.getOrDefault(Typeface.DEFAULT_BOLD)
    }

    private val soft = Paint(Paint.ANTI_ALIAS_FLAG)

    /** A raised shape: soft shadow below-right, a highlight above-left. [depth] in dp. */
    fun lift(c: Canvas, r: RectF, radius: Float, fill: Int, depth: Float = 4f) {
        val off = depth * d * 0.8f
        soft.style = Paint.Style.FILL
        soft.color = fill
        soft.setShadowLayer(depth * 2 * d, -off, -off, light)
        c.drawRoundRect(r, radius, radius, soft)
        soft.setShadowLayer(depth * 2 * d, off, off, shade)
        c.drawRoundRect(r, radius, radius, soft)
        soft.clearShadowLayer()
    }

    /** A pressed-in shape: inner shadow along the top-left, inner highlight along the bottom-right. */
    fun press(c: Canvas, r: RectF, radius: Float, fill: Int, depth: Float = 3f) {
        soft.style = Paint.Style.FILL
        soft.color = fill
        c.drawRoundRect(r, radius, radius, soft)
        val off = depth * d
        c.save()
        c.clipPath(Path().apply { addRoundRect(r, radius, radius, Path.Direction.CW) })
        soft.style = Paint.Style.STROKE
        soft.strokeWidth = off * 2.5f
        soft.maskFilter = BlurMaskFilter(off * 1.6f, BlurMaskFilter.Blur.NORMAL)
        for ((color, dx) in listOf(shade to off, light to -off)) {
            soft.color = color
            c.drawRoundRect(RectF(r).apply { offset(dx, dx); inset(-off, -off) }, radius + off, radius + off, soft)
        }
        soft.maskFilter = null
        c.restore()
    }

    companion object {
        private val fonts = HashMap<String, Typeface>()
    }

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

    /** Clipboard: a board with a clip on top and two lines of "text". */
    fun clipboard(c: Canvas, cx: Float, cy: Float, color: Int) {
        val w = 7.5f * d; val h = 9.5f * d
        c.drawRoundRect(RectF(cx - w, cy - h, cx + w, cy + h), 2 * d, 2 * d, pen(color, 1.8f))
        c.drawRoundRect(RectF(cx - w * 0.45f, cy - h - 2 * d, cx + w * 0.45f, cy - h + 3 * d), 1.5f * d, 1.5f * d, brush(color))
        c.drawLine(cx - w * 0.5f, cy - h * 0.1f, cx + w * 0.5f, cy - h * 0.1f, pen(color, 1.6f))
        c.drawLine(cx - w * 0.5f, cy + h * 0.4f, cx + w * 0.2f, cy + h * 0.4f, pen(color, 1.6f))
    }

    /** Delete a whole word: the backspace key with a "W" inside instead of the ×. */
    fun deleteWord(c: Canvas, cx: Float, cy: Float, color: Int) {
        val w = 12 * d; val h = 8.5f * d
        val p = Path().apply {
            moveTo(cx - w, cy); lineTo(cx - w * 0.45f, cy - h); lineTo(cx + w, cy - h)
            lineTo(cx + w, cy + h); lineTo(cx - w * 0.45f, cy + h); close()
        }
        c.drawPath(p, pen(color, 1.8f))
        val x = cx + w * 0.22f; val k = 4.2f * d; val t = 3.6f * d
        c.drawPath(Path().apply {
            moveTo(x - k, cy - t); lineTo(x - k * 0.5f, cy + t); lineTo(x, cy - t * 0.2f)
            lineTo(x + k * 0.5f, cy + t); lineTo(x + k, cy - t)
        }, pen(color, 1.6f))
    }

    /** The language-switch globe: a circle with a meridian and an equator. */
    fun globe(c: Canvas, cx: Float, cy: Float, color: Int) {
        val r = 8.5f * d
        val p = pen(color, 1.6f)
        c.drawCircle(cx, cy, r, p)
        c.drawOval(RectF(cx - r * 0.42f, cy - r, cx + r * 0.42f, cy + r), p)
        c.drawLine(cx - r, cy, cx + r, cy, p)
        c.drawLine(cx - r * 0.85f, cy - r * 0.5f, cx + r * 0.85f, cy - r * 0.5f, p)
        c.drawLine(cx - r * 0.85f, cy + r * 0.5f, cx + r * 0.85f, cy + r * 0.5f, p)
    }

    /** Settings, Pebble style: two sliders with knobs. */
    fun sliders(c: Canvas, cx: Float, cy: Float, color: Int) {
        val s = d * 0.85f
        fun x(v: Float) = cx + (v - 12) * s
        fun y(v: Float) = cy + (v - 12) * s
        val p = pen(color, 2f)
        c.drawLine(x(4f), y(7f), x(14f), y(7f), p); c.drawLine(x(18f), y(7f), x(20f), y(7f), p)
        c.drawLine(x(4f), y(17f), x(8f), y(17f), p); c.drawLine(x(12f), y(17f), x(20f), y(17f), p)
        c.drawCircle(x(16f), y(7f), 2 * s, p); c.drawCircle(x(10f), y(17f), 2 * s, p)
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
