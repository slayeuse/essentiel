package fr.essentiel.launcher.lock

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import fr.essentiel.launcher.CalendarRepo
import fr.essentiel.launcher.EssentielTheme
import fr.essentiel.launcher.Fonts
import fr.essentiel.launcher.Icons
import fr.essentiel.launcher.LineIcon
import fr.essentiel.launcher.LineIconView
import fr.essentiel.launcher.Palette
import fr.essentiel.launcher.Prefs
import fr.essentiel.launcher.R
import fr.essentiel.launcher.ToolsActivity
import fr.essentiel.launcher.Weather
import fr.essentiel.launcher.bodyStyle
import fr.essentiel.launcher.paper
import fr.essentiel.launcher.phone.CallManager
import fr.essentiel.launcher.reminders.ReminderStore
import fr.essentiel.launcher.serifStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Service discret qui affiche l'écran de verrouillage Essentiel à chaque fois que l'écran s'éteint.
 * Le vrai verrouillage d'Android (code, empreinte) reste derrière.
 */
class LockService : Service() {

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_SCREEN_OFF) return
            if (!Prefs(context).lockScreen) return
            if (CallManager.calls.isNotEmpty()) return // pas pendant un appel
            try {
                context.startActivity(
                    Intent(context, LockActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                )
            } catch (e: Exception) { }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Écran de verrouillage", NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) }
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Écran de verrouillage Essentiel actif")
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, n)
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Prefs(this).lockScreen) { stopSelf(); return START_NOT_STICKY }
        return START_STICKY
    }

    override fun onDestroy() {
        try { unregisterReceiver(screenOff) } catch (e: Exception) { }
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "verrouillage"
        private const val NOTIF_ID = 31

        fun ensure(ctx: Context) {
            try {
                if (Prefs(ctx).lockScreen) ctx.startForegroundService(Intent(ctx, LockService::class.java))
                else ctx.stopService(Intent(ctx, LockService::class.java))
            } catch (e: Exception) { }
        }

        fun canShow(ctx: Context) = Settings.canDrawOverlays(ctx)
    }
}

class LockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        Fonts.init(this)
        setContent {
            EssentielTheme {
                BackHandler { /* le bouton retour ne ferme pas l'écran de verrouillage */ }
                LockContent(
                    onUnlock = { then -> unlock(then) },
                )
            }
        }
    }

    /** Glisser vers le haut : Android demande le code s'il y en a un, puis on ferme. */
    private fun unlock(then: (() -> Unit)?) {
        val km = getSystemService(KeyguardManager::class.java)
        if (km == null || !km.isKeyguardLocked) { then?.invoke(); finish(); return }
        km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() { then?.invoke(); finish() }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

private fun torchCameraId(cm: CameraManager): String? = try {
    cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
} catch (e: Exception) { null }

@Composable
private fun LockContent(onUnlock: ((() -> Unit)?) -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }

    var infoLines by remember { mutableStateOf<List<Pair<LineIcon, String>>>(emptyList()) }
    LaunchedEffect(now / 60_000) {
        infoLines = withContext(Dispatchers.IO) {
            val out = mutableListOf<Pair<LineIcon, String>>()
            ctx.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.let {
                val t = it.triggerTime
                if (t - System.currentTimeMillis() < 24 * 3_600_000L) {
                    out += Icons.bell to "réveil " + SimpleDateFormat("EEE HH:mm", Locale.FRANCE).format(Date(t))
                }
            }
            CalendarRepo.load(ctx)?.next?.let { ev ->
                val time = if (ev.allDay) "journée" else SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(ev.begin))
                out += Icons.calendar to "${ev.title} · $time"
            }
            ReminderStore.all(ctx).firstOrNull { !it.done }?.let {
                out += Icons.list to "${it.text} · ${ReminderStore.label(it.time)}"
            }
            out
        }
    }

    val weather = remember(now / 600_000) { prefs.cachedWeather() }

    // Lampe torche
    val cm = remember { ctx.getSystemService(CameraManager::class.java) }
    val torchId = remember { cm?.let { torchCameraId(it) } }
    var torch by remember { mutableStateOf(false) }

    var drag by remember { mutableFloatStateOf(0f) }

    Box(
        Modifier
            .fillMaxSize()
            .paper()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = { if (drag < -220f) onUnlock(null); drag = 0f },
                    onDragCancel = { drag = 0f },
                    onVerticalDrag = { _, d -> drag = (drag + d).coerceAtMost(0f) },
                )
            }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(0, (drag / 2).roundToInt()) }
                .alpha((1f + drag / 600f).coerceIn(0.3f, 1f))
                .padding(horizontal = 30.dp, vertical = 40.dp),
        ) {
            Spacer(Modifier.height(40.dp))
            Text(SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(now)), style = serifStyle(96))
            Text(
                SimpleDateFormat("EEEE d MMMM", Locale.FRANCE).format(Date(now)),
                style = serifStyle(26, Palette.Ink)
            )
            if (weather != null) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LineIconView(Weather.icon(weather.code, weather.isDay), Palette.Accent, 22.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("${Weather.label(weather.code, weather.isDay)}, ${weather.temp}°", style = bodyStyle(18, Palette.Muted))
                }
            }
            Spacer(Modifier.height(30.dp))
            infoLines.forEach { (icon, text) ->
                Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    LineIconView(icon, Palette.Accent, 20.dp)
                    Spacer(Modifier.width(14.dp))
                    Text(text, style = bodyStyle(16), maxLines = 1)
                }
            }
            Spacer(Modifier.weight(1f))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                LockButton(Icons.flash, torch) {
                    val id = torchId ?: return@LockButton
                    try { cm?.setTorchMode(id, !torch); torch = !torch } catch (e: Exception) { }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⌃", style = bodyStyle(22, Palette.Accent, bold = true))
                    Text("glisser pour déverrouiller", style = bodyStyle(13, Palette.Muted))
                }
                LockButton(Icons.camera, false) {
                    onUnlock { ctx.startActivity(ToolsActivity.intent(ctx, "camera")) }
                }
            }
        }
    }
}

@Composable
private fun LockButton(icon: LineIcon, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(58.dp)
            .clip(CircleShape)
            .background(if (on) Palette.Accent else Palette.Card)
            .border(1.dp, if (on) Palette.Accent else Palette.Line, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { LineIconView(icon, if (on) Color.White else Palette.Accent, 26.dp) }
}
