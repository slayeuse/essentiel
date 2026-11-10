package fr.essentiel.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager

/**
 * Surveille l'app au premier plan toutes les 2 secondes. Si elle a dépassé sa limite du jour,
 * on revient sur l'écran d'accueil avec le message « bloquée ».
 * Nécessite « Accès aux données d'utilisation » et « Afficher par-dessus les autres apps ».
 */
class LimitWatcherService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs
    private var lastQuery = 0L
    private var foreground: String? = null
    private var lastBlockAt = 0L

    private val tick = object : Runnable {
        override fun run() {
            try { check() } catch (e: Exception) { /* on réessaie au prochain passage */ }
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        startForeground(NOTIF_ID, buildNotification())
        lastQuery = System.currentTimeMillis() - 3_600_000L
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun check() {
        val limits = prefs.allLimits()
        if (limits.isEmpty() || !Usage.hasPermission(this)) { stopSelf(); return }
        val power = getSystemService(PowerManager::class.java)
        if (power != null && !power.isInteractive) return

        val now = System.currentTimeMillis()
        foreground = Usage.updateForeground(this, lastQuery, now, foreground)
        lastQuery = now

        val pkg = foreground ?: return
        if (pkg == packageName) return
        val limit = limits[pkg] ?: return
        if (now - lastBlockAt < 1500) return

        val used = Usage.todayUsageMs(this)[pkg] ?: 0L
        if (used >= limit * 60_000L) {
            lastBlockAt = now
            val home = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_BLOCKED, pkg)
            startActivity(home)
        }
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Limites de temps", NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
            }
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Limites de temps actives")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "limites"
        private const val NOTIF_ID = 1

        /** Démarre la surveillance si au moins une limite existe et que l'autorisation est donnée. */
        fun ensure(ctx: Context) {
            if (Prefs(ctx).allLimits().isNotEmpty() && Usage.hasPermission(ctx)) {
                try {
                    ctx.startForegroundService(Intent(ctx, LimitWatcherService::class.java))
                } catch (e: Exception) { }
            }
        }
    }
}
