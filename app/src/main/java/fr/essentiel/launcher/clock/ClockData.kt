package fr.essentiel.launcher.clock

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.mutableIntStateOf
import fr.essentiel.launcher.R
import fr.essentiel.launcher.ToolsActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar

const val SNOOZE_SUFFIX = " (répétition)"

/** days : jours de répétition, 1 = lundi … 7 = dimanche. Vide = une seule fois. */
data class Alarm(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val days: Set<Int>,
    val label: String,
    val enabled: Boolean,
)

object AlarmStore {
    val version = mutableIntStateOf(0)
    private val lock = Any()
    private fun file(ctx: Context) = File(ctx.filesDir, "alarmes.json")

    fun all(ctx: Context): List<Alarm> {
        synchronized(lock) {
            val f = file(ctx)
            if (!f.exists()) return emptyList()
            return try {
                val arr = JSONArray(f.readText())
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    val d = o.optJSONArray("days") ?: JSONArray()
                    Alarm(
                        o.getLong("id"), o.getInt("hour"), o.getInt("minute"),
                        (0 until d.length()).map { d.getInt(it) }.toSet(),
                        o.optString("label", ""), o.optBoolean("enabled", true)
                    )
                }.sortedWith(compareBy({ it.hour }, { it.minute }))
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    private fun write(ctx: Context, list: List<Alarm>) {
        synchronized(lock) {
            val arr = JSONArray()
            list.forEach { a ->
                arr.put(
                    JSONObject().put("id", a.id).put("hour", a.hour).put("minute", a.minute)
                        .put("days", JSONArray(a.days.toList())).put("label", a.label).put("enabled", a.enabled)
                )
            }
            file(ctx).writeText(arr.toString())
        }
        version.intValue = version.intValue + 1
    }

    fun save(ctx: Context, alarm: Alarm) {
        val list = all(ctx).filter { it.id != alarm.id } + alarm
        write(ctx, list)
        AlarmScheduler.cancel(ctx, alarm)
        if (alarm.enabled) AlarmScheduler.schedule(ctx, alarm)
    }

    fun delete(ctx: Context, id: Long) {
        all(ctx).firstOrNull { it.id == id }?.let { AlarmScheduler.cancel(ctx, it) }
        write(ctx, all(ctx).filter { it.id != id })
    }

    fun get(ctx: Context, id: Long) = all(ctx).firstOrNull { it.id == id }
}

object AlarmScheduler {
    /** Prochaine sonnerie de l'alarme (en ms), après maintenant. */
    fun nextTime(a: Alarm, from: Long = System.currentTimeMillis()): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = from
            set(Calendar.HOUR_OF_DAY, a.hour); set(Calendar.MINUTE, a.minute)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= from) c.add(Calendar.DAY_OF_YEAR, 1)
        if (a.days.isNotEmpty()) {
            repeat(7) {
                val dow = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1 // lundi = 1
                if (dow in a.days) return c.timeInMillis
                c.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return c.timeInMillis
    }

    private fun pending(ctx: Context, id: Long, kind: String): PendingIntent = PendingIntent.getBroadcast(
        ctx, (id % 1_000_000).toInt() + if (kind == KIND_TIMER) 7 else 0,
        Intent(ctx, AlarmReceiver::class.java).putExtra("id", id).putExtra("kind", kind),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun show(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 77, ToolsActivity.intent(ctx, "clock"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun schedule(ctx: Context, a: Alarm) {
        if (!a.enabled) return
        scheduleAt(ctx, nextTime(a), pending(ctx, a.id, KIND_ALARM))
    }

    fun scheduleAt(ctx: Context, at: Long, pi: PendingIntent) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show(ctx)), pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(ctx: Context, a: Alarm) {
        ctx.getSystemService(AlarmManager::class.java)?.cancel(pending(ctx, a.id, KIND_ALARM))
    }

    fun scheduleTimer(ctx: Context, at: Long) = scheduleAt(ctx, at, pending(ctx, 0, KIND_TIMER))
    fun cancelTimer(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java)?.cancel(pending(ctx, 0, KIND_TIMER))
    }

    fun rescheduleAll(ctx: Context) {
        AlarmStore.all(ctx).filter { it.enabled }.forEach { schedule(ctx, it) }
        val t = TimerState.load(ctx)
        if (t.running && t.endAt > System.currentTimeMillis()) scheduleTimer(ctx, t.endAt)
    }

    const val KIND_ALARM = "alarm"
    const val KIND_TIMER = "timer"
}

/** Minuteur : un seul à la fois, son état est gardé pour survivre à la fermeture de l'écran. */
data class TimerState(val running: Boolean, val endAt: Long, val remaining: Long, val total: Long) {
    companion object {
        val version = mutableIntStateOf(0)
        private fun sp(ctx: Context) = ctx.getSharedPreferences("horloge", Context.MODE_PRIVATE)

        fun load(ctx: Context): TimerState {
            val s = sp(ctx)
            return TimerState(
                s.getBoolean("t_running", false), s.getLong("t_end", 0), s.getLong("t_remaining", 0), s.getLong("t_total", 0)
            )
        }

        fun save(ctx: Context, t: TimerState) {
            sp(ctx).edit().putBoolean("t_running", t.running).putLong("t_end", t.endAt)
                .putLong("t_remaining", t.remaining).putLong("t_total", t.total).apply()
            version.intValue = version.intValue + 1
        }

        fun start(ctx: Context, durationMs: Long, total: Long = durationMs) {
            val end = System.currentTimeMillis() + durationMs
            save(ctx, TimerState(true, end, durationMs, total))
            AlarmScheduler.scheduleTimer(ctx, end)
        }

        fun pause(ctx: Context) {
            val t = load(ctx)
            if (!t.running) return
            AlarmScheduler.cancelTimer(ctx)
            save(ctx, t.copy(running = false, remaining = (t.endAt - System.currentTimeMillis()).coerceAtLeast(0)))
        }

        fun reset(ctx: Context) {
            AlarmScheduler.cancelTimer(ctx)
            save(ctx, TimerState(false, 0, 0, 0))
        }
    }
}

/** Chronomètre (gardé dans les préférences). */
object Stopwatch {
    val version = mutableIntStateOf(0)
    private fun sp(ctx: Context) = ctx.getSharedPreferences("horloge", Context.MODE_PRIVATE)

    fun running(ctx: Context) = sp(ctx).getBoolean("s_running", false)
    fun elapsed(ctx: Context): Long {
        val s = sp(ctx)
        val acc = s.getLong("s_acc", 0)
        return if (s.getBoolean("s_running", false)) acc + (System.currentTimeMillis() - s.getLong("s_start", 0)) else acc
    }
    fun laps(ctx: Context): List<Long> =
        sp(ctx).getString("s_laps", "").orEmpty().split(",").mapNotNull { it.toLongOrNull() }

    fun startPause(ctx: Context) {
        val s = sp(ctx)
        if (running(ctx)) s.edit().putLong("s_acc", elapsed(ctx)).putBoolean("s_running", false).apply()
        else s.edit().putLong("s_start", System.currentTimeMillis()).putBoolean("s_running", true).apply()
        version.intValue = version.intValue + 1
    }

    fun lap(ctx: Context) {
        sp(ctx).edit().putString("s_laps", (laps(ctx) + elapsed(ctx)).joinToString(",")).apply()
        version.intValue = version.intValue + 1
    }

    fun reset(ctx: Context) {
        sp(ctx).edit().putLong("s_acc", 0).putBoolean("s_running", false).putString("s_laps", "").apply()
        version.intValue = version.intValue + 1
    }
}

/** Déclenché par Android à l'heure d'une alarme ou à la fin du minuteur. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra("kind") ?: AlarmScheduler.KIND_ALARM
        val id = intent.getLongExtra("id", 0)
        var label = "Minuteur terminé"
        if (kind == AlarmScheduler.KIND_ALARM) {
            val a = AlarmStore.get(context, id) ?: return
            if (!a.enabled) return
            label = a.label.ifBlank { "Réveil" }
            // Une alarme ponctuelle se désactive ; une alarme répétée est reprogrammée.
            when {
                a.days.isNotEmpty() -> AlarmScheduler.schedule(context, a)
                a.label.endsWith(SNOOZE_SUFFIX) -> AlarmStore.delete(context, a.id)
                else -> AlarmStore.save(context, a.copy(enabled = false))
            }
            label = label.removeSuffix(SNOOZE_SUFFIX)
        } else {
            TimerState.save(context, TimerState(false, 0, 0, 0))
        }
        val svc = Intent(context, AlarmRingService::class.java)
            .putExtra("label", label).putExtra("kind", kind).putExtra("id", id)
        context.startForegroundService(svc)
    }
}

/** Joue la sonnerie et affiche l'écran de réveil. */
class AlarmRingService : Service() {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        val label = intent?.getStringExtra("label") ?: "Réveil"
        val kind = intent?.getStringExtra("kind") ?: AlarmScheduler.KIND_ALARM
        val id = intent?.getLongExtra("id", 0) ?: 0

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Réveils et minuteur", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null)
        })
        val ring = Intent(this, AlarmRingActivity::class.java)
            .putExtra("label", label).putExtra("kind", kind).putExtra("id", id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        val full = PendingIntent.getActivity(this, 90, ring, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(
            this, 91, Intent(this, AlarmRingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(label)
            .setContentText("Touche pour ouvrir")
            .setCategory(Notification.CATEGORY_ALARM)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Arrêter", stop).build())
            .build()
        startForeground(NOTIF_ID, n)
        try { startActivity(ring) } catch (e: Exception) { }

        if (player == null) {
            try {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                    )
                    setDataSource(this@AlarmRingService, uri)
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (e: Exception) { }
            vibrator = getSystemService(Vibrator::class.java)
            try { vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0)) } catch (e: Exception) { }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        try { player?.stop() } catch (e: Exception) { }
        player?.release(); player = null
        vibrator?.cancel()
        sendBroadcast(Intent(ACTION_RING_STOPPED).setPackage(packageName))
        super.onDestroy()
    }

    companion object {
        const val CHANNEL = "reveils"
        const val NOTIF_ID = 88
        const val ACTION_STOP = "fr.essentiel.ALARM_STOP"
        const val ACTION_RING_STOPPED = "fr.essentiel.ALARM_STOPPED"

        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, AlarmRingService::class.java)) }

        /** Répéter dans 10 minutes : une alarme ponctuelle temporaire. */
        fun snooze(ctx: Context, label: String) {
            stop(ctx)
            val at = System.currentTimeMillis() + 10 * 60_000L
            val c = Calendar.getInstance().apply { timeInMillis = at }
            AlarmStore.save(
                ctx,
                Alarm(
                    System.currentTimeMillis(), c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE),
                    emptySet(), label.removeSuffix(SNOOZE_SUFFIX) + SNOOZE_SUFFIX, true
                )
            )
        }
    }
}
