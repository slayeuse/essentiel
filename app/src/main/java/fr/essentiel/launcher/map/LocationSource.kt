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
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Position en direct.
 * 1) Service de localisation de Google (GPS + Wi-Fi + antennes, comme les autres apps) si disponible.
 * 2) Sinon, GPS d'Android. Les vieilles positions mémorisées (plus de 2 min) sont ignorées.
 * Retourne une fonction pour arrêter.
 */
object LocationSource {

    @SuppressLint("MissingPermission")
    fun start(ctx: Context, onLocation: (Location) -> Unit): () -> Unit {
        val fine = ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return {}

        // ----- Google (fusionnée) -----
        try {
            val client = LocationServices.getFusedLocationProviderClient(ctx)
            val request = LocationRequest.Builder(
                if (fine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY, 1000L
            ).setMinUpdateIntervalMillis(500L).setWaitForAccurateLocation(false).build()
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let(onLocation)
                }
            }
            client.lastLocation.addOnSuccessListener { loc ->
                if (loc != null && System.currentTimeMillis() - loc.time < 2 * 60_000L) onLocation(loc)
            }
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            return { try { client.removeLocationUpdates(callback) } catch (e: Exception) { } }
        } catch (e: Throwable) {
            // pas de services Google : on continue avec le GPS d'Android
        }

        // ----- GPS d'Android -----
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return {}
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) = onLocation(location)
            @Deprecated("API 29") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        try {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .mapNotNull { p -> try { lm.getLastKnownLocation(p) } catch (e: Exception) { null } }
                .filter { System.currentTimeMillis() - it.time < 2 * 60_000L }
                .minByOrNull { it.accuracy }?.let(onLocation)
            if (fine) lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, listener, Looper.getMainLooper())
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 5f, listener, Looper.getMainLooper())
        } catch (e: Exception) { }
        return { try { lm.removeUpdates(listener) } catch (e: Exception) { } }
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
