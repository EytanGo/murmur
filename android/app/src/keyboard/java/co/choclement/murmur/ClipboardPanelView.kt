package co.choclement.murmur

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * The clipboard, opened from the toolbar. Two tabs: Clipboard (up to 10 pinned clips as tiles,
 * then the last 10 copies) and Snippets. Tap anything to type it in; hold a clip to remove it.
 */
class ClipboardPanelView(context: Context, private val actions: Actions) : FrameLayout(context) {

    interface Actions {
        fun onInsert(text: String)
        fun onCloseClipboard()
    }

    private val p = Pebble(context)
    private val look = p.look
    private var tab = "clips"
    private val list = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(p.px(12f), 0, p.px(12f), p.px(12f))
    }

    init {
        setBackgroundColor(look.bg)
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, p.px(10f), 0, 0)
        }
        column.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(p.px(16f), 0, p.px(10f), p.px(4f))
            addView(p.segmented(listOf("clips" to "Clipboard", "snippets" to "Snippets"), tab) { tab = it; refresh() },
                LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(p.button("Done") { actions.onCloseClipboard() }.apply { minHeight = p.px(52f) },
                LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = p.px(6f) })
        })
        column.addView(ScrollView(context).apply { addView(list) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        addView(column, LayoutParams(MATCH_PARENT, p.px(360f)))
    }

    /** Rebuilds the lists; call each time the panel is shown. */
    fun refresh() {
        list.removeAllViews()
        if (tab == "snippets") return showSnippets()
        val pinned = Clips.pinned
        list.addView(section("Pinned", "${pinned.size} of ${Clips.MAX}"))
        // Pinned clips as a two-column grid of peach tiles, ending with a "+ Pin something" slot.
        val tiles: List<View> = pinned.map { pinTile(it) } + listOfNotNull(if (pinned.size < Clips.MAX) addTile() else null)
        tiles.chunked(2).forEach { pair ->
            list.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                pair.forEach { addView(it, LinearLayout.LayoutParams(0, p.px(84f), 1f)) }
                if (pair.size == 1) addView(View(context), LinearLayout.LayoutParams(0, 0, 1f))
            })
        }
        val recent = Clips.recent
        list.addView(section("Recent", "last ${Clips.MAX} · hold to remove"))
        if (recent.isEmpty()) list.addView(note("Things you copy show up here."))
        recent.forEach { list.addView(recentRow(it)) }
    }

    private fun showSnippets() {
        val snippets = Snippets.all
        list.addView(section("Snippets", "say them, or tap here"))
        if (snippets.isEmpty()) list.addView(note("Add snippets in Murmur's settings."))
        snippets.forEach { s ->
            list.addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = p.Soft(look.key, 18f, Pebble.Depth.LIFT, 5f)
                setPadding(p.px(19f), p.px(14f), p.px(19f), p.px(14f))
                addView(p.text("“${s.trigger}”", 14f, weight = 800))
                addView(p.text(s.text, 13f, look.muted, 600).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END })
                setOnClickListener { p.tap(this); actions.onInsert(s.text) }
            })
        }
    }

    private fun pinTile(text: String) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = p.Soft(look.peach, 20f, Pebble.Depth.LIFT, 5f)
        setPadding(p.px(17f), p.px(13f), p.px(13f), p.px(11f))
        addView(p.text(text, 13f, look.text, 600).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END },
            LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        addView(pinIcon(filled = true) { Clips.unpin(text); refresh() }, LinearLayout.LayoutParams(p.px(26f), p.px(22f)))
        setOnClickListener { p.tap(this); actions.onInsert(text) }
        setOnLongClickListener { Clips.remove(text); refresh(); true }
    }

    private fun addTile() = object : TextView(context) {
        private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2 * look.d
            color = look.muted
            alpha = 120
            pathEffect = DashPathEffect(floatArrayOf(6 * look.d, 5 * look.d), 0f)
        }
        override fun onDraw(c: Canvas) {
            val m = 6 * look.d
            c.drawRoundRect(RectF(m, m, width - m, height - m), 20 * look.d, 20 * look.d, dash)
            super.onDraw(c)
        }
    }.apply {
        text = "+ Pin something"
        textSize = 13f
        typeface = look.body(700)
        setTextColor(look.muted)
        gravity = Gravity.CENTER
        setOnClickListener { Toast.makeText(context, "Tap the pin on a recent copy to keep it here.", Toast.LENGTH_SHORT).show() }
    }

    private fun recentRow(text: String) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = p.Soft(look.key, 18f, Pebble.Depth.LIFT, 5f)
        setPadding(p.px(19f), p.px(5f), p.px(9f), p.px(5f))
        minimumHeight = p.px(60f)
        addView(p.text(text, 14f, look.text, 600).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        addView(pinIcon(filled = false) {
            if (!Clips.pin(text)) Toast.makeText(context, "You can pin up to ${Clips.MAX}. Unpin one first.", Toast.LENGTH_SHORT).show()
            refresh()
        }, LinearLayout.LayoutParams(p.px(40f), p.px(40f)))
        setOnClickListener { p.tap(this); actions.onInsert(text) }
        setOnLongClickListener { Clips.remove(text); refresh(); true }
    }

    /** The pushpin from the design: clay and filled on pinned tiles, an outline on recent rows. */
    private fun pinIcon(filled: Boolean, onTap: () -> Unit) = object : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = if (filled) Paint.Style.FILL else Paint.Style.STROKE
            strokeWidth = 2 * look.d
            strokeJoin = Paint.Join.ROUND
            color = if (filled) look.clay else look.muted
        }
        override fun onDraw(c: Canvas) {
            val s = (if (filled) 14 else 16) * look.d / 24
            c.save()
            c.translate(if (filled) width - 24 * s - 2 * look.d else (width - 24 * s) / 2, (height - 24 * s) / 2)
            c.scale(s, s)
            c.drawPath(Path().apply {
                moveTo(15f, 3f); lineTo(21f, 9f); lineTo(18f, 10f); lineTo(14f, 14f); lineTo(15f, 19f); lineTo(13f, 21f)
                lineTo(9f, 17f); lineTo(4f, 22f); lineTo(3f, 21f); lineTo(8f, 16f); lineTo(4f, 12f); lineTo(6f, 10f)
                lineTo(11f, 11f); lineTo(15f, 7f); close()
            }, paint)
            c.restore()
        }
    }.apply { setOnClickListener { p.tap(this); onTap() } }

    private fun section(title: String, aside: String) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.BOTTOM
        setPadding(p.px(6f), p.px(14f), p.px(6f), p.px(4f))
        addView(p.text(title, 15f, weight = 700, display = true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        addView(p.text(aside, 12f, look.muted, 700))
    }

    private fun note(s: String) = p.text(s, 13f, look.muted, 600).apply { setPadding(p.px(6f), p.px(6f), 0, p.px(4f)) }
}
