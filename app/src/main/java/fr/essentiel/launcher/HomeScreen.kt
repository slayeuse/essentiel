package fr.essentiel.launcher

import android.Manifest
import fr.essentiel.launcher.reminders.ReminderStore
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val FR = Locale.FRANCE

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    prefs: Prefs,
    resumeTick: Int,
    onOpenApps: () -> Unit,
    onOpenSettings: () -> Unit,
    onBlocked: (String) -> Unit,
    onNeedPicker: (Int) -> Unit,
) {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var agenda by remember { mutableStateOf<CalendarRepo.Agenda?>(null) }
    var weather by remember { mutableStateOf(prefs.cachedWeather()) }
    var permTick by remember { mutableIntStateOf(0) }

    val askCalendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permTick++ }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permTick++ }

    // Heure + agenda : rafraîchis à chaque retour sur l'accueil et chaque minute.
    LaunchedEffect(resumeTick, permTick) {
        while (true) {
            now = System.currentTimeMillis()
            agenda = withContext(Dispatchers.IO) { CalendarRepo.load(ctx) }
            delay(60_000)
        }
    }
    // Météo : au plus une fois toutes les 30 minutes.
    LaunchedEffect(resumeTick, permTick) {
        if (Weather.hasPermission(ctx) && (weather == null || prefs.weatherAgeMs() > 30 * 60_000L)) {
            Weather.load(ctx)?.let { prefs.saveWeather(it); weather = it }
        }
    }

    var dragTotal by remember { mutableStateOf(0f) }

    Column(
        Modifier
            .fillMaxSize()
            .paper()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onDragEnd = { if (dragTotal < -120f) onOpenApps() },
                    onVerticalDrag = { _, d -> dragTotal += d },
                )
            }
            .padding(horizontal = 26.dp)
    ) {
        Spacer(Modifier.height(28.dp))

        // ----- Date (appui long = réglages) -----
        val day = SimpleDateFormat("EEEE", FR).format(Date(now))
        val date = SimpleDateFormat("d MMMM", FR).format(Date(now))
        Text(
            text = "$day,\n$date",
            style = TextStyle(fontFamily = Fonts.serif, fontSize = 46.sp, lineHeight = 52.sp, color = Palette.Ink),
            modifier = Modifier.combinedClickable(
                interactionSource = null, indication = null,
                onClick = {}, onLongClick = onOpenSettings,
            )
        )

        Spacer(Modifier.height(14.dp))

        // ----- Météo -----
        Row(verticalAlignment = Alignment.CenterVertically) {
            val w = weather
            if (!Weather.hasPermission(ctx)) {
                LineIconView(Icons.sun, Palette.Accent, 24.dp)
                Spacer(Modifier.width(14.dp))
                Text(
                    "activer la météo",
                    style = bodyStyle(19, Palette.Muted),
                    modifier = Modifier.clickable { askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
                )
            } else if (w != null) {
                LineIconView(Weather.icon(w.code, w.isDay), Palette.Accent, 24.dp)
                Spacer(Modifier.width(14.dp))
                Text("${Weather.label(w.code, w.isDay)}, ${w.temp}°", style = bodyStyle(19, Palette.Muted))
            } else {
                Text("météo…", style = bodyStyle(19, Palette.Muted))
            }
        }

        Spacer(Modifier.height(36.dp))

        // ----- Bandeau agenda -----
        val ev = agenda?.next
        val remaining = agenda?.remaining ?: 0
        when {
            !CalendarRepo.hasPermission(ctx) -> Banner(Icons.calendar, "autoriser l'agenda", null) {
                askCalendar.launch(Manifest.permission.READ_CALENDAR)
            }
            ev == null -> Banner(Icons.calendar, "rien de prévu aujourd'hui", null) {
                AppLauncher.start(ctx, ToolsActivity.intent(ctx, "calendar"))
            }
            else -> {
                val time = if (ev.allDay) "journée" else SimpleDateFormat("HH:mm", FR).format(Date(ev.begin))
                Banner(Icons.calendar, "${ev.title} · $time", remaining) {
                    AppLauncher.start(
                        ctx, ToolsActivity.intent(ctx, "event").putExtra(ToolsActivity.EXTRA_EVENT, ev.id)
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ----- Bandeau rappels -----
        val reminderVersion = ReminderStore.version.intValue
        val pendingReminders = remember(reminderVersion, resumeTick, now) {
            ReminderStore.all(ctx).filter { !it.done }
        }
        val nextReminder = pendingReminders.firstOrNull()
        Banner(
            Icons.list,
            nextReminder?.let { "${it.text} · ${ReminderStore.label(it.time)}" } ?: "aucun rappel",
            pendingReminders.size.takeIf { it > 0 },
        ) { AppLauncher.start(ctx, ToolsActivity.intent(ctx, "reminders")) }

        Spacer(Modifier.weight(1f))

        // ----- Favoris (4, 8 ou 12 cases) — appui long pour modifier -----
        var editing by remember { mutableStateOf(false) }
        var slotsVersion by remember { mutableIntStateOf(0) }
        val count = remember(slotsVersion, resumeTick) { prefs.tileCount }
        val slots = remember(slotsVersion, resumeTick) { prefs.slots().take(count) }
        val infos = remember(slots) { slots.map { AppLauncher.slotInfo(ctx, it) } }

        if (editing) {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("cases :", style = bodyStyle(14, Palette.Muted))
                listOf(4, 8, 12).forEach { n ->
                    val sel = n == count
                    Text(
                        "$n", style = bodyStyle(14, if (sel) Color.White else Palette.Ink, bold = true),
                        modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(50))
                            .background(if (sel) Palette.Accent else Palette.Card)
                            .border(1.dp, if (sel) Palette.Accent else Palette.Line, RoundedCornerShape(50))
                            .clickable { prefs.tileCount = n; slotsVersion++ }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "terminé", style = bodyStyle(15, Palette.Accent, bold = true),
                    modifier = Modifier.clickable { editing = false }.padding(6.dp)
                )
            }
        }

        slots.indices.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { i ->
                    SlotButton(
                        info = infos[i],
                        editing = editing,
                        canLeft = i > 0,
                        canRight = i < slots.lastIndex,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (editing) onNeedPicker(i)
                            else if (slots[i].isNotBlank()) AppLauncher.launchSlot(ctx, prefs, slots[i], onBlocked)
                        },
                        onLongClick = { editing = true },
                        onLeft = { prefs.moveSlot(i, i - 1); slotsVersion++ },
                        onRight = { prefs.moveSlot(i, i + 1); slotsVersion++ },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // ----- Indice pour la liste d'apps -----
        Text(
            "glisser vers le haut · mes apps",
            style = bodyStyle(14, Palette.Muted.copy(alpha = 0.7f)),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(interactionSource = null, indication = null) { onOpenApps() }
                .padding(vertical = 14.dp)
        )
        Spacer(Modifier.height(6.dp))
    }
}

fun bodyStyle(size: Int, color: Color = Palette.Ink, bold: Boolean = false) = TextStyle(
    fontFamily = if (bold) Fonts.sansBold else Fonts.sans,
    fontSize = size.sp,
    letterSpacing = 0.3.sp,
    color = color,
)

private val BannerShape = RoundedCornerShape(30.dp)
private val TileShape = RoundedCornerShape(22.dp)

@Composable
fun Banner(icon: LineIcon, text: String, badge: Int?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(66.dp)
            .clip(BannerShape)
            .background(Palette.Card)
            .border(1.dp, Palette.Line, BannerShape)
            .clickable(onClick = onClick)
            .padding(start = 24.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineIconView(icon, Palette.Accent, 24.dp)
        Spacer(Modifier.width(22.dp))
        Text(
            text, style = bodyStyle(18), maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (badge != null && badge > 0) {
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Palette.Accent)
                    .padding(horizontal = 14.dp, vertical = 3.dp)
            ) {
                Text("$badge", style = bodyStyle(16, Color.White, bold = true))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SlotButton(
    info: AppLauncher.SlotInfo?,
    editing: Boolean,
    canLeft: Boolean,
    canRight: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
) {
    val empty = info == null
    Box(
        modifier
            .aspectRatio(1.05f)
            .clip(TileShape)
            .background(if (empty && !editing) Color.Transparent else Palette.Card)
            .border(
                1.dp,
                when {
                    editing -> Palette.Accent.copy(alpha = 0.6f)
                    empty -> Color.Transparent
                    else -> Palette.Line
                },
                TileShape
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when {
                info == null -> if (editing) Text("+", style = serifStyleHome(30, Palette.Accent))
                info.icon != null -> LineIconView(info.icon, Palette.Accent, 30.dp)
                info.pkg != null -> AppIconView(info.pkg, 32.dp)
                else -> Text(info.letter, style = serifStyleHome(30, Palette.Accent))
            }
            if (info != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    info.label, style = bodyStyle(13, bold = true), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
        if (editing) {
            if (canLeft) Text(
                "‹", style = bodyStyle(20, Palette.Accent, bold = true),
                modifier = Modifier.align(Alignment.TopStart).clickable(onClick = onLeft).padding(horizontal = 8.dp)
            )
            if (canRight) Text(
                "›", style = bodyStyle(20, Palette.Accent, bold = true),
                modifier = Modifier.align(Alignment.TopEnd).clickable(onClick = onRight).padding(horizontal = 8.dp)
            )
        }
    }
}

private fun serifStyleHome(size: Int, color: Color) =
    TextStyle(fontFamily = Fonts.serif, fontSize = size.sp, color = color)
