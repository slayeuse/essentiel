package fr.essentiel.launcher.map

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.WindowManager
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import fr.essentiel.launcher.Divider
import fr.essentiel.launcher.Icons
import fr.essentiel.launcher.LineIcon
import fr.essentiel.launcher.LineIconView
import fr.essentiel.launcher.Nav
import fr.essentiel.launcher.Palette
import fr.essentiel.launcher.PermissionGate
import fr.essentiel.launcher.PillButton
import fr.essentiel.launcher.ToolScaffold
import fr.essentiel.launcher.bodyStyle
import fr.essentiel.launcher.serifStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.util.Locale

private val MAP_PERMS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

@Composable
fun MapScreen(nav: Nav) {
    val ctx = LocalContext.current
    val granted = MAP_PERMS.any { ctx.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    if (!granted) {
        ToolScaffold("carte", onBack = nav::back) {
            PermissionGate(MAP_PERMS, "Pour te situer sur la carte et calculer les itinéraires, Essentiel a besoin de ta position.") {
                LaunchedEffect(Unit) { nav.replace(fr.essentiel.launcher.Route.Map) }
            }
        }
        return
    }
    MapContent(nav)
}

private sealed class Panel {
    data object None : Panel()
    data class PlaceInfo(val place: Place) : Panel()
    data class Directions(val place: Place) : Panel()
}

@SuppressLint("MissingPermission")
@Composable
private fun MapContent(nav: Nav) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    @Suppress("DEPRECATION")
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var me by remember { mutableStateOf<Location?>(null) }
    var follow by remember { mutableStateOf(true) }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var panel by remember { mutableStateOf<Panel>(Panel.None) }
    var mode by remember { mutableStateOf(Mode.CAR) }
    var route by remember { mutableStateOf<RouteResult?>(null) }
    var loadingRoute by remember { mutableStateOf(false) }
    var showSteps by remember { mutableStateOf(false) }

    // Guidage
    var navigating by remember { mutableStateOf(false) }
    var stepIndex by remember { mutableIntStateOf(1) }
    var announcedNear by remember { mutableIntStateOf(-1) }
    var offRouteCount by remember { mutableIntStateOf(0) }
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }

    val mapView = remember {
        MapLibre.getInstance(ctx)
        MapView(ctx).apply { onCreate(Bundle()) }
    }

    // Cycle de vie de la carte
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            mapView.onPause(); mapView.onStop(); mapView.onDestroy()
        }
    }

    // Chargement de la carte et du style
    LaunchedEffect(Unit) {
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.isTiltGesturesEnabled = true
            m.uiSettings.isCompassEnabled = true
            m.uiSettings.setCompassMargins(0, 260, 30, 0)
            m.uiSettings.isLogoEnabled = false
            m.uiSettings.setAttributionMargins(20, 0, 0, 20)
            m.cameraPosition = CameraPosition.Builder().target(LatLng(46.6, 2.4)).zoom(5.0).build()
            m.setStyle(Style.Builder().fromUri(MapStyle.STYLE_URL)) { s ->
                MapStyle.apply(s)
                style = s
            }
            m.addOnMapLongClickListener { p ->
                follow = false
                scope.launch {
                    val place = withContext(Dispatchers.IO) { MapApi.reverse(p.latitude, p.longitude) }
                        ?: Place("Point sur la carte", "", p.latitude, p.longitude)
                    route = null; panel = Panel.PlaceInfo(place)
                }
                true
            }
            m.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) follow = false
            }
        }
    }

    // Position en direct
    DisposableEffect(Unit) {
        val lm = ctx.getSystemService(LocationManager::class.java)
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val prev = me
                // garde la position la plus précise si elles arrivent ensemble
                if (prev == null || location.provider == LocationManager.GPS_PROVIDER || location.time - prev.time > 10_000 ||
                    location.accuracy <= prev.accuracy
                ) me = location
            }
            @Deprecated("API 29") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        try {
            listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                .mapNotNull { p -> try { lm?.getLastKnownLocation(p) } catch (e: Exception) { null } }
                .maxByOrNull { it.time }?.let { me = it }
            if (ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                lm?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, listener, Looper.getMainLooper())
            }
            lm?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 5f, listener, Looper.getMainLooper())
        } catch (e: Exception) { }
        onDispose { try { lm?.removeUpdates(listener) } catch (e: Exception) { } }
    }

    fun speak(text: String) {
        tts.value?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "essentiel")
    }

    fun fitRoute(r: RouteResult) {
        val m = map ?: return
        if (r.points.size < 2) return
        val b = LatLngBounds.Builder()
        r.points.forEach { (lat, lon) -> b.include(LatLng(lat, lon)) }
        try { m.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), 120, 420, 120, 520), 800) } catch (e: Exception) { }
    }

    fun computeRoute(place: Place, fit: Boolean = true) {
        val from = me
        if (from == null) { Toast.makeText(ctx, "Position inconnue pour l'instant", Toast.LENGTH_SHORT).show(); return }
        loadingRoute = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { MapApi.route(mode, from.latitude, from.longitude, place.lat, place.lon) }
            loadingRoute = false
            route = r
            if (r == null) {
                Toast.makeText(ctx, "Itinéraire introuvable (connexion ?)", Toast.LENGTH_SHORT).show()
                MapStyle.setGeoJson(style, MapStyle.SRC_ROUTE, MapApi.EMPTY)
            } else {
                MapStyle.setGeoJson(style, MapStyle.SRC_ROUTE, MapApi.lineJson(r.points))
                if (fit) fitRoute(r)
            }
        }
    }

    fun stopNavigation() {
        navigating = false
        tts.value?.shutdown(); tts.value = null
        (ctx as? Activity)?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        route?.let { fitRoute(it) }
    }

    fun startNavigation() {
        val r = route ?: return
        navigating = true; follow = true
        stepIndex = if (r.steps.size > 1) 1 else 0
        announcedNear = -1; offRouteCount = 0
        (ctx as? Activity)?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        tts.value = TextToSpeech(ctx) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.value?.setLanguage(Locale.FRANCE)
                r.steps.getOrNull(stepIndex)?.let { speak(it.instruction) }
            }
        }
    }

    DisposableEffect(Unit) { onDispose { tts.value?.shutdown(); (ctx as? Activity)?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } }

    // Mise à jour du point « moi », de la caméra et du guidage à chaque position
    LaunchedEffect(me, style) {
        val loc = me ?: return@LaunchedEffect
        MapStyle.setGeoJson(style, MapStyle.SRC_ME, MapApi.pointJson(loc.latitude, loc.longitude))
        val m = map ?: return@LaunchedEffect
        if (follow) {
            val zoom = if (navigating) 17.0 else maxOf(m.cameraPosition.zoom, 14.0)
            val builder = CameraPosition.Builder().target(LatLng(loc.latitude, loc.longitude)).zoom(zoom)
            if (navigating) {
                builder.tilt(45.0)
                if (loc.hasBearing() && loc.speed > 1f) builder.bearing(loc.bearing.toDouble())
            }
            m.animateCamera(CameraUpdateFactory.newCameraPosition(builder.build()), 700)
        }
        val r = route
        val p = panel
        if (navigating && r != null && p is Panel.Directions) {
            val step = r.steps.getOrNull(stepIndex)
            if (step != null) {
                val d = MapApi.distance(loc.latitude, loc.longitude, step.lat, step.lon)
                val reach = if (mode == Mode.CAR) 30.0 else 15.0
                if (d < reach) {
                    stepIndex++
                    r.steps.getOrNull(stepIndex)?.let { speak(it.instruction) }
                    if (stepIndex >= r.steps.size - 1 && r.steps.lastOrNull()?.let {
                            MapApi.distance(loc.latitude, loc.longitude, it.lat, it.lon) < reach * 2
                        } == true
                    ) {
                        speak("Vous êtes arrivé"); stopNavigation()
                    }
                } else if (d < 200 && announcedNear != stepIndex && step.distance > 0) {
                    announcedNear = stepIndex
                    speak("Dans ${MapApi.formatDistance(d)}, ${step.instruction.replaceFirstChar { it.lowercase() }}")
                }
            }
            // Hors de l'itinéraire : recalcul
            val nearest = r.points.minOfOrNull { (lat, lon) -> MapApi.distance(loc.latitude, loc.longitude, lat, lon) } ?: 0.0
            if (nearest > 70) {
                offRouteCount++
                if (offRouteCount >= 3) {
                    offRouteCount = 0
                    speak("Recalcul de l'itinéraire")
                    computeRoute(p.place, fit = false)
                    stepIndex = 1
                }
            } else offRouteCount = 0
        }
    }

    // Recherche avec un petit délai pendant la frappe
    LaunchedEffect(query) {
        if (query.trim().length < 3) { results = emptyList(); return@LaunchedEffect }
        delay(450)
        val loc = me
        results = withContext(Dispatchers.IO) { MapApi.search(query.trim(), loc?.latitude, loc?.longitude) }
    }

    fun selectPlace(place: Place) {
        results = emptyList(); query = place.name; follow = false
        route = null
        MapStyle.setGeoJson(style, MapStyle.SRC_ROUTE, MapApi.EMPTY)
        MapStyle.setGeoJson(style, MapStyle.SRC_PIN, MapApi.pointJson(place.lat, place.lon))
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(place.lat, place.lon), 15.5), 800)
        panel = Panel.PlaceInfo(place)
    }

    LaunchedEffect(panel) {
        val p = panel
        if (p is Panel.PlaceInfo) MapStyle.setGeoJson(style, MapStyle.SRC_PIN, MapApi.pointJson(p.place.lat, p.place.lon))
        if (p is Panel.None) {
            MapStyle.setGeoJson(style, MapStyle.SRC_PIN, MapApi.EMPTY)
            MapStyle.setGeoJson(style, MapStyle.SRC_ROUTE, MapApi.EMPTY)
        }
    }

    Box(Modifier.fillMaxSize().background(Palette.Bg)) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        // ----- Haut : recherche ou consigne de guidage -----
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            val r = route
            if (navigating && r != null) {
                val step = r.steps.getOrNull(stepIndex)
                val dist = step?.let { s -> me?.let { MapApi.distance(it.latitude, it.longitude, s.lat, s.lon) } }
                Column(
                    Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(24.dp))
                        .clip(RoundedCornerShape(24.dp)).background(Palette.Accent).padding(18.dp)
                ) {
                    if (dist != null) Text(MapApi.formatDistance(dist), style = serifStyle(36, Color.White))
                    Text(step?.instruction ?: "Suivez l'itinéraire", style = bodyStyle(18, Color.White, bold = true))
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(26.dp))
                        .clip(RoundedCornerShape(26.dp)).background(Palette.Bg)
                        .border(1.dp, Palette.Line, RoundedCornerShape(26.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.clip(CircleShape).clickable { nav.back() }.padding(8.dp)) {
                        LineIconView(Icons.back, Palette.Ink, 24.dp)
                    }
                    Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
                        if (query.isEmpty()) Text("rechercher un lieu, une adresse", style = bodyStyle(17, Palette.Muted))
                        BasicTextField(
                            value = query, onValueChange = { query = it }, singleLine = true,
                            textStyle = bodyStyle(17), cursorBrush = SolidColor(Palette.Accent),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (query.isNotEmpty()) {
                        Box(Modifier.clip(CircleShape).clickable {
                            query = ""; results = emptyList(); panel = Panel.None; route = null
                        }.padding(8.dp)) { LineIconView(Icons.close, Palette.Muted, 20.dp) }
                    } else {
                        Box(Modifier.padding(8.dp)) { LineIconView(Icons.search, Palette.Accent, 22.dp) }
                    }
                }
                if (results.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        Modifier.fillMaxWidth().heightIn(max = 360.dp)
                            .shadow(6.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp))
                            .background(Palette.Bg).padding(horizontal = 16.dp)
                    ) {
                        items(results) { place ->
                            Column(Modifier.fillMaxWidth().clickable { selectPlace(place) }.padding(vertical = 12.dp)) {
                                Text(place.name, style = bodyStyle(16), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (place.address.isNotBlank()) {
                                    Text(place.address, style = bodyStyle(13, Palette.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Divider()
                        }
                    }
                }
            }
        }

        // ----- Bouton « me recentrer » -----
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MapRoundButton(Icons.locate, if (follow) Palette.Accent else Palette.Ink) {
                follow = true
                val loc = me
                if (loc == null) Toast.makeText(ctx, "Recherche de ta position…", Toast.LENGTH_SHORT).show()
                else map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 16.0), 700)
            }
        }

        // ----- Bas : fiche du lieu / itinéraire -----
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Text(
                "© OpenFreeMap · © contributeurs OpenStreetMap",
                style = bodyStyle(10, Palette.Muted),
                modifier = Modifier.padding(start = 14.dp, bottom = 4.dp)
                    .clickable { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/copyright")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            )
            when (val p = panel) {
                Panel.None -> {}
                is Panel.PlaceInfo -> BottomCard {
                    Text(p.place.name, style = serifStyle(28), maxLines = 2)
                    if (p.place.address.isNotBlank()) Text(p.place.address, style = bodyStyle(15, Palette.Muted))
                    me?.let {
                        Text(
                            "à " + MapApi.formatDistance(MapApi.distance(it.latitude, it.longitude, p.place.lat, p.place.lon)) + " à vol d'oiseau",
                            style = bodyStyle(14, Palette.Muted)
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton("itinéraire", modifier = Modifier.weight(1f)) {
                            panel = Panel.Directions(p.place); computeRoute(p.place)
                        }
                        PillButton("fermer", filled = false) { panel = Panel.None; query = "" }
                    }
                }
                is Panel.Directions -> BottomCard {
                    val r = route
                    if (!navigating) {
                        Text("vers ${p.place.name}", style = bodyStyle(15, Palette.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Mode.entries.forEach { m ->
                                val sel = m == mode
                                Text(
                                    m.label, style = bodyStyle(14, if (sel) Color.White else Palette.Ink, bold = true),
                                    modifier = Modifier.clip(RoundedCornerShape(50))
                                        .background(if (sel) Palette.Accent else Palette.Card)
                                        .border(1.dp, if (sel) Palette.Accent else Palette.Line, RoundedCornerShape(50))
                                        .clickable { if (!sel) { mode = m; computeRoute(p.place) } }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    when {
                        loadingRoute -> Text("calcul de l'itinéraire…", style = bodyStyle(16, Palette.Muted))
                        r == null -> Text("pas d'itinéraire", style = bodyStyle(16, Palette.Muted))
                        else -> {
                            val left = if (navigating) r.steps.drop(stepIndex).sumOf { it.distance } else r.distance
                            val timeLeft = if (navigating && r.distance > 0) r.duration * (left / r.distance) else r.duration
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(MapApi.formatDuration(timeLeft), style = serifStyle(32))
                                Spacer(Modifier.width(10.dp))
                                Text(MapApi.formatDistance(left), style = bodyStyle(16, Palette.Muted), modifier = Modifier.padding(bottom = 6.dp))
                            }
                            if (showSteps && !navigating) {
                                LazyColumn(Modifier.heightIn(max = 220.dp)) {
                                    items(r.steps) { s ->
                                        Row(Modifier.padding(vertical = 8.dp)) {
                                            Text(s.instruction, style = bodyStyle(15), modifier = Modifier.weight(1f))
                                            if (s.distance > 0) Text(MapApi.formatDistance(s.distance), style = bodyStyle(13, Palette.Muted))
                                        }
                                        Divider()
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (navigating) {
                                    PillButton("arrêter", modifier = Modifier.weight(1f)) { stopNavigation() }
                                } else {
                                    PillButton("démarrer", modifier = Modifier.weight(1f)) { startNavigation() }
                                    PillButton(if (showSteps) "carte" else "étapes", filled = false) { showSteps = !showSteps }
                                    PillButton("fermer", filled = false) {
                                        panel = Panel.PlaceInfo(p.place); route = null
                                        MapStyle.setGeoJson(style, MapStyle.SRC_ROUTE, MapApi.EMPTY)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomCard(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(Palette.Bg)
            .padding(horizontal = 22.dp, vertical = 20.dp)
    ) { content() }
}

@Composable
private fun MapRoundButton(icon: LineIcon, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(Palette.Bg)
            .border(1.dp, Palette.Line, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { LineIconView(icon, color, 24.dp) }
}

@Suppress("unused")
private fun Context.hasFine() = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
