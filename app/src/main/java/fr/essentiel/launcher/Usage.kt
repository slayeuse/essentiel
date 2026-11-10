package fr.essentiel.launcher

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.provider.Settings
import java.util.Calendar

/** Calcul du temps passé dans chaque app aujourd'hui (depuis minuit). */
object Usage {

    fun hasPermission(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName
        )
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            ctx.checkSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) ==
                PackageManager.PERMISSION_GRANTED
        } else mode == AppOpsManager.MODE_ALLOWED
    }

    fun canDrawOverlays(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    fun startOfDay(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Millisecondes passées au premier plan aujourd'hui, par app. */
    fun todayUsageMs(ctx: Context): Map<String, Long> {
        if (!hasPermission(ctx)) return emptyMap()
        val usm = ctx.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
        val start = startOfDay()
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(start, now)
        val open = HashMap<String, Long>()
        val seen = HashSet<String>()
        val total = HashMap<String, Long>()
        val e = UsageEvents.Event()

        fun add(pkg: String, ms: Long) {
            if (ms > 0) total[pkg] = (total[pkg] ?: 0L) + ms
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName ?: continue
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if (!open.containsKey(pkg)) open[pkg] = e.timeStamp
                    seen.add(pkg)
                }
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val began = open.remove(pkg) ?: if (!seen.contains(pkg)) start else null
                    if (began != null) add(pkg, e.timeStamp - began)
                    seen.add(pkg)
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    open.forEach { (p, began) -> add(p, e.timeStamp - began) }
                    open.clear()
                }
            }
        }
        open.forEach { (p, began) -> add(p, now - began) }
        total.remove(ctx.packageName)
        return total
    }

    /** Met à jour l'app actuellement au premier plan à partir des événements récents. */
    fun updateForeground(ctx: Context, from: Long, to: Long, current: String?): String? {
        val usm = ctx.getSystemService(UsageStatsManager::class.java) ?: return current
        val events = usm.queryEvents(from, to)
        val e = UsageEvents.Event()
        var cur = current
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> cur = e.packageName
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> if (e.packageName == cur) cur = null
            }
        }
        return cur
    }

    fun formatMinutes(ms: Long): String {
        val min = (ms / 60_000L).coerceAtLeast(0)
        return if (min >= 60) "${min / 60} h ${"%02d".format(min % 60)}" else "$min min"
    }
}
