package fr.essentiel.launcher.reminders

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.mutableIntStateOf
import fr.essentiel.launcher.R
import fr.essentiel.launcher.ToolsActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class Reminder(val id: Long, val text: String, val time: Long, val done: Boolean)

/** Les rappels sont stockés dans un petit fichier sur le téléphone. */
object ReminderStore {
    /** Change à chaque modification (pour rafraîchir l'accueil et la liste). */
    val version = mutableIntStateOf(0)
    private val lock = Any()

    private fun file(ctx: Context) = File(ctx.filesDir, "rappels.json")

    fun all(ctx: Context): List<Reminder> {
        synchronized(lock) {
            val f = file(ctx)
            if (!f.exists()) return emptyList()
            return try {
                val arr = JSONArray(f.readText())
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    Reminder(o.getLong("id"), o.getString("text"), o.getLong("time"), o.optBoolean("done", false))
                }.sortedBy { it.time }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    private fun write(ctx: Context, list: List<Reminder>) {
        synchronized(lock) {
            val arr = JSONArray()
            list.forEach {
                arr.put(JSONObject().put("id", it.id).put("text", it.text).put("time", it.time).put("done", it.done))
            }
            file(ctx).writeText(arr.toString())
        }
        version.intValue = version.intValue + 1
    }

    fun add(ctx: Context, text: String, time: Long): Reminder {
        val r = Reminder(System.currentTimeMillis(), text, time, false)
        write(ctx, all(ctx) + r)
        ReminderScheduler.schedule(ctx, r)
        return r
    }

    fun setDone(ctx: Context, id: Long, done: Boolean) {
        val list = all(ctx).map { if (it.id == id) it.copy(done = done) else it }
        write(ctx, list)
        list.firstOrNull { it.id == id }?.let {
            if (done) ReminderScheduler.cancel(ctx, it) else ReminderScheduler.schedule(ctx, it)
        }
    }

    fun snooze(ctx: Context, id: Long, minutes: Int) {
        val t = System.currentTimeMillis() + minutes * 60_000L
        val list = all(ctx).map { if (it.id == id) it.copy(time = t, done = false) else it }
        write(ctx, list)
        list.firstOrNull { it.id == id }?.let { ReminderScheduler.schedule(ctx, it) }
    }

    fun delete(ctx: Context, id: Long) {
        all(ctx).firstOrNull { it.id == id }?.let { ReminderScheduler.cancel(ctx, it) }
        write(ctx, all(ctx).filter { it.id != id })
    }

    fun clearDone(ctx: Context) {
        write(ctx, all(ctx).filter { !it.done })
    }

    fun label(time: Long): String {
        val today = Calendar.getInstance()
        val c = Calendar.getInstance().apply { timeInMillis = time }
        val hour = SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(time))
        fun sameDay(a: Calendar, b: Calendar) =
            a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        return when {
            sameDay(c, today) -> "aujourd'hui $hour"
            sameDay(c, tomorrow) -> "demain $hour"
            sameDay(c, yesterday) -> "hier $hour"
            else -> SimpleDateFormat("EEE d MMM", Locale.FRANCE).format(Date(time)) + " $hour"
        }
    }
}

object ReminderScheduler {
    private fun pending(ctx: Context, r: Reminder): PendingIntent = PendingIntent.getBroadcast(
        ctx, (r.id % Int.MAX_VALUE).toInt(),
        Intent(ctx, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_FIRE).putExtra("id", r.id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun schedule(ctx: Context, r: Reminder) {
        if (r.done) return
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val at = maxOf(r.time, System.currentTimeMillis() + 2000)
        val pi = pending(ctx, r)
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(ctx: Context, r: Reminder) {
        ctx.getSystemService(AlarmManager::class.java)?.cancel(pending(ctx, r))
        ctx.getSystemService(NotificationManager::class.java)?.cancel(notifId(r.id))
    }

    fun rescheduleAll(ctx: Context) {
        ReminderStore.all(ctx).filter { !it.done }.forEach { schedule(ctx, it) }
    }

    fun notifId(id: Long) = 5000 + (id % 100_000).toInt()
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("id", -1L)
        when (intent.action) {
            ACTION_FIRE -> show(context, id)
            ACTION_DONE -> {
                ReminderStore.setDone(context, id, true)
                context.getSystemService(NotificationManager::class.java).cancel(ReminderScheduler.notifId(id))
            }
            ACTION_SNOOZE -> {
                ReminderStore.snooze(context, id, 10)
                context.getSystemService(NotificationManager::class.java).cancel(ReminderScheduler.notifId(id))
            }
        }
    }

    private fun show(ctx: Context, id: Long) {
        val r = ReminderStore.all(ctx).firstOrNull { it.id == id } ?: return
        if (r.done) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Rappels", NotificationManager.IMPORTANCE_HIGH))
        fun action(a: String, code: Int) = PendingIntent.getBroadcast(
            ctx, ReminderScheduler.notifId(id) * 10 + code,
            Intent(ctx, ReminderReceiver::class.java).setAction(a).putExtra("id", id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val open = PendingIntent.getActivity(
            ctx, ReminderScheduler.notifId(id), ToolsActivity.intent(ctx, "reminders"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Rappel")
            .setContentText(r.text)
            .setStyle(Notification.BigTextStyle().bigText(r.text))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(Notification.Action.Builder(null, "Fait", action(ACTION_DONE, 1)).build())
            .addAction(Notification.Action.Builder(null, "Dans 10 min", action(ACTION_SNOOZE, 2)).build())
            .build()
        nm.notify(ReminderScheduler.notifId(id), n)
    }

    companion object {
        const val CHANNEL = "rappels"
        const val ACTION_FIRE = "fr.essentiel.REMINDER_FIRE"
        const val ACTION_DONE = "fr.essentiel.REMINDER_DONE"
        const val ACTION_SNOOZE = "fr.essentiel.REMINDER_SNOOZE"
    }
}

/** Après un redémarrage du téléphone, on reprogramme les rappels. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) ReminderScheduler.rescheduleAll(context)
    }
}
