package fr.essentiel.launcher.calendar

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.essentiel.launcher.Divider
import fr.essentiel.launcher.EmptyText
import fr.essentiel.launcher.Field
import fr.essentiel.launcher.IconAction
import fr.essentiel.launcher.Icons
import fr.essentiel.launcher.ListRow
import fr.essentiel.launcher.Nav
import fr.essentiel.launcher.Palette
import fr.essentiel.launcher.PermissionGate
import fr.essentiel.launcher.PillButton
import fr.essentiel.launcher.Route
import fr.essentiel.launcher.ToolScaffold
import fr.essentiel.launcher.bodyStyle
import fr.essentiel.launcher.rememberContentVersion
import fr.essentiel.launcher.serifStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val FR = Locale.FRANCE
private val CAL_PERMS = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

private fun dayStart(ms: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ms
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun addDays(ms: Long, d: Int) = Calendar.getInstance().apply { timeInMillis = ms; add(Calendar.DAY_OF_YEAR, d) }.timeInMillis
private fun fmt(pattern: String, ms: Long) = SimpleDateFormat(pattern, FR).format(Date(ms))

@Composable
fun CalendarScreen(nav: Nav) {
    val ctx = LocalContext.current
    var selected by remember { mutableLongStateOf(dayStart(System.currentTimeMillis())) }
    var month by remember {
        mutableLongStateOf(Calendar.getInstance().apply { timeInMillis = selected; set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis)
    }
    ToolScaffold("agenda", onBack = nav::back, actions = {
        IconAction(Icons.plus) { nav.go(Route.EventEdit(null, selected)) }
    }) {
        PermissionGate(CAL_PERMS, "Pour afficher et modifier ton agenda, Essentiel a besoin d'y accéder.") {
            val version = rememberContentVersion(CalendarContract.CONTENT_URI)
            // Grille du mois : commence le lundi de la semaine du 1er
            val gridStart = remember(month) {
                Calendar.getInstance().apply {
                    timeInMillis = month
                    val dow = (get(Calendar.DAY_OF_WEEK) + 5) % 7 // lundi = 0
                    add(Calendar.DAY_OF_YEAR, -dow)
                }.timeInMillis
            }
            var events by remember { mutableStateOf<List<CalEvent>>(emptyList()) }
            LaunchedEffect(gridStart, version) {
                events = withContext(Dispatchers.IO) { CalendarData.between(ctx, gridStart, addDays(gridStart, 42)) }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(fmt("MMMM yyyy", month), style = serifStyle(24), modifier = Modifier.weight(1f))
                IconAction(Icons.back, Palette.Ink) {
                    month = Calendar.getInstance().apply { timeInMillis = month; add(Calendar.MONTH, -1) }.timeInMillis
                }
                IconAction(Icons.chevronRight, Palette.Ink) {
                    month = Calendar.getInstance().apply { timeInMillis = month; add(Calendar.MONTH, 1) }.timeInMillis
                }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("l", "m", "m", "j", "v", "s", "d").forEach {
                    Text(it, style = bodyStyle(13, Palette.Muted), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(4.dp))
            val today = dayStart(System.currentTimeMillis())
            val monthNum = Calendar.getInstance().apply { timeInMillis = month }.get(Calendar.MONTH)
            for (week in 0 until 6) {
                Row(Modifier.fillMaxWidth()) {
                    for (d in 0 until 7) {
                        val day = dayStart(addDays(gridStart, week * 7 + d))
                        val next = addDays(day, 1)
                        val inMonth = Calendar.getInstance().apply { timeInMillis = day }.get(Calendar.MONTH) == monthNum
                        val has = events.any { it.begin < next && it.end > day }
                        val isSel = day == selected
                        Column(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1.15f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSel) Palette.Accent else Color.Transparent)
                                .clickable { selected = day },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                fmt("d", day),
                                style = bodyStyle(
                                    16,
                                    when {
                                        isSel -> Color.White
                                        day == today -> Palette.Accent
                                        inMonth -> Palette.Ink
                                        else -> Palette.Muted.copy(alpha = 0.5f)
                                    },
                                    bold = day == today || isSel
                                )
                            )
                            Box(
                                Modifier.size(5.dp).clip(CircleShape)
                                    .background(if (has) (if (isSel) Color.White else Palette.Accent) else Color.Transparent)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Divider()
            Text(fmt("EEEE d MMMM", selected), style = bodyStyle(15, Palette.Accent, bold = true), modifier = Modifier.padding(vertical = 10.dp))
            val dayEvents = events.filter { it.begin < addDays(selected, 1) && it.end > selected }
            if (dayEvents.isEmpty()) {
                Text(
                    "rien de prévu · toucher + pour ajouter", style = bodyStyle(15, Palette.Muted),
                    modifier = Modifier.clickable { nav.go(Route.EventEdit(null, selected)) }
                )
            }
            LazyColumn {
                items(dayEvents, key = { "${it.id}_${it.begin}" }) { ev ->
                    val time = if (ev.allDay) "journée" else fmt("HH:mm", ev.begin) + " – " + fmt("HH:mm", ev.end)
                    ListRow(
                        ev.title,
                        listOf(time, ev.location).filter { it.isNotBlank() }.joinToString(" · "),
                        leading = {
                            Box(Modifier.size(width = 4.dp, height = 34.dp).clip(RoundedCornerShape(2.dp))
                                .background(if (ev.color != 0) Color(ev.color) else Palette.Accent))
                        }
                    ) { nav.go(Route.EventEdit(ev.id, ev.begin)) }
                    Divider()
                }
            }
        }
    }
}

@Composable
fun EventEditScreen(nav: Nav, eventId: Long?, day: Long) {
    val ctx = LocalContext.current
    var loaded by remember { mutableStateOf(eventId == null) }
    var title by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var allDay by remember { mutableStateOf(false) }
    var recurring by remember { mutableStateOf(false) }
    // Par défaut : prochaine heure pleine du jour choisi
    val defaultStart = remember {
        val now = Calendar.getInstance()
        Calendar.getInstance().apply {
            timeInMillis = dayStart(day)
            set(Calendar.HOUR_OF_DAY, (now.get(Calendar.HOUR_OF_DAY) + 1).coerceAtMost(23))
        }.timeInMillis
    }
    var begin by remember { mutableLongStateOf(defaultStart) }
    var end by remember { mutableLongStateOf(defaultStart + 3_600_000L) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (eventId != null) {
            val ev = withContext(Dispatchers.IO) { CalendarData.get(ctx, eventId) }
            if (ev != null) {
                title = ev.title; location = ev.location; allDay = ev.allDay; recurring = ev.recurring
                begin = ev.begin; end = if (ev.allDay) addDays(ev.end, -1) else ev.end
            }
            loaded = true
        }
    }

    fun pickDate(current: Long, onPicked: (Long) -> Unit) {
        val c = Calendar.getInstance().apply { timeInMillis = current }
        DatePickerDialog(ctx, { _, y, m, d ->
            val n = Calendar.getInstance().apply { timeInMillis = current; set(y, m, d) }
            onPicked(n.timeInMillis)
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    fun pickTime(current: Long, onPicked: (Long) -> Unit) {
        val c = Calendar.getInstance().apply { timeInMillis = current }
        TimePickerDialog(ctx, { _, h, min ->
            val n = Calendar.getInstance().apply { timeInMillis = current; set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, min) }
            onPicked(n.timeInMillis)
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
    }

    ToolScaffold(if (eventId == null) "nouvel événement" else "événement", onBack = nav::back, actions = {
        if (eventId != null && !recurring) IconAction(Icons.trash) { confirmDelete = true }
    }) {
        PermissionGate(CAL_PERMS, "Pour modifier ton agenda, Essentiel a besoin d'y accéder.") {
            if (!loaded) return@PermissionGate
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (recurring) {
                    Text(
                        "Événement répétitif : pour éviter de modifier toute la série par erreur, il est en lecture seule ici.",
                        style = bodyStyle(14, Palette.Muted)
                    )
                }
                Field(title, { if (!recurring) title = it }, "titre", Modifier.fillMaxWidth())
                Field(location, { if (!recurring) location = it }, "lieu (facultatif)", Modifier.fillMaxWidth())
                Text(
                    (if (allDay) "●  " else "○  ") + "journée entière",
                    style = bodyStyle(17),
                    modifier = Modifier.clickable(enabled = !recurring) { allDay = !allDay }.padding(vertical = 6.dp)
                )
                DateTimeRow("début", begin, allDay, !recurring,
                    onDate = { pickDate(begin) { nb -> val diff = end - begin; begin = nb; end = nb + diff } },
                    onTime = { pickTime(begin) { nb -> val diff = end - begin; begin = nb; end = nb + diff } })
                DateTimeRow("fin", end, allDay, !recurring,
                    onDate = { pickDate(end) { end = it } },
                    onTime = { pickTime(end) { end = it } })
                if (!recurring) {
                    Spacer(Modifier.height(6.dp))
                    PillButton("enregistrer", modifier = Modifier.fillMaxWidth()) {
                        if (title.isBlank()) {
                            Toast.makeText(ctx, "Indique un titre", Toast.LENGTH_SHORT).show(); return@PillButton
                        }
                        val realEnd = if (allDay) addDays(dayStart(end), 1) else end
                        val realBegin = if (allDay) dayStart(begin) else begin
                        if (realEnd < realBegin) {
                            Toast.makeText(ctx, "La fin est avant le début", Toast.LENGTH_SHORT).show(); return@PillButton
                        }
                        if (CalendarData.save(ctx, eventId, title.trim(), location.trim(), realBegin, realEnd, allDay)) nav.back()
                        else Toast.makeText(ctx, "Impossible d'enregistrer (aucun agenda modifiable ?)", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    if (confirmDelete && eventId != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer l'événement ?") },
            confirmButton = {
                TextButton(onClick = { CalendarData.delete(ctx, eventId); confirmDelete = false; nav.back() }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
            containerColor = Palette.Bg,
        )
    }
}

@Composable
private fun DateTimeRow(label: String, ms: Long, allDay: Boolean, enabled: Boolean, onDate: () -> Unit, onTime: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = bodyStyle(15, Palette.Muted), modifier = Modifier.width(60.dp))
        Chip(fmt("EEE d MMM yyyy", ms), enabled, onDate, Modifier.weight(1f))
        if (!allDay) {
            Spacer(Modifier.width(10.dp))
            Chip(fmt("HH:mm", ms), enabled, onTime)
        }
    }
}

@Composable
private fun Chip(text: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .clip(shape)
            .background(Palette.Card)
            .border(1.dp, Palette.Line, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) { Text(text, style = bodyStyle(16)) }
}
