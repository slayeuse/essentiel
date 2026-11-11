package fr.essentiel.launcher.map

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class Place(
    val name: String,
    val address: String,
    val lat: Double,
    val lon: Double,
    val category: String = "",
)

/** Catégories rapides de la recherche. */
data class Category(val label: String, val word: String, val osmTag: String, val icon: String)

val CATEGORIES = listOf(
    Category("Restaurants", "restaurant", "amenity:restaurant", "🍽"),
    Category("Cafés", "café", "amenity:cafe", "☕"),
    Category("Boulangeries", "boulangerie", "shop:bakery", "🥖"),
    Category("Supermarchés", "supermarché", "shop:supermarket", "🛒"),
    Category("Pharmacies", "pharmacie", "amenity:pharmacy", "✚"),
    Category("Stations-service", "station service", "amenity:fuel", "⛽"),
    Category("Parkings", "parking", "amenity:parking", "P"),
    Category("Distributeurs", "distributeur", "amenity:atm", "€"),
)

/** Nom français d'une catégorie OpenStreetMap (classe / sous-classe d'un point d'intérêt). */
fun frenchCategory(value: String): String = when (value) {
    "restaurant" -> "restaurant"
    "fast_food" -> "restauration rapide"
    "cafe" -> "café"
    "bar", "pub", "beer" -> "bar"
    "bakery" -> "boulangerie"
    "supermarket", "grocery", "convenience" -> "alimentation"
    "pharmacy" -> "pharmacie"
    "hospital" -> "hôpital"
    "doctors", "doctor" -> "médecin"
    "fuel" -> "station-service"
    "parking" -> "parking"
    "bank" -> "banque"
    "atm" -> "distributeur"
    "post", "post_office" -> "la poste"
    "school" -> "école"
    "college", "university" -> "université"
    "library" -> "bibliothèque"
    "town_hall" -> "mairie"
    "police" -> "police"
    "museum" -> "musée"
    "cinema" -> "cinéma"
    "theatre" -> "théâtre"
    "park" -> "parc"
    "place_of_worship" -> "lieu de culte"
    "bus" -> "arrêt de bus"
    "railway", "station" -> "gare"
    "hotel", "lodging" -> "hôtel"
    "clothing_store", "clothes" -> "vêtements"
    "hairdresser" -> "coiffeur"
    "shop" -> "magasin"
    "sports", "sports_centre" -> "sport"
    else -> value.replace('_', ' ')
}

data class Step(val instruction: String, val distance: Double, val lat: Double, val lon: Double)

data class RouteResult(
    val points: List<Pair<Double, Double>>, // (lat, lon)
    val distance: Double,                    // mètres
    val duration: Double,                    // secondes
    val steps: List<Step>,
    val via: String = "",                    // grande route empruntée
    val trafficDelay: Double = 0.0,          // secondes perdues dans les bouchons (TomTom)
    val withTraffic: Boolean = false,
)

/** Contraintes d'itinéraire. */
enum class Avoid(val label: String) {
    HIGHWAYS("autoroutes"),
    TOLLS("péages"),
    FERRIES("ferries"),
}

enum class Mode(val label: String, val path: String, val valhalla: String) {
    CAR("voiture", "routed-car", "auto"),
    BIKE("vélo", "routed-bike", "bicycle"),
    FOOT("à pied", "routed-foot", "pedestrian"),
}

/**
 * Services gratuits basés sur OpenStreetMap :
 * - Photon (komoot) pour la recherche d'adresses
 * - OSRM (serveurs FOSSGIS routing.openstreetmap.de) pour les itinéraires
 */
object MapApi {
    private const val UA = "Essentiel-launcher/0.3 (app Android perso)"

    private fun get(url: String): String {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 15_000
            setRequestProperty("User-Agent", UA)
        }
        try {
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun placeFrom(f: JSONObject): Place? {
        val coords = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return null
        val p = f.optJSONObject("properties") ?: JSONObject()
        val street = listOf(p.optString("housenumber"), p.optString("street")).filter { it.isNotBlank() }.joinToString(" ")
        val city = listOf(p.optString("postcode"), p.optString("city").ifBlank { p.optString("county") })
            .filter { it.isNotBlank() }.joinToString(" ")
        val name = p.optString("name").ifBlank { street.ifBlank { city } }
        val address = listOf(if (name == street) "" else street, city).filter { it.isNotBlank() }.joinToString(", ")
        val cat = p.optString("osm_value").let { if (it.isBlank() || it == "yes") "" else frenchCategory(it) }
        return Place(name.ifBlank { "Lieu" }, address, coords.getDouble(1), coords.getDouble(0), cat)
    }

    fun search(query: String, nearLat: Double?, nearLon: Double?, osmTag: String? = null, limit: Int = 10): List<Place> = try {
        val q = URLEncoder.encode(query, "UTF-8")
        val bias = if (nearLat != null && nearLon != null)
            String.format(Locale.US, "&lat=%.5f&lon=%.5f&location_bias_scale=0.5", nearLat, nearLon) else ""
        val tag = if (osmTag != null) "&osm_tag=" + URLEncoder.encode(osmTag, "UTF-8") else ""
        val json = JSONObject(get("https://photon.komoot.io/api/?q=$q&lang=fr&limit=$limit$bias$tag"))
        val arr = json.optJSONArray("features") ?: JSONArray()
        (0 until arr.length()).mapNotNull { placeFrom(arr.getJSONObject(it)) }
    } catch (e: Exception) {
        emptyList()
    }

    fun reverse(lat: Double, lon: Double): Place? = try {
        val json = JSONObject(get(String.format(Locale.US, "https://photon.komoot.io/reverse?lat=%.6f&lon=%.6f&lang=fr", lat, lon)))
        json.optJSONArray("features")?.optJSONObject(0)?.let { placeFrom(it) }?.copy(lat = lat, lon = lon)
    } catch (e: Exception) {
        null
    }

    fun route(mode: Mode, fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): RouteResult? = try {
        val url = String.format(
            Locale.US,
            "https://routing.openstreetmap.de/%s/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson&steps=true",
            mode.path, fromLon, fromLat, toLon, toLat
        )
        val json = JSONObject(get(url))
        val r = json.getJSONArray("routes").getJSONObject(0)
        val coords = r.getJSONObject("geometry").getJSONArray("coordinates")
        val points = (0 until coords.length()).map { i -> coords.getJSONArray(i).let { it.getDouble(1) to it.getDouble(0) } }
        val steps = mutableListOf<Step>()
        val legs = r.getJSONArray("legs")
        for (l in 0 until legs.length()) {
            val s = legs.getJSONObject(l).getJSONArray("steps")
            for (i in 0 until s.length()) {
                val st = s.getJSONObject(i)
                val man = st.getJSONObject("maneuver")
                val loc = man.getJSONArray("location")
                steps += Step(instruction(man, st.optString("name")), st.optDouble("distance", 0.0), loc.getDouble(1), loc.getDouble(0))
            }
        }
        RouteResult(points, r.getDouble("distance"), r.getDouble("duration"), steps)
    } catch (e: Exception) {
        null
    }

    /**
     * Plusieurs itinéraires (jusqu'à 3), avec contraintes.
     * - Avec une clé TomTom et en voiture : itinéraires calculés avec le trafic en temps réel.
     * - Sinon : Valhalla (serveur public FOSSGIS / OpenStreetMap), puis OSRM en secours.
     */
    fun routes(
        mode: Mode, fromLat: Double, fromLon: Double, toLat: Double, toLon: Double,
        avoid: Set<Avoid>, tomtomKey: String?,
    ): List<RouteResult> {
        if (mode == Mode.CAR && !tomtomKey.isNullOrBlank()) {
            tomtom(tomtomKey, fromLat, fromLon, toLat, toLon, avoid).takeIf { it.isNotEmpty() }?.let { return it }
        }
        valhalla(mode, fromLat, fromLon, toLat, toLon, avoid).takeIf { it.isNotEmpty() }?.let { return it }
        return listOfNotNull(route(mode, fromLat, fromLon, toLat, toLon))
    }

    private fun valhalla(mode: Mode, fLat: Double, fLon: Double, tLat: Double, tLon: Double, avoid: Set<Avoid>): List<RouteResult> = try {
        val costingOptions = JSONObject()
        if (mode == Mode.CAR) {
            val auto = JSONObject()
            if (Avoid.HIGHWAYS in avoid) auto.put("use_highways", 0.0)
            if (Avoid.TOLLS in avoid) auto.put("use_tolls", 0.0)
            if (Avoid.FERRIES in avoid) auto.put("use_ferry", 0.0)
            costingOptions.put("auto", auto)
        }
        val body = JSONObject()
            .put("locations", JSONArray()
                .put(JSONObject().put("lat", fLat).put("lon", fLon))
                .put(JSONObject().put("lat", tLat).put("lon", tLon)))
            .put("costing", mode.valhalla)
            .put("costing_options", costingOptions)
            .put("alternates", 2)
            .put("units", "kilometers")
            .put("language", "fr-FR")
            .put("directions_options", JSONObject().put("language", "fr-FR").put("units", "kilometers"))
        val json = JSONObject(get("https://valhalla1.openstreetmap.de/route?json=" + URLEncoder.encode(body.toString(), "UTF-8")))
        val trips = mutableListOf(json.getJSONObject("trip"))
        json.optJSONArray("alternates")?.let { alts ->
            for (i in 0 until alts.length()) alts.getJSONObject(i).optJSONObject("trip")?.let { trips += it }
        }
        trips.mapNotNull { trip -> parseValhallaTrip(trip) }
    } catch (e: Exception) {
        emptyList()
    }

    private fun parseValhallaTrip(trip: JSONObject): RouteResult? {
        val legs = trip.optJSONArray("legs") ?: return null
        val points = mutableListOf<Pair<Double, Double>>()
        val steps = mutableListOf<Step>()
        val roadLengths = HashMap<String, Double>()
        for (l in 0 until legs.length()) {
            val leg = legs.getJSONObject(l)
            val shape = decodePolyline(leg.getString("shape"), 1e6)
            val offset = points.size
            points += shape
            val mans = leg.optJSONArray("maneuvers") ?: JSONArray()
            for (i in 0 until mans.length()) {
                val m = mans.getJSONObject(i)
                val idx = (m.optInt("begin_shape_index", 0)).coerceIn(0, shape.lastIndex)
                val (lat, lon) = shape[idx]
                val km = m.optDouble("length", 0.0)
                val text = m.optString("verbal_pre_transition_instruction").ifBlank { m.optString("instruction") }
                steps += Step(m.optString("instruction").ifBlank { text }, km * 1000, lat, lon)
                m.optJSONArray("street_names")?.optString(0)?.takeIf { it.isNotBlank() }?.let { n ->
                    roadLengths[n] = (roadLengths[n] ?: 0.0) + km
                }
            }
        }
        val summary = trip.getJSONObject("summary")
        val via = roadLengths.maxByOrNull { it.value }?.key.orEmpty()
        return RouteResult(points, summary.getDouble("length") * 1000, summary.getDouble("time"), steps, via)
    }

    private fun tomtom(key: String, fLat: Double, fLon: Double, tLat: Double, tLon: Double, avoid: Set<Avoid>): List<RouteResult> = try {
        val sb = StringBuilder(
            String.format(
                Locale.US,
                "https://api.tomtom.com/routing/1/calculateRoute/%.6f,%.6f:%.6f,%.6f/json?key=%s" +
                    "&traffic=true&maxAlternatives=2&instructionsType=text&language=fr-FR&travelMode=car&routeType=fastest",
                fLat, fLon, tLat, tLon, URLEncoder.encode(key.trim(), "UTF-8")
            )
        )
        if (Avoid.HIGHWAYS in avoid) sb.append("&avoid=motorways")
        if (Avoid.TOLLS in avoid) sb.append("&avoid=tollRoads")
        if (Avoid.FERRIES in avoid) sb.append("&avoid=ferries")
        val json = JSONObject(get(sb.toString()))
        val routes = json.getJSONArray("routes")
        (0 until routes.length()).map { r ->
            val route = routes.getJSONObject(r)
            val summary = route.getJSONObject("summary")
            val points = mutableListOf<Pair<Double, Double>>()
            val legs = route.getJSONArray("legs")
            for (l in 0 until legs.length()) {
                val pts = legs.getJSONObject(l).getJSONArray("points")
                for (i in 0 until pts.length()) pts.getJSONObject(i).let { points += it.getDouble("latitude") to it.getDouble("longitude") }
            }
            val steps = mutableListOf<Step>()
            val instr = route.optJSONObject("guidance")?.optJSONArray("instructions") ?: JSONArray()
            for (i in 0 until instr.length()) {
                val ins = instr.getJSONObject(i)
                val pt = ins.optJSONObject("point")
                val nextOffset = if (i + 1 < instr.length()) instr.getJSONObject(i + 1).optDouble("routeOffsetInMeters", 0.0) else summary.getDouble("lengthInMeters")
                steps += Step(
                    ins.optString("message").ifBlank { "Continuez" },
                    (nextOffset - ins.optDouble("routeOffsetInMeters", 0.0)).coerceAtLeast(0.0),
                    pt?.optDouble("latitude") ?: points.first().first,
                    pt?.optDouble("longitude") ?: points.first().second
                )
            }
            val via = (0 until instr.length()).map { instr.getJSONObject(it) }
                .mapNotNull { it.optString("roadNumbers").takeIf { n -> n.isNotBlank() && n != "[]" } ?: it.optString("street").takeIf { n -> n.isNotBlank() } }
                .firstOrNull()?.trim('[', ']', '"').orEmpty()
            RouteResult(
                points, summary.getDouble("lengthInMeters"), summary.getDouble("travelTimeInSeconds"), steps,
                via, summary.optDouble("trafficDelayInSeconds", 0.0), true
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    /** Décode une « polyline » encodée (format Google, précision 1e5 ou 1e6). */
    fun decodePolyline(encoded: String, precision: Double): List<Pair<Double, Double>> {
        val out = ArrayList<Pair<Double, Double>>()
        var index = 0
        var lat = 0L
        var lon = 0L
        while (index < encoded.length) {
            var result = 0L; var shift = 0; var b: Int
            do { b = encoded[index++].code - 63; result = result or ((b and 0x1f).toLong() shl shift); shift += 5 } while (b >= 0x20)
            lat += if (result and 1L != 0L) (result shr 1).inv() else (result shr 1)
            result = 0L; shift = 0
            do { b = encoded[index++].code - 63; result = result or ((b and 0x1f).toLong() shl shift); shift += 5 } while (b >= 0x20)
            lon += if (result and 1L != 0L) (result shr 1).inv() else (result shr 1)
            out += (lat / precision) to (lon / precision)
        }
        return out
    }

    /** Plusieurs itinéraires en une seule source : "sel" = 1 pour celui choisi. */
    fun routesJson(routes: List<RouteResult>, selected: Int): String {
        val feats = routes.mapIndexed { i, r ->
            val coords = r.points.joinToString(",") { (lat, lon) -> String.format(Locale.US, "[%.6f,%.6f]", lon, lat) }
            """{"type":"Feature","properties":{"i":$i,"sel":${if (i == selected) 1 else 0}},"geometry":{"type":"LineString","coordinates":[$coords]}}"""
        }
        return """{"type":"FeatureCollection","features":[${feats.joinToString(",")}]}"""
    }

    fun meJson(lat: Double, lon: Double, bearing: Float): String = String.format(
        Locale.US,
        """{"type":"Feature","properties":{"bearing":%.1f},"geometry":{"type":"Point","coordinates":[%.6f,%.6f]}}""",
        bearing, lon, lat
    )

    private fun direction(modifier: String): String = when (modifier) {
        "left" -> "à gauche"
        "right" -> "à droite"
        "slight left" -> "légèrement à gauche"
        "slight right" -> "légèrement à droite"
        "sharp left" -> "franchement à gauche"
        "sharp right" -> "franchement à droite"
        "uturn" -> "demi-tour"
        else -> "tout droit"
    }

    private fun instruction(man: JSONObject, name: String): String {
        val type = man.optString("type")
        val mod = man.optString("modifier")
        val on = if (name.isNotBlank()) " sur $name" else ""
        return when (type) {
            "depart" -> "Partez$on"
            "arrive" -> "Vous êtes arrivé"
            "turn", "end of road" -> if (mod == "uturn") "Faites demi-tour$on" else
                (if (type == "end of road") "Au bout de la route, tournez " else "Tournez ") + direction(mod) + on
            "new name", "continue" -> if (mod.isBlank() || mod == "straight") "Continuez$on" else "Continuez ${direction(mod)}$on"
            "merge" -> "Insérez-vous$on"
            "on ramp" -> "Prenez la bretelle$on"
            "off ramp" -> "Prenez la sortie$on"
            "fork" -> "Restez ${direction(mod)}$on"
            "roundabout", "rotary", "roundabout turn" -> {
                val exit = man.optInt("exit", 0)
                if (exit > 0) "Au rond-point, prenez la ${if (exit == 1) "1re" else "${exit}e"} sortie$on" else "Prenez le rond-point$on"
            }
            "exit roundabout", "exit rotary" -> "Sortez du rond-point$on"
            else -> "Continuez$on"
        }
    }

    /** Distance en mètres entre deux points. */
    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    fun formatDistance(m: Double): String = when {
        m < 950 -> "${((m / 10).roundToInt() * 10).coerceAtLeast(10)} m"
        m < 10_000 -> String.format(Locale.FRANCE, "%.1f km", m / 1000)
        else -> "${(m / 1000).roundToInt()} km"
    }

    fun formatDuration(s: Double): String {
        val min = (s / 60).roundToInt().coerceAtLeast(1)
        return if (min >= 60) "${min / 60} h ${String.format(Locale.FRANCE, "%02d", min % 60)}" else "$min min"
    }

    fun lineJson(points: List<Pair<Double, Double>>): String {
        val coords = points.joinToString(",") { (lat, lon) -> String.format(Locale.US, "[%.6f,%.6f]", lon, lat) }
        return """{"type":"Feature","properties":{},"geometry":{"type":"LineString","coordinates":[$coords]}}"""
    }

    fun pointJson(lat: Double, lon: Double): String =
        String.format(Locale.US, """{"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[%.6f,%.6f]}}""", lon, lat)

    fun pointsJson(places: List<Place>): String {
        val feats = places.mapIndexed { i, p ->
            val name = JSONObject.quote(p.name)
            String.format(
                Locale.US,
                """{"type":"Feature","properties":{"i":%d,"name":%s},"geometry":{"type":"Point","coordinates":[%.6f,%.6f]}}""",
                i, name, p.lon, p.lat
            )
        }
        return """{"type":"FeatureCollection","features":[${feats.joinToString(",")}]}"""
    }

    const val EMPTY = """{"type":"FeatureCollection","features":[]}"""
}
