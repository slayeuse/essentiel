package fr.essentiel.launcher.keyboard

import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import fr.essentiel.launcher.EssentielTheme
import fr.essentiel.launcher.Fonts
import kotlin.concurrent.thread

enum class KbMode { LETTERS, SYMBOLS, SYMBOLS2, EMOJI, NUMBERS, PHONE }
enum class Shift { OFF, ONCE, LOCK }

/**
 * Le clavier Essentiel (service de saisie Android).
 * À activer dans Réglages Android › Langues et saisie › Clavier virtuel.
 */
class EssentielKeyboard : InputMethodService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    // ---------- Ce dont Compose a besoin pour s'afficher dans un clavier ----------
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    // ---------- État du clavier (lu par l'interface) ----------
    var mode by mutableStateOf(KbMode.LETTERS)
    var shift by mutableStateOf(Shift.OFF)
    var suggestions by mutableStateOf<List<String>>(emptyList())
    var currentWord by mutableStateOf("")
    var accents by mutableStateOf<List<String>>(emptyList())
    var enterLabel by mutableStateOf("↵")
    private var enterAction = EditorInfo.IME_ACTION_NONE
    private var noSuggestions = false
    private var lastShiftTap = 0L

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        Fonts.init(this)
        thread { Dictionary.load(applicationContext) }
    }

    override fun onCreateInputView(): View {
        window?.window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(this)
            decor.setViewTreeViewModelStoreOwner(this)
            decor.setViewTreeSavedStateRegistryOwner(this)
        }
        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@EssentielKeyboard)
            setViewTreeViewModelStoreOwner(this@EssentielKeyboard)
            setViewTreeSavedStateRegistryOwner(this@EssentielKeyboard)
            setContent { EssentielTheme { KeyboardUI(this@EssentielKeyboard) } }
        }
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        mode = when (cls) {
            InputType.TYPE_CLASS_NUMBER -> KbMode.NUMBERS
            InputType.TYPE_CLASS_PHONE -> KbMode.PHONE
            InputType.TYPE_CLASS_DATETIME -> KbMode.NUMBERS
            else -> KbMode.LETTERS
        }
        noSuggestions = cls != InputType.TYPE_CLASS_TEXT ||
            variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_URI ||
            (info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        val multiLine = (info.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
        enterAction = if ((info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0 || multiLine) EditorInfo.IME_ACTION_NONE else action
        enterLabel = when (enterAction) {
            EditorInfo.IME_ACTION_SEND -> "envoyer"
            EditorInfo.IME_ACTION_SEARCH -> "chercher"
            EditorInfo.IME_ACTION_GO -> "aller"
            EditorInfo.IME_ACTION_NEXT -> "suivant"
            EditorInfo.IME_ACTION_DONE -> "ok"
            else -> "↵"
        }
        accents = emptyList()
        shift = Shift.OFF
        updateAutoCaps()
        refreshWord()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        super.onDestroy()
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        refreshWord()
    }

    // ---------- Actions ----------
    private fun ic() = currentInputConnection

    /** Le mot en cours de frappe (juste avant le curseur). */
    private fun refreshWord() {
        val before = ic()?.getTextBeforeCursor(40, 0)?.toString().orEmpty()
        currentWord = before.takeLastWhile { it.isLetter() || it == '\'' || it == '-' }
        suggestions = if (noSuggestions || currentWord.isEmpty() || !Dictionary.loaded) emptyList() else Dictionary.suggest(currentWord)
    }

    private fun updateAutoCaps() {
        if (mode != KbMode.LETTERS || shift == Shift.LOCK) return
        val info = currentInputEditorInfo ?: return
        val caps = ic()?.getCursorCapsMode(info.inputType) ?: 0
        shift = if (caps != 0) Shift.ONCE else Shift.OFF
    }

    fun type(text: String) {
        val t = if (mode == KbMode.LETTERS && shift != Shift.OFF) text.uppercase() else text
        ic()?.commitText(t, 1)
        accents = emptyList()
        if (shift == Shift.ONCE) shift = Shift.OFF
        if (t == " " && !noSuggestions && currentWord.isNotEmpty()) Dictionary.learn(applicationContext, currentWord)
        updateAutoCaps()
        refreshWord()
    }

    fun backspace() {
        val c = ic() ?: return
        val sel = c.getSelectedText(0)
        if (!sel.isNullOrEmpty()) c.commitText("", 1)
        else {
            // supprime un emoji entier (2 caractères) si besoin
            val before = c.getTextBeforeCursor(2, 0)?.toString().orEmpty()
            val n = if (before.length == 2 && Character.isSurrogatePair(before[0], before[1])) 2 else 1
            c.deleteSurroundingText(n, 0)
        }
        accents = emptyList()
        updateAutoCaps()
        refreshWord()
    }

    fun pickSuggestion(word: String) {
        val c = ic() ?: return
        c.deleteSurroundingText(currentWord.length, 0)
        c.commitText("$word ", 1)
        Dictionary.learn(applicationContext, word)
        updateAutoCaps()
        refreshWord()
    }

    fun toggleShift() {
        val now = System.currentTimeMillis()
        shift = when {
            shift == Shift.LOCK -> Shift.OFF
            now - lastShiftTap < 350 -> Shift.LOCK
            shift == Shift.ONCE -> Shift.OFF
            else -> Shift.ONCE
        }
        lastShiftTap = now
    }

    fun enter() {
        val c = ic() ?: return
        if (enterAction != EditorInfo.IME_ACTION_NONE) c.performEditorAction(enterAction)
        else c.commitText("\n", 1)
        updateAutoCaps()
        refreshWord()
    }

    /** Glisser sur la barre d'espace : déplace le curseur. */
    fun moveCursor(steps: Int) {
        val code = if (steps < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        repeat(kotlin.math.abs(steps)) { sendDownUpKeyEvents(code) }
    }

    fun showPicker() {
        getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
    }

    fun hide() = requestHideSelf(0)

    fun showAccents(base: String) {
        val list = ACCENTS[base.lowercase()] ?: return
        accents = list.map { if (shift != Shift.OFF) it.uppercase() else it }
    }

    companion object {
        val ACCENTS = mapOf(
            "e" to listOf("é", "è", "ê", "ë", "€"),
            "a" to listOf("à", "â", "æ", "ä", "@"),
            "c" to listOf("ç", "©"),
            "i" to listOf("î", "ï", "ì"),
            "o" to listOf("ô", "œ", "ö", "ò", "°"),
            "u" to listOf("ù", "û", "ü"),
            "y" to listOf("ÿ"),
            "n" to listOf("ñ"),
            "s" to listOf("ß", "$"),
            "." to listOf("…", "!", "?", ":", ";"),
            "," to listOf(";", "'", "\"", "«", "»"),
            "'" to listOf("’", "\"", "«", "»"),
        )
    }
}
