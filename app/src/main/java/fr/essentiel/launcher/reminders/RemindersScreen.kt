package fr.essentiel.launcher.reminders

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import fr.essentiel.launcher.Divider
import fr.essentiel.launcher.EmptyText
import fr.essentiel.launcher.Field
import fr.essentiel.launcher.IconAction
import fr.essentiel.launcher.Icons
import fr.essentiel.launcher.LineIconView
import fr.essentiel.launcher.Nav
import fr.essentiel.launcher.Palette
import fr.essentiel.launcher.PillButton
import fr.essentiel.launcher.SectionTitle
import fr.essentiel.launcher.ToolScaffold
import fr.essentiel.launcher.bodyStyle
import java.util.Calendar

private fun nextHour(): Long = Calendar.getInstance().apply {
    add(Calendar.HOUR_OF_DAY, 1); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

@Composable
fun RemindersScreen(nav: Nav) {
    val ctx = LocalContext.current
    val version = ReminderStore.version.intValue
    val all = remember(version) { ReminderStore.all(ctx) }
    val pending = all.filter { !it.done }
    val done = all.filter { it.done }.sortedByDescending { it.time }

    var text by remember { mutableStateOf("") }
    var time by remember { mutableLongStateOf(nextHour()) }

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

    ToolScaffold("rappels", onBack = nav::back) {
        Field(text, { text = it }, "nouveau rappel…", Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Palette.Card)
                    .border(1.dp, Palette.Line, RoundedCornerShape(16.dp))
                    .clickable { pickDateThenTime() }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LineIconView(Icons.clock, Palette.Accent, 18.dp)
                Spacer(Modifier.width(8.dp))
                Text(ReminderStore.label(time), style = bodyStyle(15))
            }
            Spacer(Modifier.weight(1f))
            PillButton("ajouter") {
                if (text.isNotBlank()) {
                    ReminderStore.add(ctx, text.trim(), time)
                    text = ""; time = nextHour()
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn {
            if (pending.isEmpty()) item { EmptyText("Aucun rappel à venir.") }
            items(pending, key = { "p" + it.id }) { r -> ReminderRow(r) }
            if (done.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle("terminés", Modifier.weight(1f))
                        Text(
                            "tout effacer", style = bodyStyle(13, Palette.Muted, bold = true),
                            modifier = Modifier.clickable { ReminderStore.clearDone(ctx) }.padding(top = 12.dp)
                        )
                    }
                }
                items(done, key = { "d" + it.id }) { r -> ReminderRow(r) }
            }
        }
    }
}

@Composable
private fun ReminderRow(r: Reminder) {
    val ctx = LocalContext.current
    val late = !r.done && r.time < System.currentTimeMillis()
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (r.done) Palette.Accent else Color.Transparent)
                .border(2.dp, Palette.Accent, CircleShape)
                .clickable { ReminderStore.setDone(ctx, r.id, !r.done) },
            contentAlignment = Alignment.Center,
        ) { if (r.done) LineIconView(Icons.check, Color.White, 16.dp, strokeWidth = 3f) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                r.text,
                style = bodyStyle(17, if (r.done) Palette.Muted else Palette.Ink).copy(
                    textDecoration = if (r.done) TextDecoration.LineThrough else null
                )
            )
            Text(
                ReminderStore.label(r.time),
                style = bodyStyle(13, if (late) Palette.Accent else Palette.Muted, bold = late)
            )
        }
        IconAction(Icons.trash, Palette.Muted, 20.dp) { ReminderStore.delete(ctx, r.id) }
    }
    Divider()
}
