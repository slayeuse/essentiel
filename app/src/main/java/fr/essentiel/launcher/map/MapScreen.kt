package fr.essentiel.launcher.map

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.RectF
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import fr.essentiel.launcher.paper
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
import java.text.SimpleDateFormat
import java.util.Date
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
    data class Results(val title: String, val places: List<Place>) : Panel()
    data class PlaceInfo(val place: Place, val back: Results? = null) : Panel()
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
    var firstFix by remember { mutableStateOf(true) }

    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
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
        mapView.onStart(); mapView.onResume()
        onDispose {
            lifecycle.removeObserver(obs)
            mapView.onPause(); mapView.onStop(); mapView.onDestroy()
        }
    }

    // ---------- Actions ----------
    fun showPin(p: Place?) = MapStyle.setGeoJson(style, MapStyle.SRC_PIN, if (p == null) MapApi.EMPTY else MapApi.pointJson(p.lat, p.lon))
    fun showResults(list: List<Place>) = MapStyle.setGeoJson(style, MapStyle.SRC_RESULTS, if (list.isEmpty()) MapApi.EMPTY else MapApi.pointsJson(list))
    fun clearRoute() { route = null; MapStyle.setGeoJson(style, MapStyle.SRC_ROUTE, MapApi.EMPTY) }

    fun fitPoints(points: List<Pair<Double, Double>>, bottom: Int = 520) {
        val m = map ?: return
        if (points.isEmpty()) return
        if (points.size == 1) {
            m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(points[0].first, points[0].second), 15.5), 700); return
        }
        val b = LatLngBounds.Builder()
        points.forEach { (lat, lon) -> b.include(LatLng(lat, lon)) }
        try { m.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), 120, 360, 120, bottom), 800) } catch (e: Exception) { }
    }

    fun openPlace(place: Place, back: Panel.Results? = null) {
        follow = false; searchOpen = false
        clearRoute()
        showPin(place)
        if (back == null) showResults(emptyList())
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(place.lat, place.lon), 16.0), 700)
        panel = Panel.PlaceInfo(place, back)
        MapPlaces.addRecent(ctx, place)
    }

    fun runCategory(c: Category) {
        searchOpen = false; follow = false
        val loc = me ?: map?.cameraPosition?.target?.let { t -> Location("carte").apply { latitude = t.latitude; longitude = t.longitude } }
        searching = true
        scope.launch {
            val list = withContext(Dispatchers.IO) { MapApi.search(c.word, loc?.latitude, loc?.longitude, c.osmTag, 20) }
                .let { l -> if (loc != null) l.sortedBy { MapApi.distance(loc.latitude, loc.longitude, it.lat, it.lon) } else l }
                .filter { p -> loc == null || MapApi.distance(loc.latitude, loc.longitude, p.lat, p.lon) < 30_000 }
            searching = false
            if (list.isEmpty()) { Toast.makeText(ctx, "Rien trouvé près d'ici", Toast.LENGTH_SHORT).show(); return@launch }
            clearRoute(); showPin(null); showResults(list)
            panel = Panel.Results(c.label, list)
            fitPoints(list.map { it.lat to it.lon })
        }
    }

    fun speak(text: String) { tts.value?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "essentiel") }

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
                if (fit) fitPoints(r.points)
            }
        }
    }

    fun stopNavigation() {
        navigating = false
        tts.value?.shutdown(); tts.value = null
        (ctx as? Activity)?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        route?.let { fitPoints(it.points) }
        map?.animateCamera(CameraUpdateFactory.tiltTo(0.0))
    }

    fun startNavigation() {
        val r = route ?: return
        navigating = true; follow = true; showSteps = false
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

    DisposableEffect(Unit) {
        onDispose {
            tts.value?.shutdown()
            (ctx as? Activity)?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // ---------- Chargement de la carte ----------
    LaunchedEffect(Unit) {
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.isCompassEnabled = true
            m.uiSettings.setCompassMargins(0, 330, 30, 0)
            m.uiSettings.isLogoEnabled = false
            m.uiSettings.setAttributionMargins(20, 0, 0, 20)
            val start = me
            m.cameraPosition = if (start != null)
                CameraPosition.Builder().target(LatLng(start.latitude, start.longitude)).zoom(15.0).build()
            else CameraPosition.Builder().target(LatLng(46.6, 2.4)).zoom(5.0).build()
            m.setStyle(Style.Builder().fromUri(MapStyle.STYLE_URL)) { s ->
                MapStyle.apply(s)
                style = s
            }
            // Toucher un point d'intérêt ou un résultat
            m.addOnMapClickListener { p ->
                val screen = m.projection.toScreenLocation(p)
                val r = 28f
                val rect = RectF(screen.x - r, screen.y - r, screen.x + r, screen.y + r)
                val current = panel
                val res = try { m.queryRenderedFeatures(rect, MapStyle.LAYER_RESULTS) } catch (e: Exception) { null }
                val hit = res?.firstOrNull { it.hasProperty("i") }
                if (hit != null) {
                    val list = when (current) {
                        is Panel.Results -> current
                        is Panel.PlaceInfo -> current.back
                        else -> null
                    }
                    val idx = hit.getNumberProperty("i").toInt()
                    list?.places?.getOrNull(idx)?.let { openPlace(it, list) }
                    return@addOnMapClickListener true
                }
                val pois = try {
                    if (MapStyle.poiLayers.isEmpty()) null else m.queryRenderedFeatures(rect, *MapStyle.poiLayers)
                } catch (e: Exception) { null }
                val poi = pois?.firstOrNull { it.hasProperty("name") }
                if (poi != null) {
                    val name = (if (poi.hasProperty("name:fr")) poi.getStringProperty("name:fr") else null)
                        ?: poi.getStringProperty("name") ?: "Lieu"
                    val cls = when {
                        poi.hasProperty("subclass") -> poi.getStringProperty("subclass")
                        poi.hasProperty("class") -> poi.getStringProperty("class")
                        else -> null
                    }
                    val place = Place(name, "", p.latitude, p.longitude, cls?.let { frenchCategory(it) } ?: "")
                    openPlace(place)
                    scope.launch {
                        val addr = withContext(Dispatchers.IO) { MapApi.reverse(p.latitude, p.longitude) }?.address
                        val cur = panel
                        if (!addr.isNullOrBlank() && cur is Panel.PlaceInfo && cur.place == place) {
                            panel = cur.copy(place = place.copy(address = addr))
                        }
                    }
                    return@addOnMapClickListener true
                }
                false
            }
            m.addOnMapLongClickListener { p ->
                scope.launch {
                    val place = withContext(Dispatchers.IO) { MapApi.reverse(p.latitude, p.longitude) }
                        ?: Place("Point sur la carte", "", p.latitude, p.longitude)
                    openPlace(place.copy(lat = p.latitude, lon = p.longitude))
                }
                true
            }
            m.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) follow = false
            }
        }
    }

    // ---------- Position en direct ----------
    DisposableEffect(Unit) {
        val lm = ctx.getSystemService(LocationManager::class.java)
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val prev = me
                if (prev == null || location.provider == LocationManager.GPS_PROVIDER ||
                    location.time - prev.time > 10_000 || location.accuracy <= prev.accuracy
                ) me = location
            }
            @Deprecated("API 29") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        try {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                .mapNotNull { p -> try { lm?.getLastKnownLocation(p) } catch (e: Exception) { null } }
                .maxByOrNull { it.time }?.let { me = it }
            if (ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                lm?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, listener, Looper.getMainLooper())
            }
            lm?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 5f, listener, Looper.getMainLooper())
        } catch (e: Exception) { }
        onDispose { try { lm?.removeUpdates(listener) } catch (e: Exception) { } }
    }

    // Point « moi », caméra qui suit, guidage
    LaunchedEffect(me, style) {
        val loc = me ?: return@LaunchedEffect
        MapStyle.setGeoJson(style, MapStyle.SRC_ME, MapApi.pointJson(loc.latitude, loc.longitude))
        val m = map ?: return@LaunchedEffect
        if (firstFix) {
            firstFix = false
            m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 15.0), 600)
            return@LaunchedEffect
        }
        if (follow) {
            val zoom = if (navigating) 17.0 else maxOf(m.cameraPosition.zoom, 14.0)
            val builder = CameraPosition.Builder().target(LatLng(loc.latitude, loc.longitude)).zoom(zoom)
            if (navigating) {
                builder.tilt(50.0)
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
                    val next = r.steps.getOrNull(stepIndex)
                    if (next == null || stepIndex >= r.steps.lastIndex &&
                        MapApi.distance(loc.latitude, loc.longitude, next.lat, next.lon) < reach * 2
                    ) {
                        speak("Vous êtes arrivé"); stopNavigation()
                    } else speak(next.instruction)
                } else if (d < 200 && announcedNear != stepIndex && d < step.distance + 250) {
                    announcedNear = stepIndex
                    speak("Dans ${MapApi.formatDistance(d)}, ${step.instruction.replaceFirstChar { it.lowercase() }}")
                }
            }
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

    // Recherche pendant la frappe
    LaunchedEffect(query, searchOpen) {
        if (!searchOpen || query.trim().length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(400)
        searching = true
        val loc = me
        results = withContext(Dispatchers.IO) { MapApi.search(query.trim(), loc?.latitude, loc?.longitude) }
        searching = false
    }

    BackHandler(enabled = searchOpen || panel != Panel.None || navigating) {
        when {
            searchOpen -> searchOpen = false
            navigating -> stopNavigation()
            else -> {
                val p = panel
                if (p is Panel.PlaceInfo && p.back != null) { panel = p.back; showPin(null) }
                else { panel = Panel.None; showPin(null); showResults(emptyList()); clearRoute() }
            }
        }
    }

    // ---------- Écran ----------
    Box(Modifier.fillMaxSize().background(Palette.Bg)) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
            val r = route
            if (navigating && r != null) {
                val step = r.steps.getOrNull(stepIndex)
                val dist = step?.let { s -> me?.let { MapApi.distance(it.latitude, it.longitude, s.lat, s.lon) } }
                Column(
                    Modifier.padding(horizontal = 14.dp).fillMaxWidth().shadow(6.dp, RoundedCornerShape(24.dp))
                        .clip(RoundedCornerShape(24.dp)).background(Palette.Accent).padding(18.dp)
                ) {
                    if (dist != null) Text(MapApi.formatDistance(dist), style = serifStyle(36, Color.White))
                    Text(step?.instruction ?: "Suivez l'itinéraire", style = bodyStyle(18, Color.White, bold = true))
                    r.steps.getOrNull(stepIndex + 1)?.let {
                        Spacer(Modifier.height(6.dp))
                        Text("puis : ${it.instruction}", style = bodyStyle(13, Color.White.copy(alpha = 0.85f)), maxLines = 1)
                    }
                }
            } else {
                // Fausse barre de recherche : un appui ouvre l'écran de recherche (clavier prêt)
                Row(
                    Modifier.padding(horizontal = 14.dp).fillMaxWidth().shadow(6.dp, RoundedCornerShape(26.dp))
                        .clip(RoundedCornerShape(26.dp)).background(Palette.Bg)
                        .border(1.dp, Palette.Line, RoundedCornerShape(26.dp))
                        .clickable { searchOpen = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.clip(CircleShape).clickable { nav.back() }.padding(8.dp)) {
                        LineIconView(Icons.back, Palette.Ink, 24.dp)
                    }
                    Text(
                        query.ifBlank { "rechercher sur la carte" },
                        style = bodyStyle(17, if (query.isBlank()) Palette.Muted else Palette.Ink),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(vertical = 12.dp)
                    )
                    Box(Modifier.padding(8.dp)) { LineIconView(Icons.search, Palette.Accent, 22.dp) }
                }
                Spacer(Modifier.height(10.dp))
                // Catégories rapides
                LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(CATEGORIES) { c ->
                        Text(
                            c.label, style = bodyStyle(14, bold = true),
                            modifier = Modifier.shadow(3.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50))
                                .background(Palette.Bg).border(1.dp, Palette.Line, RoundedCornerShape(50))
                                .clickable { runCategory(c) }.padding(horizontal = 14.dp, vertical = 9.dp)
                        )
                    }
                }
            }
        }

        // Boutons à droite
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (searching) MapRoundButton(Icons.clock, Palette.Muted) {}
            MapRoundButton(Icons.plus, Palette.Ink) { map?.animateCamera(CameraUpdateFactory.zoomIn(), 250) }
            MapRoundButton(minusIcon, Palette.Ink) { map?.animateCamera(CameraUpdateFactory.zoomOut(), 250) }
            MapRoundButton(Icons.locate, if (follow) Palette.Accent else Palette.Ink) {
                follow = true
                val loc = me
                if (loc == null) Toast.makeText(ctx, "Recherche de ta position…", Toast.LENGTH_SHORT).show()
                else map?.animateCamera(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder().target(LatLng(loc.latitude, loc.longitude)).zoom(16.0).bearing(0.0).tilt(0.0).build()
                    ), 700
                )
            }
        }

        // ---------- Panneau du bas ----------
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Text(
                "© OpenFreeMap · © contributeurs OpenStreetMap",
                style = bodyStyle(10, Palette.Muted),
                modifier = Modifier.padding(start = 14.dp, bottom = 4.dp).clickable {
                    ctx.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/copyright")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            )
            when (val p = panel) {
                Panel.None -> {}
                is Panel.Results -> BottomCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.title, style = serifStyle(26), modifier = Modifier.weight(1f))
                        Box(Modifier.clip(CircleShape).clickable {
                            panel = Panel.None; showResults(emptyList())
                        }.padding(6.dp)) { LineIconView(Icons.close, Palette.Muted, 22.dp) }
                    }
                    Spacer(Modifier.height(6.dp))
                    LazyColumn(Modifier.heightIn(max = 260.dp)) {
                        items(p.places) { place -> PlaceRow(place, me) { openPlace(place, p) } }
                    }
                }
                is Panel.PlaceInfo -> BottomCard {
                    val place = p.place
                    val favVersion = MapPlaces.version.intValue
                    val fav = remember(favVersion, place) { MapPlaces.isFavorite(ctx, place) }
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(place.name, style = serifStyle(28), maxLines = 2)
                            val sub = listOf(place.category, place.address).filter { it.isNotBlank() }.joinToString(" · ")
                            if (sub.isNotBlank()) Text(sub, style = bodyStyle(15, Palette.Muted), maxLines = 2)
                            me?.let {
                                Text(
                                    MapApi.formatDistance(MapApi.distance(it.latitude, it.longitude, place.lat, place.lon)) + " à vol d'oiseau",
                                    style = bodyStyle(13, Palette.Muted)
                                )
                            }
                        }
                        Box(Modifier.clip(CircleShape).clickable {
                            if (p.back != null) { panel = p.back; showPin(null) }
                            else { panel = Panel.None; showPin(null) }
                        }.padding(6.dp)) { LineIconView(Icons.close, Palette.Muted, 22.dp) }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        PillButton("itinéraire", modifier = Modifier.weight(1f)) {
                            showResults(emptyList()); panel = Panel.Directions(place); computeRoute(place)
                        }
                        MapRoundButton(Icons.star, if (fav) Palette.Accent else Palette.Muted) {
                            MapPlaces.toggleFavorite(ctx, place)
                            Toast.makeText(ctx, if (fav) "Retiré des favoris" else "Ajouté aux favoris", Toast.LENGTH_SHORT).show()
                        }
                        MapRoundButton(Icons.share, Palette.Ink) {
                            val link = String.format(Locale.US, "https://www.openstreetmap.org/?mlat=%.6f&mlon=%.6f#map=18/%.6f/%.6f", place.lat, place.lon, place.lat, place.lon)
                            val text = listOf(place.name, place.address, link).filter { it.isNotBlank() }.joinToString("\n")
                            ctx.startActivity(
                                Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Partager le lieu")
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                }
                is Panel.Directions -> BottomCard {
                    val r = route
                    if (!navigating) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "ma position → ${p.place.name}", style = bodyStyle(15, Palette.Muted),
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                            )
                            Box(Modifier.clip(CircleShape).clickable {
                                panel = Panel.PlaceInfo(p.place); clearRoute(); showPin(p.place)
                            }.padding(6.dp)) { LineIconView(Icons.close, Palette.Muted, 22.dp) }
                        }
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
                            val arrival = SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(System.currentTimeMillis() + (timeLeft * 1000).toLong()))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(MapApi.formatDuration(timeLeft), style = serifStyle(32))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "${MapApi.formatDistance(left)} · arrivée $arrival",
                                    style = bodyStyle(15, Palette.Muted), modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                            if (showSteps && !navigating) {
                                LazyColumn(Modifier.heightIn(max = 240.dp)) {
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
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---------- Écran de recherche ----------
        if (searchOpen) {
            SearchOverlay(
                query = query,
                onQuery = { query = it },
                results = results,
                searching = searching,
                me = me,
                onClose = { searchOpen = false },
                onPlace = { openPlace(it) },
                onCategory = { runCategory(it) },
            )
        }
    }
}

private val minusIcon = LineIcon(listOf("M5 12h14"))

@Composable
private fun SearchOverlay(
    query: String,
    onQuery: (String) -> Unit,
    results: List<Place>,
    searching: Boolean,
    me: Location?,
    onClose: () -> Unit,
    onPlace: (Place) -> Unit,
    onCategory: (Category) -> Unit,
) {
    val ctx = LocalContext.current
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(150)
        try { focus.requestFocus() } catch (e: Exception) { }
        keyboard?.show()
    }
    val placesVersion = MapPlaces.version.intValue
    val favorites = remember(placesVersion) { MapPlaces.favorites(ctx) }
    val recents = remember(placesVersion) { MapPlaces.recents(ctx) }

    Column(Modifier.fillMaxSize().paper()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 14.dp, top = 14.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.clip(CircleShape).clickable { keyboard?.hide(); onClose() }.padding(8.dp)) {
                LineIconView(Icons.back, Palette.Ink, 24.dp)
            }
            val shape = RoundedCornerShape(24.dp)
            Box(
                Modifier.weight(1f).clip(shape).background(Palette.Card).border(1.dp, Palette.Accent, shape)
                    .padding(horizontal = 16.dp, vertical = 13.dp)
            ) {
                if (query.isEmpty()) Text("adresse, lieu, commerce…", style = bodyStyle(17, Palette.Muted))
                BasicTextField(
                    value = query, onValueChange = onQuery, singleLine = true,
                    textStyle = bodyStyle(17), cursorBrush = SolidColor(Palette.Accent),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus)
                )
            }
            if (query.isNotEmpty()) {
                Box(Modifier.clip(CircleShape).clickable { onQuery("") }.padding(8.dp)) {
                    LineIconView(Icons.close, Palette.Muted, 20.dp)
                }
            }
        }

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            if (query.trim().length >= 2) {
                if (searching && results.isEmpty()) item { Text("recherche…", style = bodyStyle(15, Palette.Muted), modifier = Modifier.padding(vertical = 14.dp)) }
                if (!searching && results.isEmpty()) item { Text("aucun résultat", style = bodyStyle(15, Palette.Muted), modifier = Modifier.padding(vertical = 14.dp)) }
                items(results) { p -> PlaceRow(p, me) { keyboard?.hide(); onPlace(p) } }
            } else {
                item { SectionLabel("catégories") }
                items(CATEGORIES.chunked(2)) { pair ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { c ->
                            Text(
                                c.label, style = bodyStyle(15, bold = true),
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(Palette.Card)
                                    .border(1.dp, Palette.Line, RoundedCornerShape(18.dp))
                                    .clickable { keyboard?.hide(); onCategory(c) }.padding(14.dp)
                            )
                        }
                    }
                }
                if (favorites.isNotEmpty()) {
                    item { SectionLabel("favoris") }
                    items(favorites) { p -> PlaceRow(p, me, Icons.star) { keyboard?.hide(); onPlace(p) } }
                }
                if (recents.isNotEmpty()) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { SectionLabel("récents") }
                            Text(
                                "effacer", style = bodyStyle(13, Palette.Muted, bold = true),
                                modifier = Modifier.clickable { MapPlaces.clearRecents(ctx) }.padding(top = 14.dp)
                            )
                        }
                    }
                    items(recents) { p -> PlaceRow(p, me, Icons.clock) { keyboard?.hide(); onPlace(p) } }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), style = bodyStyle(13, Palette.Accent, bold = true), modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
}

@Composable
private fun PlaceRow(place: Place, me: Location?, icon: LineIcon? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { LineIconView(icon, Palette.Accent, 18.dp); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) {
            Text(place.name, style = bodyStyle(16), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOf(place.category, place.address).filter { it.isNotBlank() }.joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, style = bodyStyle(13, Palette.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (me != null) {
            Text(
                MapApi.formatDistance(MapApi.distance(me.latitude, me.longitude, place.lat, place.lon)),
                style = bodyStyle(13, Palette.Muted)
            )
        }
    }
    Divider()
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
        Modifier.size(50.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(Palette.Bg)
            .border(1.dp, Palette.Line, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { LineIconView(icon, color, 22.dp) }
}
