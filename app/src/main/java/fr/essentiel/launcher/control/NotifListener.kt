package fr.essentiel.launcher.control

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.mutableStateListOf
import kotlin.math.abs

data class NItem(
    val key: String,
    val pkg: String,
    val title: String,
    val text: String,
    val time: Long,
    val intent: PendingIntent?,
    val clearable: Boolean,
)

data class NowPlaying(
    val pkg: String,
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val playing: Boolean,
)

/**
 * Lit les notifications pour les afficher dans le centre de contrôle Essentiel.
 * Bonus : comme Android garde ce service actif en permanence, il porte aussi la « poignée »
 * du centre de contrôle (coin en haut à droite), si elle est activée.
 */
class NotifListener : NotificationListenerService() {

    private var edge: View? = null

    override fun onListenerConnected() {
        instance = this
        refresh()
        updateEdge()
    }

    override fun onListenerDisconnected() {
        removeEdge()
        if (instance === this) instance = null
    }

    override fun onDestroy() {
        removeEdge()
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private fun refresh() {
        val list = try { activeNotifications?.toList().orEmpty() } catch (e: Exception) { emptyList() }
        val out = list.mapNotNull { sbn ->
            val n = sbn.notification
            if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return@mapNotNull null
            if (sbn.packageName == packageName && sbn.isOngoing) return@mapNotNull null
            // Les lecteurs de musique sont affichés dans la carte « En cours de lecture »
            if (n.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return@mapNotNull null
            val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val text = (n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: n.extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
            if (title.isBlank() && text.isBlank()) return@mapNotNull null
            NItem(sbn.key, sbn.packageName, title, text, sbn.postTime, n.contentIntent, sbn.isClearable)
        }.sortedByDescending { it.time }
        items.clear()
        items.addAll(out)
    }

    // ---------- Poignée du centre de contrôle ----------
    @SuppressLint("ClickableViewAccessibility")
    fun updateEdge() {
        val want = edgeEnabled(this) && Controls.canOverlay(this)
        if (!want) { removeEdge(); return }
        if (edge != null) return
        val wm = getSystemService(WindowManager::class.java) ?: return
        val dm = resources.displayMetrics
        val v = View(this)
        var startY = 0f
        var startX = 0f
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { startY = e.rawY; startX = e.rawX }
                MotionEvent.ACTION_UP -> {
                    val dy = e.rawY - startY
                    if (dy > 40 * dm.density && abs(e.rawX - startX) < dy) openControlCenter(this)
                }
            }
            true
        }
        val lp = WindowManager.LayoutParams(
            (dm.widthPixels * 0.30f).toInt(),
            (18 * dm.density).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        lp.gravity = Gravity.TOP or Gravity.END
        // Juste sous la barre d'état (Android ne laisse pas une app se placer au-dessus d'elle)
        val sb = resources.getIdentifier("status_bar_height", "dimen", "android")
        lp.y = if (sb > 0) resources.getDimensionPixelSize(sb) else (24 * dm.density).toInt()
        try { wm.addView(v, lp); edge = v } catch (e: Exception) { }
    }

    private fun removeEdge() {
        val v = edge ?: return
        try { getSystemService(WindowManager::class.java)?.removeView(v) } catch (e: Exception) { }
        edge = null
    }

    companion object {
        @Volatile var instance: NotifListener? = null
        val items = mutableStateListOf<NItem>()

        fun dismiss(key: String) { try { instance?.cancelNotification(key) } catch (e: Exception) { } }
        fun clearAll() { try { instance?.cancelAllNotifications() } catch (e: Exception) { } }

        fun open(ctx: Context, n: NItem) {
            try {
                if (n.intent != null) n.intent.send()
                else ctx.packageManager.getLaunchIntentForPackage(n.pkg)?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                if (n.clearable) dismiss(n.key)
            } catch (e: Exception) { }
        }

        fun edgeEnabled(ctx: Context) = ctx.getSharedPreferences("essentiel", Context.MODE_PRIVATE).getBoolean("edge_control", false)
        fun setEdgeEnabled(ctx: Context, on: Boolean) {
            ctx.getSharedPreferences("essentiel", Context.MODE_PRIVATE).edit().putBoolean("edge_control", on).apply()
            instance?.updateEdge()
        }

        fun openControlCenter(ctx: Context) {
            try {
                ctx.startActivity(
                    Intent(ctx, ControlActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                )
            } catch (e: Exception) { }
        }

        // ---------- Musique en cours ----------
        private fun controller(ctx: Context): MediaController? {
            if (instance == null && !Controls.hasNotifAccess(ctx)) return null
            return try {
                val msm = ctx.getSystemService(MediaSessionManager::class.java) ?: return null
                val list = msm.getActiveSessions(ComponentName(ctx, NotifListener::class.java))
                list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
            } catch (e: Exception) { null }
        }

        fun nowPlaying(ctx: Context): NowPlaying? {
            val c = controller(ctx) ?: return null
            val m = c.metadata ?: return null
            val title = m.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
            if (title.isBlank()) return null
            return NowPlaying(
                pkg = c.packageName,
                title = title,
                artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty(),
                art = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: m.getBitmap(MediaMetadata.METADATA_KEY_ART),
                playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
            )
        }

        fun playPause(ctx: Context) {
            val c = controller(ctx) ?: return
            if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
        }
        fun next(ctx: Context) { controller(ctx)?.transportControls?.skipToNext() }
        fun previous(ctx: Context) { controller(ctx)?.transportControls?.skipToPrevious() }
    }
}
