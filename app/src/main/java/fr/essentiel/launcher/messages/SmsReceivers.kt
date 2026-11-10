package fr.essentiel.launcher.messages

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.provider.Telephony
import android.telephony.SmsManager
import android.widget.Toast
import fr.essentiel.launcher.R
import fr.essentiel.launcher.ToolsActivity
import fr.essentiel.launcher.contacts.ContactsRepo
import fr.essentiel.launcher.phone.PhoneActions

/** Envoi des SMS. */
object SmsSender {

    fun send(ctx: Context, address: String, body: String): Boolean {
        if (address.isBlank() || body.isBlank()) return false
        return try {
            @Suppress("DEPRECATION")
            val sm = SmsManager.getDefault()
            val parts = sm.divideMessage(body)
            // Si Essentiel est l'app SMS par défaut, c'est à elle d'enregistrer le message envoyé.
            val uri: Uri? = if (PhoneActions.isDefaultSms(ctx)) {
                val v = ContentValues().apply {
                    put("address", address); put("body", body); put("date", System.currentTimeMillis())
                    put("read", 1); put("seen", 1); put("type", 4) // 4 = en cours d'envoi
                }
                ctx.contentResolver.insert(Uri.parse("content://sms/outbox"), v)
            } else null
            val base = (System.currentTimeMillis() and 0xFFFFFF).toInt()
            val sent = ArrayList<PendingIntent>()
            parts.forEachIndexed { i, _ ->
                val intent = Intent(ctx, SmsSentReceiver::class.java)
                    .putExtra("uri", uri?.toString())
                    .putExtra("last", i == parts.lastIndex)
                sent += PendingIntent.getBroadcast(
                    ctx, base + i, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }
            sm.sendMultipartTextMessage(address, null, parts, sent, null)
            true
        } catch (e: Exception) {
            Toast.makeText(ctx, "Échec de l'envoi du SMS", Toast.LENGTH_SHORT).show()
            false
        }
    }
}

/** Résultat d'envoi : met à jour le message (envoyé / échec). */
class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uri = intent.getStringExtra("uri")?.let(Uri::parse) ?: return
        val ok = resultCode == Activity.RESULT_OK
        if (!ok || intent.getBooleanExtra("last", true)) {
            val v = ContentValues().apply { put("type", if (ok) 2 else 5) }
            try { context.contentResolver.update(uri, v, null, null) } catch (e: Exception) { }
        }
        if (!ok) Toast.makeText(context, "SMS non envoyé", Toast.LENGTH_SHORT).show()
    }
}

/** Réception d'un SMS (seulement quand Essentiel est l'app SMS par défaut). */
class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (msgs.isEmpty()) return
        val address = msgs[0].displayOriginatingAddress ?: return
        val body = msgs.joinToString("") { it.displayMessageBody.orEmpty() }
        val v = ContentValues().apply {
            put("address", address); put("body", body)
            put("date", System.currentTimeMillis()); put("date_sent", msgs[0].timestampMillis)
            put("read", 0); put("seen", 0); put("type", 1)
        }
        try { context.contentResolver.insert(Uri.parse("content://sms/inbox"), v) } catch (e: Exception) { }
        MessageNotifier.show(context, address, body)
    }
}

/** Réception d'un MMS : le téléchargement des MMS n'est pas encore géré par Essentiel. */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        MessageNotifier.showText(
            context, 7001, "MMS reçu",
            "Essentiel ne sait pas encore télécharger les MMS. Remets Google Messages par défaut pour les recevoir."
        )
    }
}

/** Réponse rapide depuis la notification (et « répondre par SMS » à un appel refusé). */
class SmsReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val address = intent.getStringExtra("address") ?: return
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(MessageNotifier.KEY_REPLY)?.toString()
        if (!text.isNullOrBlank() && SmsSender.send(context, address, text)) {
            context.getSystemService(NotificationManager::class.java).cancel(MessageNotifier.idFor(address))
        }
    }
}

class SmsMarkReadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val address = intent.getStringExtra("address") ?: return
        SmsRepo.markRead(context, ToolsActivity.threadFor(context, address))
        context.getSystemService(NotificationManager::class.java).cancel(MessageNotifier.idFor(address))
    }
}

/** Service demandé par Android pour « Répondre par SMS » quand on refuse un appel. */
class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val address = intent?.data?.schemeSpecificPart?.substringBefore("?")
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (!address.isNullOrBlank() && !text.isNullOrBlank()) SmsSender.send(this, address, text)
        stopSelf(startId)
        return START_NOT_STICKY
    }
}

object MessageNotifier {
    const val CHANNEL = "messages"
    const val KEY_REPLY = "reply"

    fun idFor(address: String) = 1000 + (ContactsRepo.normalize(address).hashCode() and 0xFFFF)

    private fun channel(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Messages", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    fun show(ctx: Context, address: String, body: String) {
        channel(ctx)
        val who = ContactsRepo.nameFor(ctx, address) ?: address
        val thread = ToolsActivity.threadFor(ctx, address)
        val id = idFor(address)
        val open = PendingIntent.getActivity(
            ctx, id,
            Intent(ctx, ToolsActivity::class.java)
                .putExtra(ToolsActivity.EXTRA_THREAD, thread)
                .putExtra(ToolsActivity.EXTRA_ADDRESS, address)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        // La réponse rapide a besoin d'un PendingIntent modifiable (pour y mettre le texte tapé).
        val reply = PendingIntent.getBroadcast(
            ctx, id, Intent(ctx, SmsReplyReceiver::class.java).putExtra("address", address),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val read = PendingIntent.getBroadcast(
            ctx, id + 1, Intent(ctx, SmsMarkReadReceiver::class.java).putExtra("address", address),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val input = RemoteInput.Builder(KEY_REPLY).setLabel("Répondre").build()
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(who)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(Notification.Action.Builder(null, "Répondre", reply).addRemoteInput(input).build())
            .addAction(Notification.Action.Builder(null, "Marquer comme lu", read).build())
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(id, n)
    }

    fun showText(ctx: Context, id: Int, title: String, text: String) {
        channel(ctx)
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(id, n)
    }
}
