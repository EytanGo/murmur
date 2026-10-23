package co.choclement.murmur

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs

/**
 * Murmur's send helper. In chat apps like WhatsApp the enter key only adds a new line, so when
 * you tap Send in voice mode, Murmur presses the app's own send button. Android only allows
 * that through an accessibility service; this one does nothing else.
 */
class MurmurService : AccessibilityService() {

    companion object {
        @Volatile var instance: MurmurService? = null
            private set
    }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

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
}
