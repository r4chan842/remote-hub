package app.remotehub.rdp

import android.content.Context
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * RDP scancodes (set 1). Values with bit 0x100 set are the extended keys;
 * freerdp_input_send_keyboard_event_ex() turns that bit into KBD_FLAGS_EXTENDED.
 */
object RdpKeys {
    const val ESCAPE = 0x01
    const val BACKSPACE = 0x0E
    const val TAB = 0x0F
    const val ENTER = 0x1C
    const val LCTRL = 0x1D
    const val LSHIFT = 0x2A
    const val RSHIFT = 0x36
    const val LALT = 0x38
    const val SPACE = 0x39
    const val CAPSLOCK = 0x3A
    const val LWIN = 0x15B
    const val RWIN = 0x15C
    const val APPS = 0x15D
    const val INSERT = 0x152
    const val DELETE = 0x153
    const val HOME = 0x147
    const val END = 0x14F
    const val PAGEUP = 0x149
    const val PAGEDOWN = 0x151
    const val UP = 0x148
    const val DOWN = 0x150
    const val LEFT = 0x14B
    const val RIGHT = 0x14D

    private val letters = intArrayOf(
        0x1E, 0x30, 0x2E, 0x20, 0x12, 0x21, 0x22, 0x23, 0x17, 0x24, 0x25, 0x26, 0x32,
        0x31, 0x18, 0x19, 0x10, 0x13, 0x1F, 0x14, 0x16, 0x2F, 0x11, 0x2D, 0x15, 0x2C,
    )
    private val digits = intArrayOf(0x0B, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A)

    /** Android keycode -> RDP scancode, for keys that are not text. */
    fun fromAndroid(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_DEL -> BACKSPACE
        KeyEvent.KEYCODE_FORWARD_DEL -> DELETE
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> ENTER
        KeyEvent.KEYCODE_TAB -> TAB
        KeyEvent.KEYCODE_ESCAPE -> ESCAPE
        KeyEvent.KEYCODE_SPACE -> SPACE
        KeyEvent.KEYCODE_DPAD_UP -> UP
        KeyEvent.KEYCODE_DPAD_DOWN -> DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> RIGHT
        KeyEvent.KEYCODE_MOVE_HOME -> HOME
        KeyEvent.KEYCODE_MOVE_END -> END
        KeyEvent.KEYCODE_PAGE_UP -> PAGEUP
        KeyEvent.KEYCODE_PAGE_DOWN -> PAGEDOWN
        KeyEvent.KEYCODE_INSERT -> INSERT
        KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> LCTRL
        KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> LSHIFT
        KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> LALT
        KeyEvent.KEYCODE_META_LEFT -> LWIN
        KeyEvent.KEYCODE_META_RIGHT -> RWIN
        KeyEvent.KEYCODE_CAPS_LOCK -> CAPSLOCK
        in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> letters[keyCode - KeyEvent.KEYCODE_A]
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> digits[keyCode - KeyEvent.KEYCODE_0]
        in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F10 -> 0x3B + (keyCode - KeyEvent.KEYCODE_F1)
        KeyEvent.KEYCODE_F11 -> 0x57
        KeyEvent.KEYCODE_F12 -> 0x58
        KeyEvent.KEYCODE_MINUS -> 0x0C
        KeyEvent.KEYCODE_EQUALS -> 0x0D
        KeyEvent.KEYCODE_LEFT_BRACKET -> 0x1A
        KeyEvent.KEYCODE_RIGHT_BRACKET -> 0x1B
        KeyEvent.KEYCODE_SEMICOLON -> 0x27
        KeyEvent.KEYCODE_APOSTROPHE -> 0x28
        KeyEvent.KEYCODE_GRAVE -> 0x29
        KeyEvent.KEYCODE_BACKSLASH -> 0x2B
        KeyEvent.KEYCODE_COMMA -> 0x33
        KeyEvent.KEYCODE_PERIOD -> 0x34
        KeyEvent.KEYCODE_SLASH -> 0x35
        else -> null
    }

    /**
     * US-layout scancode for a character, used only when the host did not negotiate
     * Unicode input. Returns (scancode, shiftNeeded).
     */
    fun forChar(c: Char): Pair<Int, Boolean>? {
        val lower = c.lowercaseChar()
        if (lower in 'a'..'z') return letters[lower - 'a'] to c.isUpperCase()
        if (c in '0'..'9') return digits[c - '0'] to false
        return when (c) {
            ' ' -> SPACE to false
            '\n' -> ENTER to false
            '\t' -> TAB to false
            '-' -> 0x0C to false
            '_' -> 0x0C to true
            '=' -> 0x0D to false
            '+' -> 0x0D to true
            '[' -> 0x1A to false
            '{' -> 0x1A to true
            ']' -> 0x1B to false
            '}' -> 0x1B to true
            ';' -> 0x27 to false
            ':' -> 0x27 to true
            '\'' -> 0x28 to false
            '"' -> 0x28 to true
            '`' -> 0x29 to false
            '~' -> 0x29 to true
            '\\' -> 0x2B to false
            '|' -> 0x2B to true
            ',' -> 0x33 to false
            '<' -> 0x33 to true
            '.' -> 0x34 to false
            '>' -> 0x34 to true
            '/' -> 0x35 to false
            '?' -> 0x35 to true
            '!' -> 0x02 to true
            '@' -> 0x03 to true
            '#' -> 0x04 to true
            '$' -> 0x05 to true
            '%' -> 0x06 to true
            '^' -> 0x07 to true
            '&' -> 0x08 to true
            '*' -> 0x09 to true
            '(' -> 0x0A to true
            ')' -> 0x0B to true
            else -> null
        }
    }
}

/**
 * The missing half of the keyboard.
 *
 * showKeyboard() only called toggleSoftInput(), but Android delivers IME output
 * through an InputConnection, which only exists for a focused text editor. The
 * desktop is a Canvas, so there was no editor, no connection, and every keystroke
 * went nowhere. This view is that editor: it is 1dp, invisible, and forwards
 * everything straight to the session.
 */
class RdpKeyboardView(context: Context) : View(context) {
    var onText: ((String) -> Unit)? = null
    var onScancode: ((Int, Boolean) -> Unit)? = null
    var onPaste: (() -> Unit)? = null
    var onImeVisibility: ((Boolean) -> Unit)? = null

    private val held = mutableListOf<Int>()

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        alpha = 0f
        setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) onImeVisibility?.invoke(false) }
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        // NO_SUGGESTIONS keeps Gboard from swallowing Persian/Arabic into a
        // suggestion strip that never commits; NO_EXTRACT_UI stops the fullscreen
        // extract editor from covering the remote desktop in landscape.
        outAttrs.inputType = android.text.InputType.TYPE_CLASS_TEXT or
            android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or
            EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_ACTION_NONE
        outAttrs.initialSelStart = 0
        outAttrs.initialSelEnd = 0
        return Connection(this)
    }

    fun showIme() {
        requestFocus()
        val imm = context.getSystemService(InputMethodManager::class.java) ?: return
        if (!imm.showSoftInput(this, 0)) {
            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
        }
        onImeVisibility?.invoke(true)
    }

    fun hideIme() {
        val imm = context.getSystemService(InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(windowToken, 0)
        clearFocus()
        onImeVisibility?.invoke(false)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) onImeVisibility?.invoke(false)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = handleKey(event) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = handleKey(event) || super.onKeyUp(keyCode, event)

    private fun tap(scancode: Int) {
        onScancode?.invoke(scancode, true)
        onScancode?.invoke(scancode, false)
    }

    private fun releaseHeld() {
        held.asReversed().forEach { onScancode?.invoke(it, false) }
        held.clear()
    }

    private fun handleKey(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code == KeyEvent.KEYCODE_PASTE) {
            if (event.action == KeyEvent.ACTION_UP) onPaste?.invoke()
            return true
        }
        if (code == KeyEvent.KEYCODE_BACK) return false
        val scan = RdpKeys.fromAndroid(code)
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                val uni = event.unicodeChar
                if (scan == null && uni != 0 && !event.isCtrlPressed && !event.isAltPressed) {
                    onText?.invoke(String(Character.toChars(uni)))
                    return true
                }
                if (scan == null) return false
                if (event.isShiftPressed) held += RdpKeys.LSHIFT
                if (event.isCtrlPressed) held += RdpKeys.LCTRL
                if (event.isAltPressed) held += RdpKeys.LALT
                held.forEach { onScancode?.invoke(it, true) }
                onScancode?.invoke(scan, true)
                return true
            }
            KeyEvent.ACTION_UP -> {
                if (scan == null) return false
                onScancode?.invoke(scan, false)
                releaseHeld()
                return true
            }
            KeyEvent.ACTION_MULTIPLE -> {
                event.characters?.let { onText?.invoke(it) }
                return true
            }
        }
        return false
    }

    private inner class Connection(target: View) : BaseInputConnection(target, false) {
        /** What the IME currently believes is in the editor. */
        private var composing = ""

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence = composing.takeLast(n.coerceAtLeast(0))

        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence = ""

        override fun getSelectedText(flags: Int): CharSequence? = null

        override fun getCursorCapsMode(reqModes: Int): Int = 0

        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
            applyComposing(text?.toString() ?: "")
            return true
        }

        override fun finishComposingText(): Boolean {
            // Already forwarded incrementally, so just forget the region.
            composing = ""
            return true
        }

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            val next = text?.toString() ?: ""
            // A commit replaces the composing region, so undo what we typed for it.
            if (composing.isNotEmpty()) {
                backspaces(composing.length)
                composing = ""
            }
            if (next.isNotEmpty()) onText?.invoke(next)
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            val before = beforeLength.coerceAtLeast(0)
            if (before == 0) return true
            if (composing.isNotEmpty()) {
                val keep = (composing.length - before).coerceAtLeast(0)
                applyComposing(composing.substring(0, keep))
            } else {
                backspaces(before)
            }
            return true
        }

        override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean =
            deleteSurroundingText(beforeLength, afterLength)

        override fun sendKeyEvent(event: KeyEvent): Boolean = handleKey(event)

        override fun performContextMenuAction(id: Int): Boolean = when (id) {
            android.R.id.paste, android.R.id.pasteAsPlainText -> {
                onPaste?.invoke()
                true
            }
            android.R.id.selectAll, android.R.id.copy, android.R.id.cut -> {
                // Forward the chord so it acts on the remote application.
                onScancode?.invoke(RdpKeys.LCTRL, true)
                val key = when (id) {
                    android.R.id.selectAll -> 0x1E // A
                    android.R.id.copy -> 0x2E     // C
                    else -> 0x2D                  // X
                }
                tap(key)
                onScancode?.invoke(RdpKeys.LCTRL, false)
                true
            }
            else -> false
        }

        override fun performEditorAction(editorAction: Int): Boolean {
            tap(RdpKeys.ENTER)
            return true
        }

        override fun setSelection(start: Int, end: Int): Boolean = true

        override fun clearMetaKeyStates(states: Int): Boolean {
            releaseHeld()
            return true
        }

        /**
         * Composing text is forwarded as a diff so the user sees characters as they
         * type, while an IME that revises the word (Persian/Arabic shaping, Gboard
         * autocorrect) still ends up with the right text on the host.
         */
        private fun applyComposing(next: String) {
            var common = 0
            while (common < composing.length && common < next.length && composing[common] == next[common]) common++
            backspaces(composing.length - common)
            if (next.length > common) onText?.invoke(next.substring(common))
            composing = next
        }

        private fun backspaces(count: Int) {
            repeat(count.coerceAtMost(256)) { tap(RdpKeys.BACKSPACE) }
        }
    }
}

/**
 * Hosts the invisible editor inside the remote screen. It must be composed for the
 * whole session, not only while the keyboard is open, or focus is lost between
 * toggles and the IME closes itself.
 */
@Composable
fun RdpKeyboardTarget(
    visible: Boolean,
    onText: (String) -> Unit,
    onScancode: (Int, Boolean) -> Unit,
    onPaste: () -> Unit,
    onImeVisibility: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            RdpKeyboardView(ctx).apply {
                this.onText = onText
                this.onScancode = onScancode
                this.onPaste = onPaste
                this.onImeVisibility = onImeVisibility
                if (Build.VERSION.SDK_INT >= 30) {
                    setOnApplyWindowInsetsListener { v, insets ->
                        val shown = insets.isVisible(android.view.WindowInsets.Type.ime())
                        this.onImeVisibility?.invoke(shown)
                        insets
                    }
                }
            }
        },
        update = { view ->
            view.onText = onText
            view.onScancode = onScancode
            view.onPaste = onPaste
            view.onImeVisibility = onImeVisibility
            if (visible) view.showIme() else view.hideIme()
        },
    )
}
