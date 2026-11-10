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
import android.telecom.PhoneAccountHandle
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

    /**
     * Lance un appel.
     * - Si Essentiel est l'app Téléphone par défaut : on passe par Telecom (notre écran d'appel s'affiche),
     *   avec la SIM par défaut si elle est réglée (sinon notre écran propose de choisir la SIM).
     * - Sinon : on demande à Android d'appeler, et c'est l'écran d'appel du système qui s'affiche.
     */
    fun call(ctx: Context, rawNumber: String) {
        val number = rawNumber.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        if (number.isBlank()) {
            Toast.makeText(ctx, "Tape un numéro", Toast.LENGTH_SHORT).show(); return
        }
        if (ctx.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(ctx, "Autorise les appels dans les réglages d'Essentiel", Toast.LENGTH_LONG).show()
            ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val uri = Uri.fromParts("tel", number, null)
        try {
            if (isDefaultDialer(ctx)) {
                val tm = ctx.getSystemService(TelecomManager::class.java)
                val extras = Bundle()
                try {
                    tm.getDefaultOutgoingPhoneAccount("tel")?.let {
                        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, it)
                    }
                } catch (e: SecurityException) { }
                tm.placeCall(uri, extras)
                ctx.startActivity(Intent(ctx, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } else {
                ctx.startActivity(Intent(Intent.ACTION_CALL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        } catch (e: Exception) {
            Toast.makeText(ctx, "Impossible de lancer l'appel : ${e.message ?: "erreur"}", Toast.LENGTH_LONG).show()
        }
    }

    /** Les SIM disponibles pour appeler (nom affiché + identifiant). */
    fun simAccounts(ctx: Context): List<Pair<String, PhoneAccountHandle>> = try {
        val tm = ctx.getSystemService(TelecomManager::class.java)
        tm.callCapablePhoneAccounts.map { h -> (tm.getPhoneAccount(h)?.label?.toString() ?: "SIM") to h }
    } catch (e: SecurityException) {
        emptyList()
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
