package co.choclement.murmur

import android.Manifest
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.Build
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout

/**
 * The Murmur keyboard. Two modes:
 *  - Typing: a normal Gboard-style keyboard.
 *  - Voice: no keys at all. Tapping a text box starts listening right away, your words appear
 *    in the box as you speak, and Send finishes up and sends (like pressing enter / tapping send).
 */
class MurmurKeyboard : InputMethodService(), KeyboardView.Actions, VoicePanelView.Actions, ClipboardPanelView.Actions,
    LiveSession.Listener {

    private lateinit var container: FrameLayout
    private var keys: KeyboardView? = null
    private var voice: VoicePanelView? = null
    private var clips: ClipboardPanelView? = null
    private var showingClips = false

    private var session: LiveSession? = null
    private val voiceMode get() = Prefs.imeMode == "voice"

    // Live text bookkeeping: which phrase is currently shown (underlined) in the text box.
    private var shownPhrase = -1
    private var phrasePrefix = ""
    private var phraseCapital = false
    private var lastFinal = -1
    private var justSentAt = 0L
    private var sessions = 0          // each session numbers its phrases from sessions * 100 000

    private val clipboard by lazy { getSystemService(ClipboardManager::class.java) }
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { saveClip() }

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Transcriber.load(this)
        // As your keyboard, Murmur may read the clipboard, so it can keep a history of what you copy.
        clipboard?.addPrimaryClipChangedListener(clipListener)
    }

    override fun onDestroy() {
        clipboard?.removePrimaryClipChangedListener(clipListener)
        super.onDestroy()
    }

    /** Adds the current clipboard to the history, skipping things apps mark as sensitive (passwords). */
    private fun saveClip() {
        val clip = runCatching { clipboard?.primaryClip }.getOrNull() ?: return
        if (Build.VERSION.SDK_INT >= 33 && clip.description.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true) return
        val text = clip.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString() ?: return
        Clips.add(text)
    }

    override fun onCreateInputView(): View {
        container = FrameLayout(this)
        keys = KeyboardView(this, this)
        voice = VoicePanelView(this, this)
        clips = ClipboardPanelView(this, this)
        showMode()
        return container
    }

    override fun onEvaluateFullscreenMode() = false

    private fun showMode() {
        if (!::container.isInitialized) return
        container.removeAllViews()
        container.addView(if (showingClips) clips else if (voiceMode) voice else keys)
        // The system bar under the keyboard takes the same soft color as the panel above it.
        val look = Look(this)
        window.window?.let { w ->
            w.navigationBarColor = if (!showingClips && voiceMode) look.key else look.bg
            @Suppress("DEPRECATION")
            w.decorView.systemUiVisibility = if (look.dark) 0 else View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        showingClips = false
        saveClip() // in case something was copied while Murmur wasn't running
        keys?.enter = enterKind(info)
        keys?.setPage(KeyboardView.Page.LETTERS)
        updateShift()
        showMode()
        // Voice mode: tapping a text box starts listening straight away.
        // (Not right after Send, when chat apps re-open the same box.)
        if (voiceMode && !restarting && System.currentTimeMillis() - justSentAt > 1500) startListening()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        // The text box is going away: keep what's already there and stop listening.
        session?.cancel()
        session = null
        currentInputConnection?.finishComposingText()
        voice?.state = VoicePanelView.State.PAUSED
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candStart: Int, candEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candStart, candEnd)
        if (!voiceMode) updateShift()
    }

    private fun updateShift() {
        val info = currentInputEditorInfo ?: return
        val caps = currentInputConnection?.getCursorCapsMode(info.inputType) ?: 0
        keys?.autoShift(caps != 0)
    }

    // MARK: - Typing mode

    override fun onText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    override fun onDelete() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) ic.commitText("", 1) else ic.deleteSurroundingText(1, 0)
        if (selected.isNullOrEmpty() && ic.getTextBeforeCursor(1, 0).isNullOrEmpty()) {
            // Some apps (and empty fields) only react to a real backspace key.
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
        }
    }

    /** Opens the clipboard: pinned clips, the last 10 copies, and snippets. Listening pauses meanwhile. */
    override fun onPaste() {
        stopListening {}
        saveClip()
        clips?.refresh()
        showingClips = true
        showMode()
    }

    override fun onInsert(text: String) {
        currentInputConnection?.finishComposingText()
        currentInputConnection?.commitText(text, 1)
        onCloseClipboard()
    }

    override fun onCloseClipboard() {
        showingClips = false
        showMode()
        if (!voiceMode) updateShift()
    }

    /**
     * Deletes the word before the cursor, with any punctuation stuck to it ("there." goes in one
     * tap), or the selection. A line break on its own is deleted by itself.
     */
    override fun onDeleteWord() {
        val ic = currentInputConnection ?: return
        if (shownPhrase != -1) return
        if (!ic.getSelectedText(0).isNullOrEmpty()) { ic.commitText("", 1); return }
        val before = ic.getTextBeforeCursor(200, 0)?.toString().orEmpty()
        if (before.isEmpty()) return onDelete()
        var n = before.length
        if (before[n - 1] == '\n') n--
        else {
            fun wordChar(c: Char) = c.isLetterOrDigit() || c in "'’-"
            while (n > 0 && before[n - 1].isWhitespace() && before[n - 1] != '\n') n--
            while (n > 0 && !wordChar(before[n - 1]) && !before[n - 1].isWhitespace()) n--
            while (n > 0 && wordChar(before[n - 1])) n--
        }
        ic.deleteSurroundingText(before.length - n, 0)
    }

    override fun onSpace() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(2, 0)?.toString() ?: ""
        // Double-space types ". " like Gboard.
        if (keys?.spaceJustTyped() == true && before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
        } else {
            ic.commitText(" ", 1)
        }
    }

    override fun onEnter() = pressEnter()

    override fun onVoiceMode() {
        Prefs.imeMode = "voice"
        showMode()
        startListening()
    }

    /** Clears the whole text box (holding delete-word). Words still on their way are dropped. */
    override fun onClearAll() {
        val ic = currentInputConnection ?: return
        val wasListening = session != null
        session?.cancel()
        session = null
        shownPhrase = -1
        ic.finishComposingText()
        val before = ic.getTextBeforeCursor(100_000, 0)?.length ?: 0
        val after = ic.getTextAfterCursor(100_000, 0)?.length ?: 0
        ic.beginBatchEdit()
        if (!(ic.performContextMenuAction(android.R.id.selectAll) && ic.commitText("", 1))) ic.deleteSurroundingText(before, after)
        ic.endBatchEdit()
        if (wasListening) startListening()
    }

    override fun onSettings() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun onPickKeyboard() {
        getSystemService(InputMethodManager::class.java).showInputMethodPicker()
    }

    // MARK: - Voice mode

    override fun onTypingMode() {
        stopListening {}
        Prefs.imeMode = "typing"
        showMode()
        updateShift()
    }

    override fun onPauseResume() {
        if (session != null) stopListening {} else startListening()
    }

    override fun onSend() {
        if (session != null) stopListening(waitForText = true) { pressSend() } else pressSend()
    }

    private fun startListening() {
        if (session != null) return
        val v = voice ?: return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            v.state = VoicePanelView.State.NO_MIC
            onSettings()
            return
        }
        val test = LiveSession.testAudio?.also { LiveSession.testAudio = null }
        val base = ++sessions * 100_000
        val s = if (test != null) LiveSession(this, this, WavSource(test), base) else LiveSession(this, this, firstPhrase = base)
        if (!s.start()) {
            v.state = VoicePanelView.State.NO_MIC
            return
        }
        session = s
        v.state = VoicePanelView.State.LISTENING
    }

    /**
     * Stops listening. Pausing turns red right away while the last words finish in the
     * background; [waitForText] (Send) waits until they're in the text box first.
     */
    private fun stopListening(waitForText: Boolean = false, then: () -> Unit) {
        val s = session ?: return then()
        session = null
        voice?.state = if (waitForText) VoicePanelView.State.WORKING else VoicePanelView.State.PAUSED
        s.stop {
            if (session == null) currentInputConnection?.finishComposingText()
            if (waitForText) voice?.state = VoicePanelView.State.PAUSED
            then()
        }
    }

    override fun onLevel(level: Float) {
        voice?.pushLevel(level)
    }

    override fun onPartial(phrase: Int, text: String) {
        if (phrase <= lastFinal) return
        val ic = currentInputConnection ?: return
        if (phrase != shownPhrase) {
            beginPhrase()
            shownPhrase = phrase
        }
        // Whisper ends a phrase it hears cut off with "…"; you're still talking, so drop it.
        val live = text.trimEnd().removeSuffix("...").removeSuffix("…").trimEnd()
        ic.setComposingText(phrasePrefix + shape(live), 1)
    }

    override fun onFinal(phrase: Int, text: String) {
        val ic = currentInputConnection ?: return
        if (phrase != shownPhrase) beginPhrase()
        if (text.isBlank()) {
            if (phrase == shownPhrase) ic.setComposingText("", 1)
        } else {
            ic.setComposingText(phrasePrefix + shape(text), 1)
        }
        ic.finishComposingText()
        lastFinal = phrase
        shownPhrase = -1
    }

    /**
     * Looks at the text before the cursor once, as a new phrase begins: whether it needs a
     * space in front (you're continuing after other words) and a capital (new sentence).
     */
    private fun beginPhrase() {
        val before = currentInputConnection?.getTextBeforeCursor(3, 0)?.toString() ?: ""
        val last = before.lastOrNull()
        phrasePrefix = if (last == null || last.isWhitespace() || last in "([{\"'/") "" else " "
        val trimmed = before.trimEnd()
        phraseCapital = trimmed.isEmpty() || trimmed.last() in ".!?\n"
    }

    private fun shape(text: String) = if (phraseCapital) text.replaceFirstChar { it.uppercaseChar() } else text

    // MARK: - Enter and Send

    private fun enterKind(info: EditorInfo): Look.Enter {
        if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return Look.Enter.RETURN
        return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_SEARCH -> Look.Enter.SEARCH
            EditorInfo.IME_ACTION_SEND -> Look.Enter.SEND
            EditorInfo.IME_ACTION_GO -> Look.Enter.GO
            EditorInfo.IME_ACTION_NEXT -> Look.Enter.NEXT
            EditorInfo.IME_ACTION_DONE -> Look.Enter.DONE
            else -> Look.Enter.RETURN
        }
    }

    private fun editorAction(): Int? {
        val info = currentInputEditorInfo ?: return null
        if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return null
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        return action.takeIf { it != EditorInfo.IME_ACTION_NONE && it != EditorInfo.IME_ACTION_UNSPECIFIED }
    }

    /** The enter key: the field's own action (search, go, send…) or a new line. */
    private fun pressEnter() {
        val ic = currentInputConnection ?: return
        val action = editorAction()
        if (action != null) ic.performEditorAction(action) else sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
    }

    /**
     * Send: in a search box that's "search"/"go"; in a chat app like WhatsApp, where enter
     * just adds a new line, Murmur taps the app's own send button (via its accessibility service).
     */
    private fun pressSend() {
        val ic = currentInputConnection ?: return
        ic.finishComposingText()
        justSentAt = System.currentTimeMillis()
        val action = editorAction()
        when {
            action != null -> ic.performEditorAction(action)
            MurmurService.instance?.clickSendButton() == true -> {}
            else -> sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }
}
