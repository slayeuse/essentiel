package fr.essentiel.launcher.phone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.essentiel.launcher.R
import fr.essentiel.launcher.contacts.ContactsRepo

/** État partagé des appels en cours, lu par l'écran d'appel. */
object CallManager {
    val calls = mutableStateListOf<Call>()
    val tick = mutableIntStateOf(0)
    var audio by mutableStateOf<CallAudioState?>(null)
    var service: EssentielInCallService? = null

    fun number(call: Call): String = call.details?.handle?.schemeSpecificPart.orEmpty()

    @Suppress("DEPRECATION")
    fun state(call: Call): Int = call.state

    fun answer(call: Call) = call.answer(VideoProfile.STATE_AUDIO_ONLY)

    fun hangup(call: Call) {
        if (state(call) == Call.STATE_RINGING) call.reject(false, null) else call.disconnect()
    }

    fun setMuted(muted: Boolean) { service?.setMuted(muted) }

    @Suppress("DEPRECATION")
    fun setSpeaker(on: Boolean) {
        service?.setAudioRoute(if (on) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_WIRED_OR_EARPIECE)
    }

    fun ringing(): Call? = calls.firstOrNull { state(it) == Call.STATE_RINGING }
    fun main(): Call? =
        calls.firstOrNull { state(it) == Call.STATE_ACTIVE || state(it) == Call.STATE_DIALING || state(it) == Call.STATE_CONNECTING }
            ?: calls.firstOrNull { state(it) != Call.STATE_DISCONNECTED }
            ?: calls.firstOrNull()
}

/**
 * Le service qu'Android appelle quand Essentiel est l'app Téléphone par défaut :
 * il reçoit chaque appel (entrant ou sortant) et affiche notre écran d'appel.
 */
class EssentielInCallService : InCallService() {

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            CallManager.tick.intValue++
            updateNotification()
        }
        override fun onDetailsChanged(call: Call, details: Call.Details) {
            CallManager.tick.intValue++
        }
    }

    override fun onCreate() {
        super.onCreate()
        CallManager.service = this
    }

    override fun onDestroy() {
        if (CallManager.service === this) CallManager.service = null
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        call.registerCallback(callback)
        CallManager.calls.add(call)
        CallManager.tick.intValue++
        updateNotification()
        try {
            startActivity(Intent(this, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { }
    }

    override fun onCallRemoved(call: Call) {
        call.unregisterCallback(callback)
        CallManager.calls.remove(call)
        CallManager.tick.intValue++
        updateNotification()
    }

    @Deprecated("Remplacé par onCallEndpointChanged sur Android 14+")
    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        CallManager.audio = audioState
    }

    private fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_INCOMING, "Appels entrants", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null) // la sonnerie est jouée par Android
            }
        )
        nm.createNotificationChannel(NotificationChannel(CH_ONGOING, "Appel en cours", NotificationManager.IMPORTANCE_LOW))

        val open = PendingIntent.getActivity(
            this, 0, Intent(this, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val ringing = CallManager.ringing()
        val main = CallManager.main()
        if (main == null || CallManager.calls.isEmpty()) { nm.cancel(NOTIF_ID); return }

        val number = CallManager.number(ringing ?: main)
        val who = ContactsRepo.nameFor(this, number) ?: number.ifBlank { "Numéro masqué" }

        val builder = if (ringing != null) {
            Notification.Builder(this, CH_INCOMING)
                .setContentTitle("Appel entrant")
                .setContentText(who)
                .setCategory(Notification.CATEGORY_CALL)
                .setFullScreenIntent(open, true)
                .addAction(Notification.Action.Builder(null, "Refuser", actionIntent(ACTION_HANGUP)).build())
                .addAction(Notification.Action.Builder(null, "Répondre", actionIntent(ACTION_ANSWER)).build())
        } else {
            Notification.Builder(this, CH_ONGOING)
                .setContentTitle("Appel en cours")
                .setContentText(who)
                .setCategory(Notification.CATEGORY_CALL)
                .setUsesChronometer(true)
                .addAction(Notification.Action.Builder(null, "Raccrocher", actionIntent(ACTION_HANGUP)).build())
        }
        builder.setSmallIcon(R.drawable.ic_stat).setOngoing(true).setContentIntent(open)
        nm.notify(NOTIF_ID, builder.build())
    }

    private fun actionIntent(action: String): PendingIntent = PendingIntent.getBroadcast(
        this, action.hashCode(), Intent(this, CallActionReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    companion object {
        const val CH_INCOMING = "appels_entrants"
        const val CH_ONGOING = "appel_en_cours"
        const val NOTIF_ID = 42
        const val ACTION_ANSWER = "fr.essentiel.ANSWER"
        const val ACTION_HANGUP = "fr.essentiel.HANGUP"
    }
}

/** Boutons « Répondre / Refuser / Raccrocher » de la notification d'appel. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            EssentielInCallService.ACTION_ANSWER -> CallManager.ringing()?.let {
                CallManager.answer(it)
                context.startActivity(Intent(context, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            EssentielInCallService.ACTION_HANGUP -> (CallManager.ringing() ?: CallManager.main())?.let { CallManager.hangup(it) }
        }
    }
}
