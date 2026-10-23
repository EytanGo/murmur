package co.choclement.murmur

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Building blocks for Pebble screens made of ordinary views (settings, clipboard): soft cards,
 * segmented controls, chips, buttons, switches and selectable options.
 */
class Pebble(val context: Context) {
    val look = Look(context)
    private val d = look.d

    enum class Depth { LIFT, PRESS, FLAT }

    /**
     * A rounded shape drawn raised or pressed in. The shape is inset by [margin] dp so its soft
     * shadow stays inside the view (views can't draw outside their bounds).
     */
    inner class Soft(private val fill: Int, private val radiusDp: Float, private val depth: Depth, private val margin: Float = 6f) : Drawable() {
        override fun draw(c: Canvas) {
            val m = margin * d
            val r = RectF(bounds).apply { inset(m, m) }
            when (depth) {
                Depth.LIFT -> look.lift(c, r, radiusDp * d, fill, 4f)
                Depth.PRESS -> look.press(c, r, radiusDp * d, fill, 3f)
                Depth.FLAT -> c.drawRoundRect(r, radiusDp * d, radiusDp * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill })
            }
        }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    fun px(dp: Float) = (dp * d).toInt()

    fun text(s: String, size: Float, color: Int = look.text, weight: Int = 600, display: Boolean = false) = TextView(context).apply {
        text = s
        textSize = size
        setTextColor(color)
        typeface = if (display) look.display(weight) else look.body(weight)
    }

    /** A soft raised card holding a column of content. */
    fun card(fill: Int = look.key, content: LinearLayout.() -> Unit) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val m = 8f
        background = Soft(fill, 28f, Depth.LIFT, m)
        setPadding(px(18 + m), px(16 + m), px(18 + m), px(16 + m))
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = px(4f) }
        content()
    }

    /** A card title, with an optional little pressed-in icon tile in front. */
    fun heading(title: String, icon: ((Canvas, Float, Float, Int) -> Unit)? = null) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 0, 0, px(8f))
        if (icon != null) {
            addView(object : View(context) {
                override fun onDraw(c: Canvas) {
                    look.press(c, RectF(0f, 0f, width.toFloat(), height.toFloat()), 12 * d, look.bg, 2f)
                    icon(c, width / 2f, height / 2f, look.accent)
                }
            }, LinearLayout.LayoutParams(px(34f), px(34f)).apply { marginEnd = px(10f) })
        }
        addView(text(title, 17f, weight = 700, display = true))
    }

    /** Two or more choices in a pressed-in tray; the chosen one is raised. */
    fun segmented(options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit): LinearLayout {
        val tray = LinearLayout(context)
        tray.orientation = LinearLayout.HORIZONTAL
        tray.background = Soft(look.bg, 18f, Depth.PRESS, 0f)
        tray.setPadding(px(5f), px(5f), px(5f), px(5f))
        var current = selected
        fun paint() {
            for (i in 0 until tray.childCount) {
                val v = tray.getChildAt(i) as TextView
                val on = v.tag == current
                v.background = if (on) Soft(look.key, 14f, Depth.LIFT, 2f) else null
                v.setTextColor(if (on) look.text else look.muted)
            }
        }
        for ((id, label) in options) {
            tray.addView(text(label, 14f, weight = 700).apply {
                tag = id
                gravity = Gravity.CENTER
                minHeight = px(44f)
                setPadding(px(6f), 0, px(6f), 0)
                setOnClickListener {
                    if (current != id) {
                        current = id
                        tap(this)
                        paint()
                        onPick(id)
                    }
                }
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }
        paint()
        return tray
    }

    /** A small rounded status label ("Yes", "Allowed", "105 MB"). */
    fun chip(s: String, fill: Int = look.fn, color: Int = look.accentDeep) = text(s, 12f, color, 700).apply {
        setPadding(px(12f), px(6f), px(12f), px(6f))
        background = Soft(fill, 999f, Depth.FLAT, 0f)
    }

    /** A button: moss green when [primary], otherwise soft and raised. */
    fun button(s: String, primary: Boolean = false, onClick: () -> Unit) = text(s, 15f, if (primary) look.onAccent else look.text, 800).apply {
        gravity = Gravity.CENTER
        minHeight = px(56f)
        setPadding(px(24f), px(6f), px(24f), px(6f))
        background = Soft(if (primary) look.accent else look.key, 16f, Depth.LIFT, 5f)
        setOnClickListener { tap(this); onClick() }
    }

    /** A text field, pressed into the card. */
    fun field(hint: String, password: Boolean = false, multiLine: Boolean = false) = EditText(context).apply {
        this.hint = hint
        textSize = 15f
        typeface = look.body(600)
        setTextColor(look.text)
        setHintTextColor(look.muted)
        background = Soft(look.bg, 16f, Depth.PRESS, 0f)
        setPadding(px(14f), px(14f), px(14f), px(14f))
        inputType = when {
            password -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            multiLine -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            else -> InputType.TYPE_CLASS_TEXT
        }
        if (multiLine) { minLines = 2; gravity = Gravity.TOP } else isSingleLine = true
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = px(6f) }
    }

    /**
     * One choice in a list (a model, a price tier): title, a quiet line under it, and an optional
     * chip on the right. The chosen one is pressed in, with a filled dot.
     */
    fun option(title: String, sub: String?, chip: TextView?, on: Boolean, onClick: () -> Unit) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(px(14f), px(12f), px(14f), px(12f))
        background = if (on) Soft(look.bg, 18f, Depth.PRESS, 0f) else null
        addView(object : View(context) {
            override fun onDraw(c: Canvas) {
                val r = RectF(0f, 0f, width.toFloat(), height.toFloat())
                look.press(c, r, width / 2f, look.bg, 2f)
                if (on) c.drawCircle(width / 2f, height / 2f, 5 * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = look.accent })
            }
        }, LinearLayout.LayoutParams(px(22f), px(22f)).apply { marginEnd = px(12f) })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(title, 15f, weight = 700))
            sub?.let { addView(text(it, 12f, look.muted, 600)) }
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        chip?.let { addView(it) }
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = px(4f) }
        setOnClickListener { tap(this); onClick() }
    }

    /** A row: label on the left, something (chip, button, switch) on the right. */
    fun row(label: View, end: View) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, px(4f), 0, px(4f))
        addView(label, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        addView(end)
    }

    fun tap(v: View) = v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

    /** The Pebble switch: a pressed-in track that turns green, with a raised knob. */
    inner class Switch(on: Boolean, private val onChange: (Boolean) -> Unit) : View(context) {
        var on = on
            set(v) { field = v; invalidate() }
        override fun setEnabled(enabled: Boolean) {
            super.setEnabled(enabled)
            alpha = if (enabled) 1f else 0.45f
        }

        override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(px(64f), px(40f))

        override fun onDraw(c: Canvas) {
            val m = 4 * d
            val track = RectF(m, m, width - m, height - m)
            look.press(c, track, track.height() / 2, if (on) look.accent else look.bg, 3f)
            val k = track.height() - 8 * d
            val x = if (on) track.right - 4 * d - k else track.left + 4 * d
            look.lift(c, RectF(x, track.top + 4 * d, x + k, track.top + 4 * d + k), k / 2, look.key, 2f)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (e.action == MotionEvent.ACTION_UP && isEnabled) {
                on = !on
                tap(this)
                onChange(on)
            }
            return true
        }
    }
}
