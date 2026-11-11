package fr.essentiel.launcher.calendar

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.essentiel.launcher.CardGroup
import fr.essentiel.launcher.Divider
import fr.essentiel.launcher.Fab
import fr.essentiel.launcher.Field
import fr.essentiel.launcher.IconAction
import fr.essentiel.launcher.Icons
import fr.essentiel.launcher.LineIconView
import fr.essentiel.launcher.Nav
import fr.essentiel.launcher.Palette
import fr.essentiel.launcher.PermissionGate
import fr.essentiel.launcher.PillButton
import fr.essentiel.launcher.Route
import fr.essentiel.launcher.ToolScaffold
import fr.essentiel.launcher.bodyStyle
import fr.essentiel.launcher.clock.Switch
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
private fun addMonths(ms: Long, m: Int) = Calendar.getInstance().apply { timeInMillis = ms; add(Calendar.MONTH, m) }.timeInMillis
private fun monthStart(ms: Long) = Calendar.getInstance().apply { timeInMillis = dayStart(ms); set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
private fun fmt(pattern: String, ms: Long) = SimpleDateFormat(pattern, FR).format(Date(ms))
private fun eventColor(c: Int) = if (c != 0) Color(c) else Palette.Accent

@Composable
fun CalendarScreen(nav: Nav) {
    val ctx = LocalContext.current
    val today = dayStart(System.currentTimeMillis())
    var selected by remember { mutableLongStateOf(today) }
    var month by remember { mutableLongStateOf(monthStart(today)) }

    Box(Modifier.fillMaxSize()) {
        ToolScaffold(
            fmt("MMMM", month),
            subtitle = fmt("yyyy", month),
            actions = {
                if (month != monthStart(today) || selected != today) {
                    Text(
                        "aujourd'hui", style = bodyStyle(13, Palette.Accent, bold = true),
                        modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, Palette.Accent, RoundedCornerShape(50))
                            .clickable { selected = today; month = monthStart(today) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                IconAction(Icons.back, Palette.Ink) { month = addMonths(month, -1) }
                IconAction(Icons.chevronRight, Palette.Ink) { month = addMonths(month, 1) }
            }
        ) {
            PermissionGate(CAL_PERMS, "Pour afficher et modifier ton agenda, Essentiel a besoin d'y accéder.") {
                val version = rememberContentVersion(CalendarContract.CONTENT_URI)
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
                val monthNum = Calendar.getInstance().apply { timeInMillis = month }.get(Calendar.MONTH)

                // ----- Grille du mois (glisser à gauche / droite pour changer de mois) -----
                var drag by remember { mutableStateOf(0f) }
                CardGroup(
                    Modifier.pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { drag = 0f },
                            onDragEnd = {
                                if (drag < -120f) month = addMonths(month, 1)
                                if (drag > 120f) month = addMonths(month, -1)
                            },
                            onHorizontalDrag = { _, d -> drag += d },
                        )
                    }
                ) {
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp)) {
                        listOf("lun", "mar", "mer", "jeu", "ven", "sam", "dim").forEach {
                            Text(it, style = bodyStyle(12, Palette.Muted, bold = true), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                        }
                    }
                    for (week in 0 until 6) {
                        Row(Modifier.fillMaxWidth()) {
                            for (d in 0 until 7) {
                                val day = dayStart(addDays(gridStart, week * 7 + d))
                                val next = addDays(day, 1)
                                val inMonth = Calendar.getInstance().apply { timeInMillis = day }.get(Calendar.MONTH) == monthNum
                                val dayEvents = events.filter { it.begin < next && it.end > day }
                                val isSel = day == selected
                                val isToday = day == today
                                Column(
                                    Modifier.weight(1f).aspectRatio(1f).padding(2.dp)
                                        .clip(CircleShape)
                                        .background(if (isSel) Palette.Accent else Color.Transparent)
                                        .border(if (isToday && !isSel) 1.5.dp else 0.dp, if (isToday && !isSel) Palette.Accent else Color.Transparent, CircleShape)
                                        .clickable { selected = day; if (!inMonth) month = monthStart(day) },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        fmt("d", day),
                                        style = bodyStyle(
                                            16,
                                            when {
                                                isSel -> Color.White
                                                isToday -> Palette.Accent
                                                inMonth -> Palette.Ink
                                                else -> Palette.Muted.copy(alpha = 0.45f)
                                            },
                                            bold = isSel || isToday
                                        )
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(6.dp)) {
                                        dayEvents.take(3).forEach { ev ->
                                            Box(
                                                Modifier.size(5.dp).clip(CircleShape)
                                                    .background(if (isSel) Color.White else eventColor(ev.color))
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }

                // ----- Événements du jour choisi -----
                Spacer(Modifier.height(16.dp))
                val dayLabel = when (selected) {
                    today -> "aujourd'hui"
                    addDays(today, 1) -> "demain"
                    addDays(today, -1) -> "hier"
                    else -> fmt("EEEE d MMMM", selected)
                }
                Text(dayLabel, style = serifStyle(24))
                Spacer(Modifier.height(8.dp))
                val dayEvents = events.filter { it.begin < addDays(selected, 1) && it.end > selected }
                if (dayEvents.isEmpty()) {
                    Text(
                        "Rien de prévu. Touche + pour ajouter un événement.", style = bodyStyle(15, Palette.Muted),
                        modifier = Modifier.clickable { nav.go(Route.EventEdit(null, selected)) }.padding(vertical = 8.dp)
                    )
                }
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(dayEvents, key = { "${it.id}_${it.begin}" }) { ev ->
                        val shape = RoundedCornerShape(20.dp)
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(shape)
                                .background(eventColor(ev.color).copy(alpha = 0.10f))
                                .clickable { nav.go(Route.EventEdit(ev.id, ev.begin)) }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(width = 5.dp, height = 42.dp).clip(RoundedCornerShape(3.dp)).background(eventColor(ev.color)))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.width(62.dp)) {
                                if (ev.allDay) Text("journée", style = bodyStyle(14, bold = true))
                                else {
                                    Text(fmt("HH:mm", ev.begin), style = bodyStyle(15, bold = true))
                                    Text(fmt("HH:mm", ev.end), style = bodyStyle(13, Palette.Muted))
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(ev.title, style = bodyStyle(17), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (ev.location.isNotBlank()) Text(ev.location, style = bodyStyle(13, Palette.Muted), maxLines = 1)
                            }
                        }
                    }
                    item { Spacer(Modifier.height(90.dp)) }
                }
            }
        }
        Fab(Icons.plus, Modifier.align(Alignment.BottomEnd).padding(22.dp)) { nav.go(Route.EventEdit(null, selected)) }
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
    var calendars by remember { mutableStateOf<List<CalendarData.Cal>>(emptyList()) }
    var calendarId by remember { mutableStateOf<Long?>(null) }
    var pickCalendar by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        calendars = withContext(Dispatchers.IO) { CalendarData.writableCalendars(ctx) }
        if (eventId != null) {
            val ev = withContext(Dispatchers.IO) { CalendarData.get(ctx, eventId) }
            if (ev != null) {
                title = ev.title; location = ev.location; allDay = ev.allDay; recurring = ev.recurring
                begin = ev.begin; end = if (ev.allDay) addDays(ev.end, -1) else ev.end
            }
            calendarId = withContext(Dispatchers.IO) { CalendarData.calendarOf(ctx, eventId) }
            loaded = true
        } else {
            calendarId = calendars.firstOrNull()?.id
        }
    }

    fun pickDate(current: Long, onPicked: (Long) -> Unit) {
        val c = Calendar.getInstance().apply { timeInMillis = current }
        DatePickerDialog(ctx, { _, y, m, d ->
            onPicked(Calendar.getInstance().apply { timeInMillis = current; set(y, m, d) }.timeInMillis)
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    fun pickTime(current: Long, onPicked: (Long) -> Unit) {
        val c = Calendar.getInstance().apply { timeInMillis = current }
        TimePickerDialog(ctx, { _, h, min ->
            onPicked(Calendar.getInstance().apply { timeInMillis = current; set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, min) }.timeInMillis)
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
    }

    ToolScaffold(if (eventId == null) "nouvel événement" else "événement", actions = {
        if (eventId != null && !recurring) IconAction(Icons.trash) { confirmDelete = true }
    }) {
        PermissionGate(CAL_PERMS, "Pour modifier ton agenda, Essentiel a besoin d'y accéder.") {
            if (!loaded) return@PermissionGate
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (recurring) {
                    Text(
                        "Événement répétitif : il est en lecture seule ici pour ne pas modifier toute la série par erreur.",
                        style = bodyStyle(14, Palette.Muted)
                    )
                }
                Field(title, { if (!recurring) title = it }, "titre", Modifier.fillMaxWidth())
                Field(location, { if (!recurring) location = it }, "lieu (facultatif)", Modifier.fillMaxWidth())

                CardGroup {
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("journée entière", style = bodyStyle(16), modifier = Modifier.weight(1f))
                        Switch(allDay) { if (!recurring) allDay = !allDay }
                    }
                    Divider()
                    DateTimeRow("début", begin, allDay, !recurring,
                        onDate = { pickDate(begin) { nb -> val diff = end - begin; begin = nb; end = nb + diff } },
                        onTime = { pickTime(begin) { nb -> val diff = end - begin; begin = nb; end = nb + diff } })
                    Divider()
                    DateTimeRow("fin", end, allDay, !recurring,
                        onDate = { pickDate(end) { end = it } },
                        onTime = { pickTime(end) { end = it } })
                }

                // Choix de l'agenda
                val cal = calendars.firstOrNull { it.id == calendarId }
                CardGroup {
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !recurring && calendars.size > 1) { pickCalendar = true }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(eventColor(cal?.color ?: 0)))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(cal?.name ?: "agenda du téléphone (créé automatiquement)", style = bodyStyle(16))
                            if (cal != null && cal.account.isNotBlank()) Text(cal.account, style = bodyStyle(13, Palette.Muted))
                        }
                        if (calendars.size > 1) Text("changer", style = bodyStyle(13, Palette.Accent, bold = true))
                    }
                }

                if (!recurring) {
                    PillButton("enregistrer", modifier = Modifier.fillMaxWidth()) {
                        if (title.isBlank()) {
                            Toast.makeText(ctx, "Indique un titre", Toast.LENGTH_SHORT).show(); return@PillButton
                        }
                        val realEnd = if (allDay) addDays(dayStart(end), 1) else end
                        val realBegin = if (allDay) dayStart(begin) else begin
                        if (realEnd < realBegin) {
                            Toast.makeText(ctx, "La fin est avant le début", Toast.LENGTH_SHORT).show(); return@PillButton
                        }
                        if (CalendarData.save(ctx, eventId, title.trim(), location.trim(), realBegin, realEnd, allDay, calendarId)) nav.back()
                        else Toast.makeText(ctx, "Impossible d'enregistrer : ${CalendarData.lastError}", Toast.LENGTH_LONG).show()
                    }
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }

    if (pickCalendar) {
        AlertDialog(
            onDismissRequest = { pickCalendar = false },
            containerColor = Palette.Bg,
            title = { Text("Agenda", style = serifStyle(24)) },
            text = {
                Column {
                    calendars.forEach { c ->
                        Row(
                            Modifier.fillMaxWidth().clickable { calendarId = c.id; pickCalendar = false }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(eventColor(c.color)))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.name, style = bodyStyle(16, bold = c.id == calendarId))
                                if (c.account.isNotBlank()) Text(c.account, style = bodyStyle(12, Palette.Muted))
                            }
                            if (c.id == calendarId) LineIconView(Icons.check, Palette.Accent, 18.dp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickCalendar = false }) { Text("Fermer") } },
        )
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
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = bodyStyle(16), modifier = Modifier.weight(1f))
        Chip(fmt("EEE d MMM", ms), enabled, onDate)
        if (!allDay) {
            Spacer(Modifier.width(8.dp))
            Chip(fmt("HH:mm", ms), enabled, onTime)
        }
    }
}

@Composable
private fun Chip(text: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.clip(shape).background(Palette.Bg).border(1.dp, Palette.Line, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) { Text(text, style = bodyStyle(15, bold = true)) }
}
