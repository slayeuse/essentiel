package fr.essentiel.launcher.control

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.provider.Settings
import android.widget.Toast

/**
 * Tout ce que le centre de contrôle peut lire / changer sans root.
 * Certaines actions demandent une autorisation spéciale (donnée une fois, ou par le script Mac).
 */
object Controls {

    // ---------- Autorisations ----------
    fun canWriteSettings(ctx: Context) = Settings.System.canWrite(ctx)

    fun hasDndAccess(ctx: Context) =
        ctx.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true

    fun hasSecureSettings(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun hasNotifAccess(ctx: Context): Boolean {
        val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners").orEmpty()
        return flat.split(":").any { ComponentName.unflattenFromString(it)?.packageName == ctx.packageName }
    }

    fun canOverlay(ctx: Context) = Settings.canDrawOverlays(ctx)

    fun openWriteSettings(ctx: Context) =
        open(ctx, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + ctx.packageName)))

    fun openDndAccess(ctx: Context) = open(ctx, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
    fun openNotifAccess(ctx: Context) = open(ctx, Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
    fun openOverlay(ctx: Context) =
        open(ctx, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName)))

    fun open(ctx: Context, intent: Intent) {
        try { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { Toast.makeText(ctx, "Réglage introuvable sur ce téléphone", Toast.LENGTH_SHORT).show() }
    }

    private fun need(ctx: Context, what: String, then: () -> Unit) {
        Toast.makeText(ctx, "Autorise « $what » pour Essentiel", Toast.LENGTH_LONG).show()
        then()
    }

    // ---------- Lampe torche ----------
    @Volatile var torchOn = false
    private var torchId: String? = null
    private var torchRegistered = false

    private fun torchCamera(ctx: Context): String? {
        torchId?.let { return it }
        val cm = ctx.getSystemService(CameraManager::class.java) ?: return null
        if (!torchRegistered) {
            torchRegistered = true
            try {
                cm.registerTorchCallback(object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) { if (cameraId == torchId) torchOn = enabled }
                }, null)
            } catch (e: Exception) { }
        }
        torchId = try {
            cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        } catch (e: Exception) { null }
        return torchId
    }

    fun setTorch(ctx: Context, on: Boolean) {
        val id = torchCamera(ctx) ?: return
        try {
            ctx.getSystemService(CameraManager::class.java)?.setTorchMode(id, on)
            torchOn = on
        } catch (e: Exception) {
            Toast.makeText(ctx, "Lampe indisponible (appareil photo utilisé ?)", Toast.LENGTH_SHORT).show()
        }
    }

    fun initTorch(ctx: Context) { torchCamera(ctx) }

    // ---------- Luminosité ----------
    fun brightness(ctx: Context): Float = try {
        Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
    } catch (e: Exception) { 0.5f }

    fun setBrightness(ctx: Context, v: Float): Boolean {
        if (!canWriteSettings(ctx)) return false
        try {
            Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (v.coerceIn(0.01f, 1f) * 255).toInt())
        } catch (e: Exception) { return false }
        return true
    }

    fun autoBrightness(ctx: Context) = try {
        Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
    } catch (e: Exception) { false }

    fun setAutoBrightness(ctx: Context, on: Boolean) {
        if (!canWriteSettings(ctx)) { need(ctx, "Modifier les paramètres système") { openWriteSettings(ctx) }; return }
        try {
            Settings.System.putInt(
                ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                if (on) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
        } catch (e: Exception) { }
    }

    // ---------- Rotation ----------
    fun autoRotate(ctx: Context) = try {
        Settings.System.getInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION) == 1
    } catch (e: Exception) { false }

    fun setAutoRotate(ctx: Context, on: Boolean) {
        if (!canWriteSettings(ctx)) { need(ctx, "Modifier les paramètres système") { openWriteSettings(ctx) }; return }
        try { Settings.System.putInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (on) 1 else 0) } catch (e: Exception) { }
    }

    // ---------- Wi-Fi / données / avion (Android 10 : panneau système) ----------
    fun wifiOn(ctx: Context) = try {
        (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled
    } catch (e: Exception) { false }

    fun openWifi(ctx: Context) = open(ctx, Intent(Settings.Panel.ACTION_WIFI))
    fun openData(ctx: Context) = open(ctx, Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
    fun openNfc(ctx: Context) = open(ctx, Intent(Settings.Panel.ACTION_NFC))
    fun openAirplane(ctx: Context) = open(ctx, Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))

    fun airplaneOn(ctx: Context) = try {
        Settings.Global.getInt(ctx.contentResolver, Settings.Global.AIRPLANE_MODE_ON) == 1
    } catch (e: Exception) { false }

    fun openHotspot(ctx: Context) {
        try {
            ctx.startActivity(
                Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.TetherSettings"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) { open(ctx, Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
    }

    // ---------- Bluetooth ----------
    @Suppress("DEPRECATION")
    fun bluetoothOn(): Boolean = try { BluetoothAdapter.getDefaultAdapter()?.isEnabled == true } catch (e: Exception) { false }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun setBluetooth(ctx: Context, on: Boolean) {
        val ok = try {
            val a = BluetoothAdapter.getDefaultAdapter()
            if (a == null) false else if (on) a.enable() else a.disable()
        } catch (e: Exception) { false }
        if (!ok) open(ctx, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    }

    // ---------- Ne pas déranger / sonnerie ----------
    fun dndOn(ctx: Context): Boolean {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return false
        return nm.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL
    }

    fun setDnd(ctx: Context, on: Boolean) {
        if (!hasDndAccess(ctx)) { need(ctx, "Accès à Ne pas déranger") { openDndAccess(ctx) }; return }
        ctx.getSystemService(NotificationManager::class.java)?.setInterruptionFilter(
            if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL
        )
    }

    fun ringer(ctx: Context): Int = ctx.getSystemService(AudioManager::class.java)?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL

    fun setRinger(ctx: Context, mode: Int) {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return
        if (mode == AudioManager.RINGER_MODE_SILENT && !hasDndAccess(ctx)) {
            need(ctx, "Accès à Ne pas déranger") { openDndAccess(ctx) }; return
        }
        try { am.ringerMode = mode } catch (e: SecurityException) { need(ctx, "Accès à Ne pas déranger") { openDndAccess(ctx) } }
    }

    // ---------- Volume ----------
    fun volume(ctx: Context, stream: Int = AudioManager.STREAM_MUSIC): Float {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return 0f
        val max = am.getStreamMaxVolume(stream).coerceAtLeast(1)
        return am.getStreamVolume(stream).toFloat() / max
    }

    fun setVolume(ctx: Context, v: Float, stream: Int = AudioManager.STREAM_MUSIC) {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return
        val max = am.getStreamMaxVolume(stream)
        try { am.setStreamVolume(stream, Math.round(v.coerceIn(0f, 1f) * max), 0) } catch (e: SecurityException) { }
    }

    // ---------- Position ----------
    fun locationOn(ctx: Context) = try { ctx.getSystemService(LocationManager::class.java)?.isLocationEnabled == true } catch (e: Exception) { false }

    @Suppress("DEPRECATION")
    fun setLocation(ctx: Context, on: Boolean) {
        if (hasSecureSettings(ctx)) {
            try {
                Settings.Secure.putInt(
                    ctx.contentResolver, Settings.Secure.LOCATION_MODE,
                    if (on) Settings.Secure.LOCATION_MODE_HIGH_ACCURACY else Settings.Secure.LOCATION_MODE_OFF
                )
                return
            } catch (e: Exception) { }
        }
        open(ctx, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }

    // ---------- Batterie ----------
    fun battery(ctx: Context): Int = ctx.getSystemService(BatteryManager::class.java)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    fun charging(ctx: Context): Boolean = ctx.getSystemService(BatteryManager::class.java)?.isCharging == true

    // ---------- Barre d'état (mode avancé : WRITE_SECURE_SETTINGS) ----------
    val STATUS_ICONS = listOf(
        "alarm_clock" to "Réveil", "bluetooth" to "Bluetooth", "headset" to "Casque", "volume" to "Mode vibreur / silencieux",
        "zen" to "Ne pas déranger", "nfc" to "NFC", "rotate" to "Rotation", "location" to "Position", "hotspot" to "Partage de connexion",
        "cast" to "Diffusion", "data_saver" to "Économiseur de données", "ime" to "Clavier", "airplane" to "Mode avion",
        "mobile" to "Réseau mobile", "wifi" to "Wi-Fi", "clock" to "Heure",
    )

    fun hiddenIcons(ctx: Context): Set<String> =
        Settings.Secure.getString(ctx.contentResolver, "icon_blacklist").orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    fun setIconHidden(ctx: Context, slot: String, hidden: Boolean): Boolean {
        if (!hasSecureSettings(ctx)) return false
        val now = hiddenIcons(ctx).toMutableSet()
        if (hidden) now += slot else now -= slot
        return try { Settings.Secure.putString(ctx.contentResolver, "icon_blacklist", now.joinToString(",")) } catch (e: Exception) { false }
    }
}
