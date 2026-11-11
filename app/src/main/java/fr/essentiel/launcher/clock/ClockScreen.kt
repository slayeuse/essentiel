package fr.essentiel.launcher.clock

import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.essentiel.launcher.Divider
import fr.essentiel.launcher.EmptyText
import fr.essentiel.launcher.Field
import fr.essentiel.launcher.IconAction
import fr.essentiel.launcher.Icons
import fr.essentiel.launcher.Nav
import fr.essentiel.launcher.Palette
import fr.essentiel.launcher.PillButton
import fr.essentiel.launcher.SegmentedTabs
import fr.essentiel.launcher.ToolScaffold
import fr.essentiel.launcher.bodyStyle
import fr.essentiel.launcher.serifStyle
import kotlinx.coroutines.delay

private val DAY_LETTERS = listOf("l", "m", "m", "j", "v", "s", "d")

private fun two(n: Long) = n.toString().padStart(2, '0')

fun formatDuration(ms: Long, withCentis: Boolean = false): String {
    val total = ms.coerceAtLeast(0)
    val h = total / 3_600_000
    val m = (total / 60_000) % 60
    val s = (total / 1000) % 60
    val base = if (h > 0) "$h:${two(m)}:${two(s)}" else "${two(m)}:${two(s)}"
    return if (withCentis) "$base,${two((total / 10) % 100)}" else base
}

private fun daysLabel(days: Set<Int>): String = when {
    days.isEmpty() -> "une fois"
    days.size == 7 -> "tous les jours"
    days == setOf(1, 2, 3, 4, 5) -> "en semaine"
    days == setOf(6, 7) -> "le week-end"
    else -> days.sorted().joinToString(" ") { listOf("lun", "mar", "mer", "jeu", "ven", "sam", "dim")[it - 1] }
}

@Composable
fun ClockScreen(nav: Nav) {
    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Alarm?>(null) }
    ToolScaffold("horloge", onBack = nav::back, actions = {
        if (tab == 0) IconAction(Icons.plus) {
            editing = Alarm(System.currentTimeMillis(), 7, 0, setOf(1, 2, 3, 4, 5), "", true)
        }
    }) {
        SegmentedTabs(listOf("réveils", "minuteur", "chrono"), tab) { tab = it }
        Spacer(Modifier.height(16.dp))
        when (tab) {
            0 -> AlarmsTab(onEdit = { editing = it })
            1 -> TimerTab()
            else -> StopwatchTab()
        }
    }
    editing?.let { a -> AlarmEditDialog(a, onDismiss = { editing = null }) }
}

@Composable
private fun AlarmsTab(onEdit: (Alarm) -> Unit) {
    val ctx = LocalContext.current
    val version = AlarmStore.version.intValue
    val alarms = remember(version) { AlarmStore.all(ctx) }
    if (alarms.isEmpty()) { EmptyText("Aucun réveil. Touche + pour en ajouter un."); return }
    LazyColumn {
        items(alarms, key = { it.id }) { a ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(24.dp))
                    .background(if (a.enabled) Color(0xFFF2EFEA) else Palette.Card)
                    .border(1.dp, if (a.enabled) Palette.Accent.copy(alpha = 0.45f) else Palette.Line, RoundedCornerShape(24.dp))
                    .clickable { onEdit(a) }.padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${two(a.hour.toLong())}:${two(a.minute.toLong())}",
                        style = serifStyle(40, if (a.enabled) Palette.Ink else Palette.Muted)
                    )
                    Text(
                        listOf(a.label, daysLabel(a.days)).filter { it.isNotBlank() }.joinToString(" · "),
                        style = bodyStyle(14, Palette.Muted)
                    )
                }
                Switch(a.enabled) {
                    AlarmStore.save(ctx, a.copy(enabled = !a.enabled))
                    if (!a.enabled) {
                        val next = AlarmScheduler.nextTime(a) - System.currentTimeMillis()
                        Toast.makeText(ctx, "Sonne dans ${untilText(next)}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}

private fun untilText(ms: Long): String {
    val min = ms / 60_000
    val h = min / 60
    return if (h > 0) "$h h ${min % 60} min" else "${min.coerceAtLeast(1)} min"
}

@Composable
fun Switch(on: Boolean, onToggle: () -> Unit) {
    Box(
        Modifier
            .size(width = 54.dp, height = 32.dp)
            .clip(RoundedCornerShape(50))
            .background(if (on) Palette.Accent else Palette.Card)
            .border(1.dp, if (on) Palette.Accent else Palette.Line, RoundedCornerShape(50))
            .clickable(onClick = onToggle)
            .padding(4.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(if (on) Color.White else Palette.Line))
    }
}

@Composable
private fun AlarmEditDialog(initial: Alarm, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var hour by remember { mutableIntStateOf(initial.hour) }
    var minute by remember { mutableIntStateOf(initial.minute) }
    var days by remember { mutableStateOf(initial.days) }
    var label by remember { mutableStateOf(initial.label) }
    val exists = remember { AlarmStore.get(ctx, initial.id) != null }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Bg,
        title = null,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "${two(hour.toLong())}:${two(minute.toLong())}",
                    style = serifStyle(60), textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable {
                        TimePickerDialog(ctx, { _, h, m -> hour = h; minute = m }, hour, minute, true).show()
                    }
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DAY_LETTERS.forEachIndexed { i, l ->
                        val d = i + 1
                        val on = d in days
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (on) Palette.Accent else Palette.Card)
                                .border(1.dp, if (on) Palette.Accent else Palette.Line, CircleShape)
                                .clickable { days = if (on) days - d else days + d },
                            contentAlignment = Alignment.Center,
                        ) { Text(l, style = bodyStyle(14, if (on) Color.White else Palette.Ink, bold = true)) }
                    }
                }
                Text(daysLabel(days), style = bodyStyle(13, Palette.Muted))
                Field(label, { label = it }, "nom du réveil (facultatif)", Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = initial.copy(hour = hour, minute = minute, days = days, label = label.trim(), enabled = true)
                AlarmStore.save(ctx, a)
                Toast.makeText(ctx, "Sonne dans ${untilText(AlarmScheduler.nextTime(a) - System.currentTimeMillis())}", Toast.LENGTH_SHORT).show()
                onDismiss()
            }) { Text("Enregistrer") }
        },
        dismissButton = {
            Row {
                if (exists) TextButton(onClick = { AlarmStore.delete(ctx, initial.id); onDismiss() }) { Text("Supprimer") }
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
        },
    )
}

@Composable
private fun TimerTab() {
    val ctx = LocalContext.current
    val version = TimerState.version.intValue
    val state = remember(version) { TimerState.load(ctx) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.running) { while (state.running) { now = System.currentTimeMillis(); delay(250) } }

    var minutes by remember { mutableIntStateOf(5) }
    var seconds by remember { mutableIntStateOf(0) }

    val active = state.running || state.remaining > 0
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(30.dp))
        if (!active) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Stepper(minutes, "min", 0..180) { minutes = it }
                Text(":", style = serifStyle(60), modifier = Modifier.padding(horizontal = 10.dp))
                Stepper(seconds, "s", 0..59, step = 5) { seconds = it }
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 5, 10, 30).forEach { m ->
                    Text(
                        "$m min", style = bodyStyle(14, if (minutes == m && seconds == 0) Color.White else Palette.Ink, bold = true),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (minutes == m && seconds == 0) Palette.Accent else Palette.Card)
                            .border(1.dp, Palette.Line, RoundedCornerShape(50))
                            .clickable { minutes = m; seconds = 0 }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            PillButton("démarrer", modifier = Modifier.fillMaxWidth()) {
                val ms = (minutes * 60L + seconds) * 1000L
                if (ms > 0) TimerState.start(ctx, ms)
            }
        } else {
            val left = if (state.running) state.endAt - now else state.remaining
            // Anneau de progression
            val frac = if (state.total > 0) (left.toFloat() / state.total).coerceIn(0f, 1f) else 0f
            Box(Modifier.size(270.dp), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    val sw = 14.dp.toPx()
                    val inset = sw / 2
                    val arcSize = androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw)
                    val tl = androidx.compose.ui.geometry.Offset(inset, inset)
                    drawArc(Palette.Card, 0f, 360f, false, tl, arcSize, style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
                    drawArc(
                        Palette.Accent, -90f, 360f * frac, false, tl, arcSize,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatDuration(left), style = serifStyle(60))
                    Text(if (state.running) "en cours" else "en pause", style = bodyStyle(14, Palette.Muted))
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "+1 min", style = bodyStyle(16, Palette.Accent, bold = true),
                modifier = Modifier.clickable {
                    if (state.running) TimerState.start(ctx, state.endAt - System.currentTimeMillis() + 60_000, state.total + 60_000)
                    else TimerState.save(ctx, state.copy(remaining = state.remaining + 60_000, total = state.total + 60_000))
                }.padding(10.dp)
            )
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("réinitialiser", filled = false, modifier = Modifier.weight(1f)) { TimerState.reset(ctx) }
                PillButton(if (state.running) "pause" else "reprendre", modifier = Modifier.weight(1f)) {
                    if (state.running) TimerState.pause(ctx) else TimerState.start(ctx, state.remaining, state.total)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Stepper(value: Int, unit: String, range: IntRange, step: Int = 1, onChange: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "+", style = serifStyle(30, Palette.Accent),
            modifier = Modifier.clickable { onChange((value + step).coerceAtMost(range.last)) }.padding(8.dp)
        )
        Text(two(value.toLong()), style = serifStyle(64))
        Text(unit, style = bodyStyle(13, Palette.Muted))
        Text(
            "–", style = serifStyle(30, Palette.Accent),
            modifier = Modifier.clickable { onChange((value - step).coerceAtLeast(range.first)) }.padding(8.dp)
        )
    }
}

@Composable
private fun StopwatchTab() {
    val ctx = LocalContext.current
    val version = Stopwatch.version.intValue
    val running = remember(version) { Stopwatch.running(ctx) }
    val laps = remember(version) { Stopwatch.laps(ctx) }
    var elapsed by remember { mutableLongStateOf(Stopwatch.elapsed(ctx)) }
    LaunchedEffect(running, version) {
        elapsed = Stopwatch.elapsed(ctx)
        while (running) { elapsed = Stopwatch.elapsed(ctx); delay(31) }
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(30.dp))
        Text(formatDuration(elapsed, withCentis = true), style = serifStyle(64))
        Spacer(Modifier.height(20.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(laps.indices.reversed().toList()) { i ->
                val lapTime = laps[i] - (if (i > 0) laps[i - 1] else 0L)
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Text("tour ${i + 1}", style = bodyStyle(16, Palette.Muted), modifier = Modifier.weight(1f))
                    Text(formatDuration(lapTime, true), style = bodyStyle(16))
                    Spacer(Modifier.width(16.dp))
                    Text(formatDuration(laps[i], true), style = bodyStyle(16, Palette.Muted))
                }
                Divider()
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (running) PillButton("tour", filled = false, modifier = Modifier.weight(1f)) { Stopwatch.lap(ctx) }
            else PillButton("réinitialiser", filled = false, modifier = Modifier.weight(1f)) { Stopwatch.reset(ctx) }
            PillButton(if (running) "pause" else "démarrer", modifier = Modifier.weight(1f)) { Stopwatch.startPause(ctx) }
        }
        Spacer(Modifier.height(24.dp))
    }
}
