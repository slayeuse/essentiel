package fr.essentiel.launcher.weather

import android.content.Context
import android.location.Location
import fr.essentiel.launcher.map.LocationSource
import fr.essentiel.launcher.map.MapApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import kotlin.coroutines.resume

data class City(val name: String, val lat: Double, val lon: Double, val isCurrent: Boolean = false) {
    val key: String get() = if (isCurrent) "ici" else String.format(Locale.US, "%.3f,%.3f", lat, lon)
}

data class Hour(val time: LocalDateTime, val temp: Double, val code: Int, val isDay: Boolean, val rainProb: Int)

data class Day(
    val date: LocalDate, val code: Int, val max: Double, val min: Double, val rainProb: Int, val rainSum: Double,
    val sunrise: LocalDateTime?, val sunset: LocalDateTime?, val uvMax: Double, val windMax: Double,
)

data class Forecast(
    val now: LocalDateTime,
    val temp: Double, val feels: Double, val humidity: Int, val isDay: Boolean, val code: Int,
    val wind: Double, val windDir: Int, val gusts: Double, val pressure: Double, val cloud: Int,
    val precip: Double, val uv: Double, val visibility: Double, val dewPoint: Double,
    val hours: List<Hour>, val days: List<Day>, val aqi: Int?, val pm25: Double?,
    val fetchedAt: Long,
)

/** Météo d'Open-Meteo (gratuit, sans compte) + qualité de l'air (Open-Meteo Air Quality). */
object WeatherData {

    private fun get(url: String): String {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 15_000
            setRequestProperty("User-Agent", "Essentiel-launcher (app Android perso)")
        }
        try { return c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() }
    }

    private fun sp(ctx: Context) = ctx.getSharedPreferences("meteo", Context.MODE_PRIVATE)

    // ---------- Villes enregistrées ----------
    fun cities(ctx: Context): List<City> = try {
        val arr = JSONArray(sp(ctx).getString("villes", "[]"))
        (0 until arr.length()).map { arr.getJSONObject(it).let { o -> City(o.getString("name"), o.getDouble("lat"), o.getDouble("lon")) } }
    } catch (e: Exception) { emptyList() }

    fun saveCities(ctx: Context, list: List<City>) {
        val arr = JSONArray()
        list.filter { !it.isCurrent }.forEach { arr.put(JSONObject().put("name", it.name).put("lat", it.lat).put("lon", it.lon)) }
        sp(ctx).edit().putString("villes", arr.toString()).apply()
    }

    fun lastHereName(ctx: Context): String = sp(ctx).getString("ici_nom", "Ma position").orEmpty()

    // ---------- Position actuelle ----------
    suspend fun currentLocation(ctx: Context): Location? = withTimeoutOrNull(12_000) {
        suspendCancellableCoroutine { cont ->
            var stop: () -> Unit = {}
            val stopper = LocationSource.start(ctx) { loc ->
                if (cont.isActive && loc.accuracy < 3000) { cont.resume(loc); stop() }
            }
            stop = stopper
            if (!cont.isActive) stopper() // la position est arrivée tout de suite
            cont.invokeOnCancellation { stopper() }
        }
    }

    suspend fun hereCity(ctx: Context): City? {
        val loc = currentLocation(ctx) ?: return null
        val name = withContext(Dispatchers.IO) {
            MapApi.reverse(loc.latitude, loc.longitude)?.address?.substringAfterLast(", ")
                ?.replace(Regex("^\\d{5} "), "")?.takeIf { it.isNotBlank() }
        } ?: "Ma position"
        sp(ctx).edit().putString("ici_nom", name).putFloat("ici_lat", loc.latitude.toFloat()).putFloat("ici_lon", loc.longitude.toFloat()).apply()
        return City(name, loc.latitude, loc.longitude, true)
    }

    fun lastHere(ctx: Context): City? {
        val s = sp(ctx)
        if (!s.contains("ici_lat")) return null
        return City(lastHereName(ctx), s.getFloat("ici_lat", 0f).toDouble(), s.getFloat("ici_lon", 0f).toDouble(), true)
    }

    // ---------- Prévisions ----------
    fun cached(ctx: Context, city: City): Forecast? = try {
        val raw = sp(ctx).getString("f_" + city.key, null) ?: return null
        val o = JSONObject(raw)
        parse(o.getJSONObject("meteo"), o.optJSONObject("air"), o.getLong("t"))
    } catch (e: Exception) { null }

    fun fetch(ctx: Context, city: City): Forecast? = try {
        val base = String.format(Locale.US, "latitude=%.4f&longitude=%.4f", city.lat, city.lon)
        val meteo = JSONObject(
            get(
                "https://api.open-meteo.com/v1/forecast?$base&timezone=auto&forecast_days=10&wind_speed_unit=kmh" +
                    "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code," +
                    "cloud_cover,pressure_msl,wind_speed_10m,wind_direction_10m,wind_gusts_10m,dew_point_2m" +
                    "&hourly=temperature_2m,precipitation_probability,weather_code,is_day,uv_index,visibility" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_probability_max," +
                    "precipitation_sum,uv_index_max,wind_speed_10m_max"
            )
        )
        val air = try {
            JSONObject(get("https://air-quality-api.open-meteo.com/v1/air-quality?$base&timezone=auto&current=european_aqi,pm2_5"))
        } catch (e: Exception) { null }
        val now = System.currentTimeMillis()
        sp(ctx).edit().putString(
            "f_" + city.key, JSONObject().put("meteo", meteo).put("air", air ?: JSONObject()).put("t", now).toString()
        ).apply()
        parse(meteo, air, now)
    } catch (e: Exception) { null }

    private fun dt(s: String): LocalDateTime? = try { LocalDateTime.parse(s) } catch (e: Exception) { null }

    private fun parse(m: JSONObject, air: JSONObject?, fetchedAt: Long): Forecast {
        val cur = m.getJSONObject("current")
        val now = dt(cur.getString("time")) ?: LocalDateTime.now()
        val h = m.getJSONObject("hourly")
        val times = h.getJSONArray("time")
        val hourList = mutableListOf<Hour>()
        var uvNow = 0.0
        var visNow = 0.0
        val nowHour = now.withMinute(0).withSecond(0)
        for (i in 0 until times.length()) {
            val t = dt(times.getString(i)) ?: continue
            if (t == nowHour) {
                uvNow = h.getJSONArray("uv_index").optDouble(i, 0.0)
                visNow = h.getJSONArray("visibility").optDouble(i, 0.0)
            }
            if (t.isBefore(nowHour) || hourList.size >= 26) continue
            hourList += Hour(
                t, h.getJSONArray("temperature_2m").optDouble(i, 0.0), h.getJSONArray("weather_code").optInt(i, 0),
                h.getJSONArray("is_day").optInt(i, 1) == 1, h.getJSONArray("precipitation_probability").optInt(i, 0)
            )
        }
        val d = m.getJSONObject("daily")
        val dTimes = d.getJSONArray("time")
        val days = (0 until dTimes.length()).map { i ->
            Day(
                LocalDate.parse(dTimes.getString(i)), d.getJSONArray("weather_code").optInt(i, 0),
                d.getJSONArray("temperature_2m_max").optDouble(i, 0.0), d.getJSONArray("temperature_2m_min").optDouble(i, 0.0),
                d.getJSONArray("precipitation_probability_max").optInt(i, 0), d.getJSONArray("precipitation_sum").optDouble(i, 0.0),
                dt(d.getJSONArray("sunrise").optString(i)), dt(d.getJSONArray("sunset").optString(i)),
                d.getJSONArray("uv_index_max").optDouble(i, 0.0), d.getJSONArray("wind_speed_10m_max").optDouble(i, 0.0)
            )
        }
        val airCur = air?.optJSONObject("current")
        return Forecast(
            now = now,
            temp = cur.optDouble("temperature_2m"), feels = cur.optDouble("apparent_temperature"),
            humidity = cur.optInt("relative_humidity_2m"), isDay = cur.optInt("is_day", 1) == 1,
            code = cur.optInt("weather_code"), wind = cur.optDouble("wind_speed_10m"),
            windDir = cur.optInt("wind_direction_10m"), gusts = cur.optDouble("wind_gusts_10m"),
            pressure = cur.optDouble("pressure_msl"), cloud = cur.optInt("cloud_cover"),
            precip = cur.optDouble("precipitation", 0.0), uv = uvNow, visibility = visNow,
            dewPoint = cur.optDouble("dew_point_2m", 0.0),
            hours = hourList, days = days,
            aqi = airCur?.let { if (it.has("european_aqi") && !it.isNull("european_aqi")) it.optInt("european_aqi") else null },
            pm25 = airCur?.let { if (it.has("pm2_5") && !it.isNull("pm2_5")) it.optDouble("pm2_5") else null },
            fetchedAt = fetchedAt,
        )
    }

    // ---------- Textes ----------
    fun windDirection(deg: Int): String {
        val dirs = listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
        return dirs[((deg % 360 + 360) % 360 + 22) / 45 % 8]
    }

    fun uvLabel(uv: Double) = when {
        uv < 3 -> "faible"
        uv < 6 -> "modéré"
        uv < 8 -> "élevé"
        uv < 11 -> "très élevé"
        else -> "extrême"
    }

    fun aqiLabel(aqi: Int) = when {
        aqi <= 20 -> "bonne"
        aqi <= 40 -> "correcte"
        aqi <= 60 -> "moyenne"
        aqi <= 80 -> "médiocre"
        aqi <= 100 -> "mauvaise"
        else -> "très mauvaise"
    }
}
