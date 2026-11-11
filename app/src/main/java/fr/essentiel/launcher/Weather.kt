package fr.essentiel.launcher

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/** Météo actuelle via Open-Meteo (gratuit, sans compte) à partir de la position du téléphone. */
object Weather {

    data class Now(val temp: Int, val code: Int, val isDay: Boolean)

    fun hasPermission(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun lastKnown(ctx: Context): Location? {
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        return listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { p -> try { lm.getLastKnownLocation(p) } catch (e: Exception) { null } }
            .maxByOrNull { it.time }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private suspend fun freshLocation(ctx: Context): Location? = withTimeoutOrNull(15_000) {
        suspendCancellableCoroutine<Location?> { cont ->
            val lm = ctx.getSystemService(LocationManager::class.java)
            if (lm == null || !lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                cont.resume(null); return@suspendCancellableCoroutine
            }
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (cont.isActive) cont.resume(location)
                }
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            try {
                lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, listener, Looper.getMainLooper())
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(null)
            }
            cont.invokeOnCancellation { lm.removeUpdates(listener) }
        }
    }

    /** Retourne la météo, ou null si impossible (pas de réseau, pas de position…). */
    suspend fun load(ctx: Context): Now? {
        if (!hasPermission(ctx)) return null
        val loc = withContext(Dispatchers.IO) { lastKnown(ctx) }
            ?.takeIf { System.currentTimeMillis() - it.time < 6 * 3_600_000L }
            ?: freshLocation(ctx)
            ?: withContext(Dispatchers.IO) { lastKnown(ctx) }
            ?: return null
        return withContext(Dispatchers.IO) { fetch(loc.latitude, loc.longitude) }
    }

    private fun fetch(lat: Double, lon: Double): Now? = try {
        val url = URL(
            String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f" +
                    "&current=temperature_2m,weather_code,is_day&timezone=auto",
                lat, lon
            )
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000; readTimeout = 8000
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        val cur = JSONObject(body).getJSONObject("current")
        Now(
            temp = cur.getDouble("temperature_2m").roundToInt(),
            code = cur.getInt("weather_code"),
            isDay = cur.optInt("is_day", 1) == 1
        )
    } catch (e: Exception) {
        null
    }

    fun label(code: Int, isDay: Boolean): String = when (code) {
        0 -> if (isDay) "ensoleillé" else "ciel dégagé"
        1 -> if (isDay) "plutôt ensoleillé" else "peu nuageux"
        2 -> "partiellement nuageux"
        3 -> "couvert"
        45, 48 -> "brouillard"
        in 51..57 -> "bruine"
        in 61..67 -> "pluie"
        in 71..77 -> "neige"
        in 80..82 -> "averses"
        85, 86 -> "averses de neige"
        in 95..99 -> "orage"
        else -> "météo"
    }

    fun icon(code: Int, isDay: Boolean): LineIcon = when (code) {
        0 -> if (isDay) Icons.sun else Icons.moon
        1, 2 -> if (isDay) Icons.cloudSun else Icons.moon
        3 -> Icons.cloud
        45, 48 -> Icons.fog
        in 51..67, in 80..82 -> Icons.rain
        in 71..77, 85, 86 -> Icons.snow
        in 95..99 -> Icons.storm
        else -> Icons.cloud
    }
}
