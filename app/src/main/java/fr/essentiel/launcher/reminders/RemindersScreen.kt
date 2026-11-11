package fr.essentiel.launcher.reminders

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import fr.essentiel.launcher.CardGroup
import fr.essentiel.launcher.Divider
import fr.essentiel.launcher.Fab
import fr.essentiel.launcher.Field
import fr.essentiel.launcher.Icons
import fr.essentiel.launcher.LineIconView
import fr.essentiel.launcher.Nav
import fr.essentiel.launcher.Palette
import fr.essentiel.launcher.PillButton
import fr.essentiel.launcher.ToolScaffold
import fr.essentiel.launcher.bodyStyle
import fr.essentiel.launcher.serifStyle
import java.util.Calendar

private fun at(dayOffset: Int, hour: Int, minute: Int = 0): Long = Calendar.getInstance().apply {
    add(Calendar.DAY_OF_YEAR, dayOffset); set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun dayIndex(ms: Long): Int {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    val t = Calendar.getInstance()
    return (c.get(Calendar.YEAR) - t.get(Calendar.YEAR)) * 366 + c.get(Calendar.DAY_OF_YEAR) - t.get(Calendar.DAY_OF_YEAR)
}

@Composable
fun RemindersScreen(@Suppress("UNUSED_PARAMETER") nav: Nav) {
    val ctx = LocalContext.current
    val version = ReminderStore.version.intValue
    val all = remember(version) { ReminderStore.all(ctx) }
    val now = System.currentTimeMillis()
    val pending = all.filter { !it.done }
    val done = all.filter { it.done }.sortedByDescending { it.time }
    var adding by remember { mutableStateOf(false) }
    var showDone by remember { mutableStateOf(false) }

    val groups = listOf(
        "en retard" to pending.filter { it.time < now },
        "aujourd'hui" to pending.filter { it.time >= now && dayIndex(it.time) == 0 },
        "demain" to pending.filter { it.time >= now && dayIndex(it.time) == 1 },
        "plus tard" to pending.filter { it.time >= now && dayIndex(it.time) > 1 },
    ).filter { it.second.isNotEmpty() }

    Box(Modifier.fillMaxSize()) {
        ToolScaffold(
            "rappels",
            subtitle = if (pending.isEmpty()) "rien à faire" else "${pending.size} à faire",
        ) {
            LazyColumn(Modifier.fillMaxSize()) {
                if (pending.isEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            LineIconView(Icons.check, Palette.Accent, 44.dp)
                            Spacer(Modifier.height(12.dp))
                            Text("Tout est fait.", style = serifStyle(26))
                            Text("Touche + pour ajouter un rappel.", style = bodyStyle(14, Palette.Muted))
                        }
                    }
                }
                groups.forEach { (title, list) ->
                    item(key = "t_$title") {
                        Text(
                            title.uppercase(), style = bodyStyle(13, if (title == "en retard") Palette.Accent else Palette.Muted, bold = true),
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                    }
                    item(key = "g_$title") {
                        CardGroup {
                            list.forEachIndexed { i, r ->
                                ReminderRow(r)
                                if (i < list.lastIndex) Divider()
                            }
                        }
                    }
                }
                if (done.isNotEmpty()) {
                    item(key = "done_title") {
                        Row(Modifier.padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                (if (showDone) "▾ " else "▸ ") + "TERMINÉS (${done.size})", style = bodyStyle(13, Palette.Muted, bold = true),
                                modifier = Modifier.weight(1f).clickable { showDone = !showDone }
                            )
                            if (showDone) Text(
                                "tout effacer", style = bodyStyle(13, Palette.Accent, bold = true),
                                modifier = Modifier.clickable { ReminderStore.clearDone(ctx) }
                            )
                        }
                    }
                    if (showDone) item(key = "done_list") {
                        CardGroup {
                            done.forEachIndexed { i, r ->
                                ReminderRow(r)
                                if (i < done.lastIndex) Divider()
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(100.dp)) }
            }
        }
        Fab(Icons.plus, Modifier.align(Alignment.BottomEnd).padding(22.dp)) { adding = true }
    }

    if (adding) AddReminderDialog(onClose = { adding = false })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReminderRow(r: Reminder) {
    val ctx = LocalContext.current
    val late = !r.done && r.time < System.currentTimeMillis()
    var confirm by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = { confirm = true }).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (r.done) Palette.Accent else Color.Transparent)
                .border(2.dp, if (late) Palette.Accent else Palette.Accent.copy(alpha = 0.7f), CircleShape)
                .clickable { ReminderStore.setDone(ctx, r.id, !r.done) },
            contentAlignment = Alignment.Center,
        ) { if (r.done) LineIconView(Icons.check, Color.White, 15.dp, strokeWidth = 3f) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                r.text,
                style = bodyStyle(17, if (r.done) Palette.Muted else Palette.Ink).copy(
                    textDecoration = if (r.done) TextDecoration.LineThrough else null
                )
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                LineIconView(Icons.bell, if (late) Palette.Accent else Palette.Muted, 13.dp)
                Spacer(Modifier.width(5.dp))
                Text(ReminderStore.label(r.time), style = bodyStyle(13, if (late) Palette.Accent else Palette.Muted, bold = late))
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = Palette.Bg,
            title = { Text("« ${r.text} »") },
            confirmButton = { TextButton(onClick = { ReminderStore.delete(ctx, r.id); confirm = false }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun AddReminderDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("") }
    val nowCal = Calendar.getInstance()
    val inOneHour = remember { System.currentTimeMillis() + 3_600_000L }
    val tonight = remember { if (nowCal.get(Calendar.HOUR_OF_DAY) < 19) at(0, 19) else at(1, 19) }
    val tomorrow = remember { at(1, 9) }
    var time by remember { mutableLongStateOf(inOneHour) }

    fun pickDateThenTime() {
        val c = Calendar.getInstance().apply { timeInMillis = time }
        DatePickerDialog(ctx, { _, y, m, d ->
            val picked = Calendar.getInstance().apply { timeInMillis = time; set(y, m, d) }
            TimePickerDialog(ctx, { _, h, min ->
                picked.set(Calendar.HOUR_OF_DAY, h); picked.set(Calendar.MINUTE, min); picked.set(Calendar.SECOND, 0)
                time = picked.timeInMillis
            }, picked.get(Calendar.HOUR_OF_DAY), picked.get(Calendar.MINUTE), true).show()
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Palette.Bg,
        title = { Text("Nouveau rappel", style = serifStyle(26)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Field(text, { text = it }, "à quoi penser ?", Modifier.fillMaxWidth())
                Text("quand ?", style = bodyStyle(13, Palette.Muted, bold = true))
                val choices = listOf("dans 1 h" to inOneHour, "ce soir 19:00" to tonight, "demain 9:00" to tomorrow)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    choices.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (label, t) -> TimeChip(label, time == t, Modifier.weight(1f)) { time = t } }
                            if (row.size == 1) TimeChip("choisir…", choices.none { it.second == time }, Modifier.weight(1f)) { pickDateThenTime() }
                        }
                    }
                }
                Text("→ " + ReminderStore.label(time), style = bodyStyle(15, Palette.Accent, bold = true))
            }
        },
        confirmButton = {
            PillButton("ajouter") {
                if (text.isNotBlank()) { ReminderStore.add(ctx, text.trim(), time); onClose() }
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Annuler") } },
    )
}

@Composable
private fun TimeChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier.clip(shape).background(if (selected) Palette.Accent else Palette.Card)
            .border(1.dp, if (selected) Palette.Accent else Palette.Line, shape)
            .clickable(onClick = onClick).padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = bodyStyle(14, if (selected) Color.White else Palette.Ink, bold = true)) }
}
