package fr.essentiel.launcher.keyboard

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.essentiel.launcher.Icons
import fr.essentiel.launcher.LineIcon
import fr.essentiel.launcher.LineIconView
import fr.essentiel.launcher.Palette
import fr.essentiel.launcher.bodyStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val KbBg = Color(0xFFDCD8D1)
private val KeyBg = Color(0xFFF6F4F0)
private val SpecialBg = Color(0xFFC9C4BB)
private val LocalKeyH = androidx.compose.runtime.compositionLocalOf { 50.dp }

/** Petite vibration (si activée dans les réglages). */
private fun buzz(view: android.view.View) {
    if (KbPrefs.vibration(view.context)) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
}

private val LETTERS = listOf("azertyuiop", "qsdfghjklm", "wxcvbn'")
private val SYMBOLS = listOf("1234567890", "@#€_&-+()/", "*\"':;!?")
private val SYMBOLS2 = listOf("~`|•√π÷×¶∆", "£¥$¢^°={}\\", "%©®™✓[]<>")
private val EMOJIS = (
    "😀😂🥲😊😍🥰😘😎🤔🙄😴😭😡🥳😇🤗🤩😅😉🙃😬😱🤯🥺👍👎👏🙏💪👌✌️🤞👋🙌" +
        "❤️🧡💛💚💙💜🖤🤍💔💯✨🔥⭐🌞🌧️❄️🌈☕🍕🍔🍟🍰🍷🍺🎉🎁🎂⚽🏀🚗🚆✈️🏠📱💻📷🎵📅⏰✅❌⚠️❓" +
        "🐶🐱🐭🐰🦊🐻🐼🐸🐵🐔🐧🐦🌸🌻🌳🍀🍎🍓🍌🥑🥖🧀🥐"
    ).let { s -> Regex("\\X").findAll(s).map { it.value }.toList() }

@Composable
fun KeyboardUI(kb: EssentielKeyboard) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val v = KbPrefs.version.intValue
    val tall = androidx.compose.runtime.remember(v) { KbPrefs.tall(ctx) }
    val numberRow = androidx.compose.runtime.remember(v) { KbPrefs.numberRow(ctx) }
    androidx.compose.runtime.CompositionLocalProvider(LocalKeyH provides if (tall) 56.dp else 48.dp) {
    Column(Modifier.fillMaxWidth().background(KbBg).padding(horizontal = 3.dp, vertical = 4.dp)) {
        TopBar(kb)
        when (kb.mode) {
            KbMode.EMOJI -> EmojiPanel(kb)
            KbMode.NUMBERS, KbMode.PHONE -> NumberPad(kb)
            else -> {
                val rows = when (kb.mode) {
                    KbMode.SYMBOLS -> SYMBOLS
                    KbMode.SYMBOLS2 -> SYMBOLS2
                    else -> LETTERS
                }
                val upper = kb.mode == KbMode.LETTERS && kb.shift != Shift.OFF
                if (numberRow && kb.mode == KbMode.LETTERS) {
                    KeyRow { "1234567890".forEach { c -> CharKey(kb, c.toString(), false) } }
                }
                // Rangée 1 et 2
                KeyRow { rows[0].forEach { c -> CharKey(kb, c.toString(), upper) } }
                KeyRow { rows[1].forEach { c -> CharKey(kb, c.toString(), upper) } }
                // Rangée 3 : maj / symboles suivants, lettres, effacer
                KeyRow {
                    if (kb.mode == KbMode.LETTERS) {
                        SpecialKey(
                            Modifier.weight(1.5f),
                            icon = shiftIcon,
                            highlight = kb.shift != Shift.OFF,
                            label = if (kb.shift == Shift.LOCK) "⇪" else null,
                        ) { kb.toggleShift() }
                    } else {
                        SpecialKey(Modifier.weight(1.5f), text = if (kb.mode == KbMode.SYMBOLS) "=\\<" else "?123") {
                            kb.mode = if (kb.mode == KbMode.SYMBOLS) KbMode.SYMBOLS2 else KbMode.SYMBOLS
                        }
                    }
                    rows[2].forEach { c -> CharKey(kb, c.toString(), upper) }
                    RepeatKey(Modifier.weight(1.5f), onSwipeLeft = { kb.deleteWord() }) { kb.backspace() }
                }
                // Rangée du bas
                KeyRow {
                    SpecialKey(Modifier.weight(1.4f), text = if (kb.mode == KbMode.LETTERS) "?123" else "ABC") {
                        kb.mode = if (kb.mode == KbMode.LETTERS) KbMode.SYMBOLS else KbMode.LETTERS
                    }
                    SpecialKey(Modifier.weight(1f), text = "☺") { kb.mode = KbMode.EMOJI }
                    CharKey(kb, ",", false, weight = 1f)
                    SpaceKey(kb, Modifier.weight(4.2f))
                    CharKey(kb, ".", false, weight = 1f)
                    EnterKey(kb, Modifier.weight(1.8f))
                }
            }
        }
    }
    }
}

private val shiftIcon = LineIcon(listOf("M12 4l-8 8h5v8h6v-8h5z"))

@Composable
private fun TopBar(kb: EssentielKeyboard) {
    Row(Modifier.fillMaxWidth().height(42.dp), verticalAlignment = Alignment.CenterVertically) {
        // Changer de clavier (Gboard…)
        Box(
            Modifier.width(44.dp).fillMaxHeight().clip(RoundedCornerShape(12.dp)).clickable { kb.showPicker() },
            contentAlignment = Alignment.Center,
        ) { LineIconView(Icons.globe, Palette.Muted, 20.dp) }
        Row(Modifier.weight(1f).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            val items = if (kb.accents.isNotEmpty()) kb.accents else kb.suggestions
            items.forEachIndexed { i, s ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp))
                        .clickable { if (kb.accents.isNotEmpty()) kb.type(s) else kb.pickSuggestion(s) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        s, style = bodyStyle(17, if (i == 0 && kb.accents.isEmpty()) Palette.Accent else Palette.Ink, bold = i == 0),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                if (i < items.lastIndex) Box(Modifier.width(1.dp).height(20.dp).background(Palette.Line))
            }
        }
        Box(
            Modifier.width(44.dp).fillMaxHeight().clip(RoundedCornerShape(12.dp)).clickable { kb.hide() },
            contentAlignment = Alignment.Center,
        ) { LineIconView(chevronDown, Palette.Muted, 20.dp) }
    }
}

private val chevronDown = LineIcon(listOf("M6 9l6 6 6-6"))

@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(LocalKeyH.current + 8.dp).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        content = content,
    )
}

@Composable
private fun keyModifier(bg: Color, modifier: Modifier): Modifier =
    modifier.fillMaxHeight().shadow(1.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)).background(bg)

@Composable
private fun RowScope.CharKey(kb: EssentielKeyboard, c: String, upper: Boolean, weight: Float = 1f) {
    val view = LocalView.current
    var pressed by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Box(
        keyModifier(if (pressed) SpecialBg else KeyBg, Modifier.weight(weight)).pointerInput(c) {
            detectTapGestures(
                onPress = { pressed = true; buzz(view); tryAwaitRelease(); pressed = false },
                onTap = { kb.type(c) },
                onLongPress = { kb.showAccents(c) },
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        Text(if (upper) c.uppercase() else c, style = bodyStyle(if (pressed) 25 else 21, if (pressed) Palette.Accent else Palette.Ink))
    }
}

@Composable
private fun SpecialKey(
    modifier: Modifier,
    text: String? = null,
    icon: LineIcon? = null,
    highlight: Boolean = false,
    label: String? = null,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    Box(
        keyModifier(if (highlight) Palette.Accent else SpecialBg, modifier)
            .clickable { buzz(view); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        when {
            label != null -> Text(label, style = bodyStyle(20, if (highlight) Color.White else Palette.Ink, bold = true))
            icon != null -> LineIconView(icon, if (highlight) Color.White else Palette.Ink, 22.dp)
            text != null -> Text(text, style = bodyStyle(15, Palette.Ink, bold = true))
        }
    }
}

/** Touche effacer : maintenir pour effacer en continu. */
@Composable
private fun RepeatKey(modifier: Modifier, onSwipeLeft: (() -> Unit)? = null, action: () -> Unit) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    Box(
        keyModifier(SpecialBg, modifier).pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                buzz(view)
                action()
                val job = scope.launch {
                    delay(420)
                    while (true) { action(); delay(55) }
                }
                var dx = 0f
                var wordDeleted = false
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    dx += change.positionChange().x
                    if (!wordDeleted && dx < -60f * density) {
                        job.cancel(); wordDeleted = true
                        onSwipeLeft?.invoke()
                    }
                }
                job.cancel()
            }
        },
        contentAlignment = Alignment.Center,
    ) { LineIconView(Icons.backspace, Palette.Ink, 22.dp) }
}

/** Espace : toucher = espace, glisser = déplacer le curseur, appui long = changer de clavier. */
@Composable
private fun SpaceKey(kb: EssentielKeyboard, modifier: Modifier) {
    val stepPx = with(LocalDensity.current) { 14.dp.toPx() }
    val view = LocalView.current
    Box(
        keyModifier(KeyBg, modifier).pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val start = System.currentTimeMillis()
                var acc = 0f
                var moved = false
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    acc += change.positionChange().x
                    val steps = (acc / stepPx).toInt()
                    if (steps != 0) {
                        moved = true
                        kb.moveCursor(steps)
                        acc -= steps * stepPx
                        change.consume()
                    }
                }
                if (!moved) {
                    if (System.currentTimeMillis() - start > 600) kb.showPicker()
                    else { buzz(view); kb.type(" ") }
                }
            }
        },
        contentAlignment = Alignment.Center,
    ) { Text("français", style = bodyStyle(13, Palette.Muted)) }
}

@Composable
private fun EnterKey(kb: EssentielKeyboard, modifier: Modifier) {
    val view = LocalView.current
    Box(
        keyModifier(Palette.Accent, modifier).clickable { buzz(view); kb.enter() },
        contentAlignment = Alignment.Center,
    ) {
        if (kb.enterLabel == "↵") LineIconView(enterIcon, Color.White, 22.dp)
        else Text(kb.enterLabel, style = bodyStyle(14, Color.White, bold = true))
    }
}

private val enterIcon = LineIcon(listOf("M9 10l-5 5 5 5", "M20 4v7a4 4 0 0 1-4 4H4"))

@Composable
private fun EmojiPanel(kb: EssentielKeyboard) {
    Column {
        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            modifier = Modifier.fillMaxWidth().height(LocalKeyH.current * 3 + 24.dp),
        ) {
            items(EMOJIS) { e ->
                Box(
                    Modifier.height(44.dp).clip(RoundedCornerShape(10.dp)).clickable { kb.type(e) },
                    contentAlignment = Alignment.Center,
                ) { Text(e, style = bodyStyle(24)) }
            }
        }
        KeyRow {
            SpecialKey(Modifier.weight(1.5f), text = "ABC") { kb.mode = KbMode.LETTERS }
            SpaceKey(kb, Modifier.weight(5f))
            RepeatKey(Modifier.weight(1.5f), onSwipeLeft = { kb.deleteWord() }) { kb.backspace() }
        }
    }
}

@Composable
private fun NumberPad(kb: EssentielKeyboard) {
    val phone = kb.mode == KbMode.PHONE
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"),
        if (phone) listOf("*", "0", "#") else listOf(",", "0", "."))
    rows.forEachIndexed { i, r ->
        KeyRow {
            r.forEach { c -> CharKey(kb, c, false) }
            when (i) {
                0 -> RepeatKey(Modifier.weight(1f)) { kb.backspace() }
                1 -> if (phone) CharKey(kb, "+", false) else CharKey(kb, "-", false)
                2 -> SpecialKey(Modifier.weight(1f), text = "ABC") { kb.mode = KbMode.LETTERS }
                else -> EnterKey(kb, Modifier.weight(1f))
            }
        }
    }
}

@Suppress("unused")
private fun dpSum(a: Dp, b: Dp) = a + b
