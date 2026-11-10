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

data class Place(val name: String, val address: String, val lat: Double, val lon: Double)

data class Step(val instruction: String, val distance: Double, val lat: Double, val lon: Double)

data class RouteResult(
    val points: List<Pair<Double, Double>>, // (lat, lon)
    val distance: Double,                    // mètres
    val duration: Double,                    // secondes
    val steps: List<Step>,
)

enum class Mode(val label: String, val path: String) {
    CAR("voiture", "routed-car"),
    BIKE("vélo", "routed-bike"),
    FOOT("à pied", "routed-foot"),
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
        return Place(name.ifBlank { "Lieu" }, address, coords.getDouble(1), coords.getDouble(0))
    }

    fun search(query: String, nearLat: Double?, nearLon: Double?): List<Place> = try {
        val q = URLEncoder.encode(query, "UTF-8")
        val bias = if (nearLat != null && nearLon != null) String.format(Locale.US, "&lat=%.5f&lon=%.5f", nearLat, nearLon) else ""
        val json = JSONObject(get("https://photon.komoot.io/api/?q=$q&lang=fr&limit=8$bias"))
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

    const val EMPTY = """{"type":"FeatureCollection","features":[]}"""
}
