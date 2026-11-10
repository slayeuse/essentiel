package fr.essentiel.launcher.messages

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import fr.essentiel.launcher.contacts.ContactsRepo
import fr.essentiel.launcher.phone.PhoneActions

data class Conversation(
    val threadId: Long,
    val addresses: List<String>,
    val title: String,
    val snippet: String,
    val date: Long,
    val unread: Boolean,
)

data class Message(
    val id: Long,
    val body: String,
    val date: Long,
    val outgoing: Boolean,
    val failed: Boolean,
    val pending: Boolean,
    val images: List<Uri>,
)

/** Lecture des SMS / MMS du téléphone (base Android partagée par toutes les apps de messages). */
object SmsRepo {

    val SMS_URI: Uri = Uri.parse("content://sms")
    val MMS_URI: Uri = Uri.parse("content://mms")
    val ALL_URI: Uri = Uri.parse("content://mms-sms/")

    fun canRead(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    private fun canonicalAddresses(ctx: Context): Map<Long, String> {
        val map = HashMap<Long, String>()
        try {
            ctx.contentResolver.query(
                Uri.parse("content://mms-sms/canonical-addresses"), arrayOf("_id", "address"), null, null, null
            )?.use { c -> while (c.moveToNext()) map[c.getLong(0)] = c.getString(1).orEmpty() }
        } catch (e: Exception) { }
        return map
    }

    fun conversations(ctx: Context): List<Conversation> {
        if (!canRead(ctx)) return emptyList()
        val addresses = canonicalAddresses(ctx)
        val out = mutableListOf<Conversation>()
        try {
            ctx.contentResolver.query(
                Uri.parse("content://mms-sms/conversations?simple=true"),
                arrayOf("_id", "recipient_ids", "snippet", "date", "read", "message_count"),
                null, null, "date DESC"
            )?.use { c ->
                while (c.moveToNext()) {
                    if (c.getInt(5) == 0) continue
                    val ids = c.getString(1).orEmpty().split(" ").mapNotNull { it.trim().toLongOrNull() }
                    val addrs = ids.mapNotNull { addresses[it] }.filter { it.isNotBlank() }
                    val title = addrs.joinToString(", ") { ContactsRepo.nameFor(ctx, it) ?: it }.ifBlank { "Inconnu" }
                    out += Conversation(
                        threadId = c.getLong(0),
                        addresses = addrs,
                        title = title,
                        snippet = c.getString(2)?.takeIf { it.isNotBlank() } ?: "MMS",
                        date = c.getLong(3),
                        unread = c.getInt(4) == 0,
                    )
                }
            }
        } catch (e: Exception) { }
        return out
    }

    fun messages(ctx: Context, threadId: Long, limit: Int = 400): List<Message> {
        if (!canRead(ctx) || threadId < 0) return emptyList()
        val out = mutableListOf<Message>()
        try {
            ctx.contentResolver.query(
                SMS_URI, arrayOf("_id", "body", "date", "type"),
                "thread_id = ?", arrayOf(threadId.toString()), "date DESC LIMIT $limit"
            )?.use { c ->
                while (c.moveToNext()) {
                    val type = c.getInt(3)
                    out += Message(
                        id = c.getLong(0), body = c.getString(1).orEmpty(), date = c.getLong(2),
                        outgoing = type != 1, failed = type == 5, pending = type == 4 || type == 6,
                        images = emptyList()
                    )
                }
            }
        } catch (e: Exception) { }
        try {
            ctx.contentResolver.query(
                MMS_URI, arrayOf("_id", "date", "msg_box"),
                "thread_id = ?", arrayOf(threadId.toString()), "date DESC LIMIT 150"
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val (text, images) = mmsParts(ctx, id)
                    out += Message(
                        id = -id, body = text, date = c.getLong(1) * 1000L,
                        outgoing = c.getInt(2) != 1, failed = c.getInt(2) == 5, pending = c.getInt(2) == 4,
                        images = images
                    )
                }
            }
        } catch (e: Exception) { }
        return out.sortedBy { it.date }
    }

    private fun mmsParts(ctx: Context, mmsId: Long): Pair<String, List<Uri>> {
        val text = StringBuilder()
        val images = mutableListOf<Uri>()
        ctx.contentResolver.query(
            Uri.parse("content://mms/part"), arrayOf("_id", "ct", "text", "_data"),
            "mid = ?", arrayOf(mmsId.toString()), null
        )?.use { c ->
            while (c.moveToNext()) {
                val partId = c.getLong(0)
                val type = c.getString(1).orEmpty()
                val partUri = Uri.parse("content://mms/part/$partId")
                when {
                    type == "text/plain" -> {
                        val t = if (c.getString(3) != null) {
                            try {
                                ctx.contentResolver.openInputStream(partUri)?.bufferedReader()?.use { it.readText() }
                            } catch (e: Exception) { null }
                        } else c.getString(2)
                        if (!t.isNullOrBlank()) { if (text.isNotEmpty()) text.append("\n"); text.append(t) }
                    }
                    type.startsWith("image/") -> images += partUri
                }
            }
        }
        return text.toString() to images
    }

    /** Marque la conversation comme lue (possible seulement si Essentiel est l'app SMS par défaut). */
    fun markRead(ctx: Context, threadId: Long) {
        if (!PhoneActions.isDefaultSms(ctx) || threadId < 0) return
        val v = ContentValues().apply { put("read", 1); put("seen", 1) }
        try {
            ctx.contentResolver.update(SMS_URI, v, "thread_id = ? AND read = 0", arrayOf(threadId.toString()))
            ctx.contentResolver.update(MMS_URI, v, "thread_id = ? AND read = 0", arrayOf(threadId.toString()))
        } catch (e: Exception) { }
    }

    fun deleteThread(ctx: Context, threadId: Long): Boolean {
        if (!PhoneActions.isDefaultSms(ctx)) return false
        return try {
            ctx.contentResolver.delete(Uri.parse("content://mms-sms/conversations/$threadId"), null, null)
            true
        } catch (e: Exception) { false }
    }
}
