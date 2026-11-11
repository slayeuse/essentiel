package fr.essentiel.launcher.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.view.Surface
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Position en direct, en combinant DEUX sources en même temps :
 * 1) le service de localisation de Google (GPS + Wi-Fi + antennes, comme les autres apps) ;
 * 2) le GPS d'Android directement.
 * On demande aussi une position toute fraîche à chaque ouverture. Les positions mémorisées
 * de plus de 2 minutes sont ignorées. Retourne une fonction pour tout arrêter.
 */
object LocationSource {

    /** Dernière position reçue de chaque source (pour l'écran de diagnostic). */
    val debug = androidx.compose.runtime.mutableStateMapOf<String, Location>()
    /** État des services Google Play (0 = OK). */
    var playStatus by androidx.compose.runtime.mutableIntStateOf(-1)
        private set
    @Volatile private var lastFused = 0L

    fun playStatusText(code: Int): String = when (code) {
        -1 -> "non vérifié"
        ConnectionResult.SUCCESS -> "OK"
        ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> "mise à jour nécessaire"
        ConnectionResult.SERVICE_DISABLED -> "désactivés"
        ConnectionResult.SERVICE_MISSING -> "absents"
        ConnectionResult.SERVICE_UPDATING -> "en cours de mise à jour"
        else -> "erreur $code"
    }

    fun isLocationOn(ctx: Context): Boolean = try {
        ctx.getSystemService(LocationManager::class.java)?.isLocationEnabled ?: true
    } catch (e: Exception) { true }

    /** Garde la meilleure position entre deux sources. */
    fun better(new: Location, old: Location?): Boolean {
        // Quand le service Google répond, on ignore les autres sources (souvent fausses sur certains téléphones)
        if (new.provider != "fused" && System.currentTimeMillis() - lastFused < 20_000) return false
        if (old == null) return true
        if (new.provider == "fused" && old.provider != "fused") return true
        val age = new.time - old.time
        if (age > 8_000) return true                         // l'ancienne est périmée
        if (age < -8_000) return false                       // la nouvelle est plus vieille
        return new.accuracy <= old.accuracy + 10             // à peu près aussi précise et plus récente
    }

    @SuppressLint("MissingPermission")
    fun start(ctx: Context, onLocation: (Location) -> Unit): () -> Unit {
        val fine = ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return {}
        val stops = mutableListOf<() -> Unit>()
        val report: (String, Location) -> Unit = { src, l ->
            debug[src] = l
            if (src == "google") lastFused = System.currentTimeMillis()
            onLocation(l)
        }
        val recent = { l: Location -> System.currentTimeMillis() - l.time < 2 * 60_000L }

        // ----- 1) Google (fusionnée) -----
        try {
            playStatus = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(ctx)
            val ok = playStatus == ConnectionResult.SUCCESS
            if (ok) {
                val client = LocationServices.getFusedLocationProviderClient(ctx)
                val priority = if (fine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
                // position toute fraîche à l'ouverture
                client.getCurrentLocation(
                    CurrentLocationRequest.Builder().setPriority(priority).setMaxUpdateAgeMillis(0).build(), null
                ).addOnSuccessListener { loc -> if (loc != null) report("google", loc) }
                val request = LocationRequest.Builder(priority, 1000L)
                    .setMinUpdateIntervalMillis(500L).setWaitForAccurateLocation(false).build()
                val callback = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) { result.lastLocation?.let { report("google", it) } }
                }
                client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                stops += { try { client.removeLocationUpdates(callback) } catch (e: Exception) { } }
            }
        } catch (e: Throwable) { }

        // ----- 2) GPS d'Android (en parallèle) -----
        try {
            val lm = ctx.getSystemService(LocationManager::class.java)
            if (lm != null) {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) = report(location.provider ?: "android", location)
                    @Deprecated("API 29") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }
                listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                    .mapNotNull { p -> try { lm.getLastKnownLocation(p) } catch (e: Exception) { null } }
                    .filter(recent)
                    .minByOrNull { it.accuracy }?.let { report("mémoire " + (it.provider ?: ""), it) }
                if (fine && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
                }
                if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 0f, listener, Looper.getMainLooper())
                }
                stops += { try { lm.removeUpdates(listener) } catch (e: Exception) { } }
            }
        } catch (e: Exception) { }

        return { stops.forEach { it() } }
    }

    // ---------- Dernière bonne position (pour ouvrir la carte au bon endroit) ----------
    fun saveLast(ctx: Context, l: Location) {
        if (l.hasAccuracy() && l.accuracy > 300f) return   // on ne garde pas les positions arrondies / imprécises
        ctx.getSharedPreferences("carte", Context.MODE_PRIVATE).edit()
            .putFloat("last_lat", l.latitude.toFloat()).putFloat("last_lon", l.longitude.toFloat())
            .putBoolean("last_precise", true).apply()
    }

    fun last(ctx: Context): Pair<Double, Double>? {
        val sp = ctx.getSharedPreferences("carte", Context.MODE_PRIVATE)
        // Les anciennes positions enregistrées (versions ≤ 0.8) peuvent être les fausses : on les oublie.
        if (!sp.getBoolean("last_precise", false)) return null
        if (!sp.contains("last_lat")) return null
        return sp.getFloat("last_lat", 0f).toDouble() to sp.getFloat("last_lon", 0f).toDouble()
    }
}

/** Boussole : direction vers laquelle pointe le haut du téléphone (0 = nord). */
object Compass {
    fun start(ctx: Context, onHeading: (Float) -> Unit): () -> Unit {
        val sm = ctx.getSystemService(SensorManager::class.java) ?: return {}
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: return {}
        val rot = FloatArray(9)
        val remapped = FloatArray(9)
        val orient = FloatArray(3)
        var smooth = Float.NaN
        var lastSent = 0L
        @Suppress("DEPRECATION")
        val display = ctx.getSystemService(WindowManager::class.java)?.defaultDisplay
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                val (ax, ay) = when (display?.rotation) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                SensorManager.remapCoordinateSystem(rot, ax, ay, remapped)
                SensorManager.getOrientation(remapped, orient)
                val deg = ((Math.toDegrees(orient[0].toDouble()) + 360) % 360).toFloat()
                smooth = if (smooth.isNaN()) deg else {
                    var diff = deg - smooth
                    if (diff > 180) diff -= 360
                    if (diff < -180) diff += 360
                    (smooth + diff * 0.15f + 360) % 360
                }
                val now = System.currentTimeMillis()
                if (now - lastSent > 120) { lastSent = now; onHeading(smooth) }
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        return { sm.unregisterListener(listener) }
    }
}
