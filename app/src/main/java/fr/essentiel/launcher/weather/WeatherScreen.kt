package fr.essentiel.launcher.weather

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.essentiel.launcher.CardGroup
import fr.essentiel.launcher.Divider
import fr.essentiel.launcher.Field
import fr.essentiel.launcher.IconAction
import fr.essentiel.launcher.Icons
import fr.essentiel.launcher.LineIcon
import fr.essentiel.launcher.LineIconView
import fr.essentiel.launcher.Nav
import fr.essentiel.launcher.Palette
import fr.essentiel.launcher.PillButton
import fr.essentiel.launcher.Weather
import fr.essentiel.launcher.bodyStyle
import fr.essentiel.launcher.map.MapApi
import fr.essentiel.launcher.map.Place
import fr.essentiel.launcher.paper
import fr.essentiel.launcher.serifStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val FR = Locale.FRANCE
private val Rain = Color(0xFF4F7FA6)

private fun t(v: Double) = "${v.roundToInt()}°"

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WeatherScreen(@Suppress("UNUSED_PARAMETER") nav: Nav) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var here by remember { mutableStateOf(WeatherData.lastHere(ctx)) }
    var saved by remember { mutableStateOf(WeatherData.cities(ctx)) }
    val forecasts = remember { mutableStateMapOf<String, Forecast>() }
    var hasLocation by remember {
        mutableStateOf(ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    var managing by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableIntStateOf(0) }

    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasLocation = ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    val pages: List<City?> = listOf(here) + saved  // null = position pas encore connue
    val pager = rememberPagerState { pages.size }

    // Position actuelle
    LaunchedEffect(hasLocation, refreshTick) {
        if (hasLocation) WeatherData.hereCity(ctx)?.let { here = it }
    }
    // Chargement : cache tout de suite, puis réseau (au plus toutes les 15 min)
    LaunchedEffect(pages, refreshTick) {
        pages.filterNotNull().forEach { c ->
            if (forecasts[c.key] == null) WeatherData.cached(ctx, c)?.let { forecasts[c.key] = it }
            val f = forecasts[c.key]
            if (f == null || System.currentTimeMillis() - f.fetchedAt > 15 * 60_000L || refreshTick > 0) {
                withContext(Dispatchers.IO) { WeatherData.fetch(ctx, c) }?.let { forecasts[c.key] = it }
            }
        }
    }

    Box(Modifier.fillMaxSize().paper()) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            val city = pages.getOrNull(page)
            val f = city?.let { forecasts[it.key] }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(22.dp))
                // ----- En-tête -----
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                city?.name ?: "ma position", style = serifStyle(34), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (page == 0) {
                                Spacer(Modifier.width(8.dp))
                                LineIconView(Icons.locate, Palette.Accent, 18.dp)
                            }
                        }
                        Text(
                            if (f != null) "mis à jour à " + SimpleDateFormat("HH:mm", FR).format(Date(f.fetchedAt)) else "chargement…",
                            style = bodyStyle(13, Palette.Muted)
                        )
                    }
                    IconAction(Icons.rotate, Palette.Muted) { refreshTick++ }
                    IconAction(Icons.list, Palette.Ink) { managing = true }
                }
                // Points de pagination
                if (pages.size > 1) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                        pages.indices.forEach { i ->
                            Box(
                                Modifier.padding(horizontal = 3.dp).size(if (i == pager.currentPage) 8.dp else 6.dp).clip(CircleShape)
                                    .background(if (i == pager.currentPage) Palette.Accent else Palette.Line)
                            )
                        }
                    }
                }

                when {
                    page == 0 && !hasLocation -> Column(Modifier.padding(vertical = 40.dp)) {
                        Text("Autorise la position pour voir la météo là où tu es.", style = bodyStyle(16, Palette.Muted))
                        Spacer(Modifier.height(14.dp))
                        PillButton("autoriser la position") {
                            askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        }
                    }
                    f == null -> Text("Récupération de la météo…", style = bodyStyle(16, Palette.Muted), modifier = Modifier.padding(vertical = 40.dp))
                    else -> ForecastContent(f)
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }

    if (managing) {
        CitiesOverlay(
            here = here,
            saved = saved,
            forecasts = forecasts,
            onClose = { managing = false },
            onOpen = { i -> managing = false; scope.launch { pager.scrollToPage(i) } },
            onSave = { list -> saved = list; WeatherData.saveCities(ctx, list) },
        )
    }
}

@Composable
private fun ForecastContent(f: Forecast) {
    val today = f.days.firstOrNull()

    // ----- Le grand bloc du moment -----
    Spacer(Modifier.height(18.dp))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp))
            .background(
                Brush.verticalGradient(
                    if (f.isDay) listOf(Palette.Accent.copy(alpha = 0.16f), Palette.Accent.copy(alpha = 0.04f))
                    else listOf(Color(0xFF2B2A33).copy(alpha = 0.18f), Color(0xFF2B2A33).copy(alpha = 0.05f))
                )
            )
            .border(1.dp, Palette.Line, RoundedCornerShape(32.dp))
            .padding(vertical = 26.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LineIconView(Weather.icon(f.code, f.isDay), Palette.Accent, 64.dp, strokeWidth = 1.6f)
        Text(t(f.temp), style = serifStyle(104))
        Text(Weather.label(f.code, f.isDay).replaceFirstChar { it.uppercase() }, style = bodyStyle(20, bold = true))
        if (today != null) {
            Spacer(Modifier.height(4.dp))
            Text("max ${t(today.max)}  ·  min ${t(today.min)}  ·  ressenti ${t(f.feels)}", style = bodyStyle(14, Palette.Muted))
        }
    }

    // Résumé pluie
    val rainSoon = f.hours.take(12).firstOrNull { it.rainProb >= 50 }
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp)) {
        LineIconView(Icons.drop, if (rainSoon != null) Rain else Palette.Muted, 18.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            if (rainSoon != null) "Pluie probable vers ${rainSoon.time.hour}h (${rainSoon.rainProb} %)"
            else "Pas de pluie prévue dans les 12 prochaines heures",
            style = bodyStyle(15)
        )
    }

    // ----- Heure par heure -----
    Section("prochaines heures")
    CardGroup {
        LazyRow(contentPadding = PaddingValues(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(f.hours.take(25)) { h ->
                Column(Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (h == f.hours.first()) "maint." else "${h.time.hour}h",
                        style = bodyStyle(13, if (h == f.hours.first()) Palette.Accent else Palette.Muted, bold = true)
                    )
                    Spacer(Modifier.height(8.dp))
                    LineIconView(Weather.icon(h.code, h.isDay), Palette.Accent, 24.dp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (h.rainProb >= 20) "${h.rainProb}%" else " ", style = bodyStyle(11, Rain, bold = true)
                    )
                    Text(t(h.temp), style = bodyStyle(16, bold = true))
                }
            }
        }
    }

    // ----- 10 jours -----
    Section("10 prochains jours")
    val weekMin = f.days.minOfOrNull { it.min } ?: 0.0
    val weekMax = f.days.maxOfOrNull { it.max } ?: 1.0
    val span = (weekMax - weekMin).coerceAtLeast(1.0)
    CardGroup {
        f.days.forEachIndexed { i, d ->
            Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (i) {
                        0 -> "auj."
                        else -> d.date.format(DateTimeFormatter.ofPattern("EEE", FR))
                    },
                    style = bodyStyle(16, bold = i == 0), modifier = Modifier.width(52.dp)
                )
                Box(Modifier.width(34.dp), contentAlignment = Alignment.Center) {
                    LineIconView(Weather.icon(d.code, true), Palette.Accent, 22.dp)
                }
                Text(
                    if (d.rainProb >= 20) "${d.rainProb}%" else "", style = bodyStyle(12, Rain, bold = true),
                    modifier = Modifier.width(40.dp), textAlign = TextAlign.Center
                )
                Text(t(d.min), style = bodyStyle(15, Palette.Muted), modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
                // Barre min → max sur l'échelle de la semaine
                Box(Modifier.weight(1f).padding(horizontal = 10.dp).height(6.dp).clip(RoundedCornerShape(50)).background(Palette.Line.copy(alpha = 0.6f))) {
                    val start = ((d.min - weekMin) / span).toFloat()
                    val end = ((d.max - weekMin) / span).toFloat()
                    Row(Modifier.fillMaxSize()) {
                        if (start > 0f) Spacer(Modifier.weight(start))
                        Box(
                            Modifier.weight((end - start).coerceAtLeast(0.04f)).fillMaxSize().clip(RoundedCornerShape(50))
                                .background(Brush.horizontalGradient(listOf(Color(0xFFF2A37F), Palette.Accent)))
                        )
                        if (end < 1f) Spacer(Modifier.weight(1f - end))
                    }
                }
                Text(t(d.max), style = bodyStyle(15, bold = true), modifier = Modifier.width(36.dp))
            }
            if (i < f.days.lastIndex) Divider()
        }
    }

    // ----- Détails -----
    Section("en détail")
    val tiles = mutableListOf<@Composable () -> Unit>()
    tiles += { Tile(Icons.thermometer, "ressenti", t(f.feels), if (f.feels < f.temp - 1) "le vent rafraîchit" else if (f.feels > f.temp + 1) "l'humidité réchauffe" else "proche du réel") }
    tiles += { Tile(Icons.drop, "humidité", "${f.humidity} %", "point de rosée ${t(f.dewPoint)}") }
    tiles += {
        Tile(Icons.wind, "vent", "${f.wind.roundToInt()} km/h", "${WeatherData.windDirection(f.windDir)} · rafales ${f.gusts.roundToInt()}") {
            LineIconView(Icons.send, Palette.Accent, 18.dp, modifier = Modifier.rotate(f.windDir + 135f))
        }
    }
    tiles += { Tile(Icons.sun, "indice UV", "${f.uv.roundToInt()}", WeatherData.uvLabel(f.uv) + (today?.let { " · max ${it.uvMax.roundToInt()}" } ?: "")) }
    tiles += {
        Tile(
            Icons.sunrise, "soleil",
            today?.sunrise?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "–",
            "coucher " + (today?.sunset?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "–")
        )
    }
    tiles += { Tile(Icons.gauge, "pression", "${f.pressure.roundToInt()} hPa", if (f.pressure > 1020) "haute pression" else if (f.pressure < 1005) "basse pression" else "normale") }
    tiles += {
        Tile(
            Icons.eye, "visibilité",
            if (f.visibility >= 1000) "${(f.visibility / 1000).roundToInt()} km" else "${f.visibility.roundToInt()} m",
            if (f.visibility >= 10_000) "très bonne" else if (f.visibility >= 3_000) "bonne" else "réduite"
        )
    }
    tiles += { Tile(Icons.drop, "précipitations", String.format(FR, "%.1f mm", today?.rainSum ?: 0.0), "prévues aujourd'hui") }
    f.aqi?.let { aqi ->
        tiles += { Tile(Icons.leaf, "qualité de l'air", "$aqi", WeatherData.aqiLabel(aqi) + (f.pm25?.let { " · PM2,5 ${it.roundToInt()}" } ?: "")) }
    }
    tiles += { Tile(Icons.cloud, "nuages", "${f.cloud} %", if (f.cloud < 25) "ciel dégagé" else if (f.cloud < 70) "partiellement couvert" else "couvert") }

    tiles.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { tile -> Box(Modifier.weight(1f)) { tile() } }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    Text(
        "Données : Open-Meteo · qualité de l'air : Copernicus (via Open-Meteo)",
        style = bodyStyle(11, Palette.Muted), modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun Section(title: String) {
    Text(title.uppercase(), style = bodyStyle(13, Palette.Muted, bold = true), modifier = Modifier.padding(top = 22.dp, bottom = 8.dp))
}

@Composable
private fun Tile(icon: LineIcon, label: String, value: String, sub: String, extra: (@Composable () -> Unit)? = null) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier.fillMaxWidth().height(132.dp).clip(shape).background(Color(0xFFF2EFEA))
            .border(1.dp, Palette.Line, shape).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LineIconView(icon, Palette.Accent, 16.dp)
            Spacer(Modifier.width(6.dp))
            Text(label.uppercase(), style = bodyStyle(11, Palette.Muted, bold = true), maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, style = serifStyle(30), maxLines = 1)
            if (extra != null) { Spacer(Modifier.width(8.dp)); extra() }
        }
        Text(sub, style = bodyStyle(12, Palette.Muted), maxLines = 2)
    }
}

@Composable
private fun CitiesOverlay(
    here: City?,
    saved: List<City>,
    forecasts: Map<String, Forecast>,
    onClose: () -> Unit,
    onOpen: (Int) -> Unit,
    onSave: (List<City>) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    LaunchedEffect(query) {
        if (query.trim().length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(400)
        results = withContext(Dispatchers.IO) { MapApi.search(query.trim(), null, null, null, 8) }
    }
    androidx.activity.compose.BackHandler { onClose() }

    Column(Modifier.fillMaxSize().paper().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("villes", style = serifStyle(38), modifier = Modifier.weight(1f))
            Text("terminé", style = bodyStyle(16, Palette.Accent, bold = true), modifier = Modifier.clickable { onClose() }.padding(8.dp))
        }
        Spacer(Modifier.height(12.dp))
        Field(query, { query = it }, "ajouter une ville…", Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            if (results.isNotEmpty()) {
                items(results) { p ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            onSave(saved + City(p.name, p.lat, p.lon)); query = ""
                        }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LineIconView(Icons.plus, Palette.Accent, 18.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(p.name, style = bodyStyle(16))
                            if (p.address.isNotBlank()) Text(p.address, style = bodyStyle(13, Palette.Muted), maxLines = 1)
                        }
                    }
                    Divider()
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
            val all = listOf(here) + saved
            items(all.size) { i ->
                val c = all[i]
                val f = c?.let { forecasts[it.key] }
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(24.dp))
                        .background(if (i == 0) Palette.Accent.copy(alpha = 0.10f) else Color(0xFFF2EFEA))
                        .border(1.dp, Palette.Line, RoundedCornerShape(24.dp))
                        .clickable { onOpen(i) }.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(c?.name ?: "ma position", style = bodyStyle(18, bold = true))
                        Text(
                            if (i == 0) "ma position" + (f?.let { " · " + Weather.label(it.code, it.isDay) } ?: "")
                            else f?.let { Weather.label(it.code, it.isDay) } ?: "",
                            style = bodyStyle(13, Palette.Muted)
                        )
                    }
                    if (f != null) {
                        LineIconView(Weather.icon(f.code, f.isDay), Palette.Accent, 26.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(t(f.temp), style = serifStyle(34))
                    }
                    if (i > 0 && c != null) {
                        Spacer(Modifier.width(6.dp))
                        IconAction(Icons.trash, Palette.Muted, 18.dp) { onSave(saved.filter { it.key != c.key }) }
                    }
                }
            }
        }
    }
}

@Suppress("unused")
private fun keep(scope: ColumnScope) = scope
@Suppress("unused")
private val keepDate: LocalDate? = null
