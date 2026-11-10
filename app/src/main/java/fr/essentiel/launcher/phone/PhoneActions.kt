package fr.essentiel.launcher.phone

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.provider.Telephony
import android.telecom.TelecomManager
import android.widget.Toast

data class CallEntry(
    val number: String,
    val name: String?,
    val type: Int,
    val date: Long,
    val duration: Long,
    val count: Int = 1,
)

object PhoneActions {

    /** Lance un appel (avec CALL_PHONE), sinon ouvre le clavier. */
    fun call(ctx: Context, number: String) {
        if (number.isBlank()) return
        val uri = Uri.fromParts("tel", number, null)
        if (ctx.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            try {
                ctx.getSystemService(TelecomManager::class.java).placeCall(uri, Bundle())
                return
            } catch (e: Exception) { }
        }
        try {
            ctx.startActivity(Intent(Intent.ACTION_CALL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(ctx, "Impossible de lancer l'appel", Toast.LENGTH_SHORT).show()
        }
    }

    fun isDefaultDialer(ctx: Context): Boolean =
        ctx.getSystemService(TelecomManager::class.java)?.defaultDialerPackage == ctx.packageName

    fun isDefaultSms(ctx: Context): Boolean =
        Telephony.Sms.getDefaultSmsPackage(ctx) == ctx.packageName

    fun roleIntent(ctx: Context, role: String): Intent? {
        val rm = ctx.getSystemService(RoleManager::class.java) ?: return null
        if (!rm.isRoleAvailable(role)) return null
        return rm.createRequestRoleIntent(role)
    }

    fun recents(ctx: Context, limit: Int = 300): List<CallEntry> {
        if (ctx.checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) return emptyList()
        val raw = mutableListOf<CallEntry>()
        try {
            ctx.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION),
                null, null, "${CallLog.Calls.DATE} DESC"
            )?.use { c ->
                while (c.moveToNext() && raw.size < limit) {
                    raw += CallEntry(
                        c.getString(0).orEmpty(), c.getString(1)?.takeIf { it.isNotBlank() },
                        c.getInt(2), c.getLong(3), c.getLong(4)
                    )
                }
            }
        } catch (e: Exception) { }
        // Regroupe les appels successifs du même numéro et du même type
        val grouped = mutableListOf<CallEntry>()
        raw.forEach { e ->
            val last = grouped.lastOrNull()
            if (last != null && last.number == e.number && last.type == e.type) {
                grouped[grouped.lastIndex] = last.copy(count = last.count + 1)
            } else grouped += e
        }
        return grouped
    }
}
