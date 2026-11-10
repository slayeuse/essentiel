package fr.essentiel.launcher.contacts

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import java.util.concurrent.ConcurrentHashMap

data class Contact(val id: Long, val name: String, val photo: Uri?, val starred: Boolean)
data class PhoneNumber(val number: String, val label: String)
data class ContactFull(
    val id: Long,
    val name: String,
    val photo: Uri?,
    val starred: Boolean,
    val phones: List<PhoneNumber>,
    val emails: List<String>,
)

object ContactsRepo {

    fun canRead(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun all(ctx: Context): List<Contact> {
        if (!canRead(ctx)) return emptyList()
        val out = mutableListOf<Contact>()
        ctx.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
                ContactsContract.Contacts.STARRED,
            ),
            null, null,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                out += Contact(c.getLong(0), name, c.getString(2)?.let(Uri::parse), c.getInt(3) == 1)
            }
        }
        return out
    }

    /** Tous les numéros : id du contact → numéros (pour la recherche et le clavier). */
    fun allNumbers(ctx: Context): Map<Long, List<String>> {
        if (!canRead(ctx)) return emptyMap()
        val map = HashMap<Long, MutableList<String>>()
        ctx.contentResolver.query(
            Phone.CONTENT_URI, arrayOf(Phone.CONTACT_ID, Phone.NUMBER), null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val n = c.getString(1) ?: continue
                map.getOrPut(c.getLong(0)) { mutableListOf() }.add(n)
            }
        }
        return map
    }

    fun get(ctx: Context, id: Long): ContactFull? {
        if (!canRead(ctx)) return null
        var base: Contact? = null
        ctx.contentResolver.query(
            ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id),
            arrayOf(
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_URI,
                ContactsContract.Contacts.STARRED,
            ), null, null, null
        )?.use { c ->
            if (c.moveToFirst()) {
                base = Contact(id, c.getString(0) ?: "", c.getString(1)?.let(Uri::parse), c.getInt(2) == 1)
            }
        }
        val b = base ?: return null
        val phones = mutableListOf<PhoneNumber>()
        ctx.contentResolver.query(
            Phone.CONTENT_URI, arrayOf(Phone.NUMBER, Phone.TYPE, Phone.LABEL),
            "${Phone.CONTACT_ID} = ?", arrayOf(id.toString()), null
        )?.use { c ->
            while (c.moveToNext()) {
                val n = c.getString(0) ?: continue
                val label = Phone.getTypeLabel(ctx.resources, c.getInt(1), c.getString(2)).toString()
                if (phones.none { normalize(it.number) == normalize(n) }) phones += PhoneNumber(n, label.lowercase())
            }
        }
        val emails = mutableListOf<String>()
        ctx.contentResolver.query(
            Email.CONTENT_URI, arrayOf(Email.ADDRESS),
            "${Email.CONTACT_ID} = ?", arrayOf(id.toString()), null
        )?.use { c -> while (c.moveToNext()) c.getString(0)?.let { if (it !in emails) emails += it } }
        return ContactFull(id, b.name, b.photo, b.starred, phones, emails)
    }

    private val nameCache = ConcurrentHashMap<String, String>()

    /** Nom du contact pour un numéro, ou null. */
    fun nameFor(ctx: Context, number: String): String? {
        if (number.isBlank() || !canRead(ctx)) return null
        nameCache[number]?.let { return it.ifEmpty { null } }
        var name: String? = null
        try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) name = c.getString(0) }
        } catch (e: Exception) { }
        nameCache[number] = name ?: ""
        return name
    }

    /** Id du contact pour un numéro, ou null. */
    fun idFor(ctx: Context, number: String): Long? {
        if (number.isBlank() || !canRead(ctx)) return null
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
        } catch (e: Exception) { null }
    }

    fun clearCache() = nameCache.clear()

    fun normalize(n: String) = n.filter { it.isDigit() || it == '+' }.let {
        if (it.startsWith("+33")) "0" + it.drop(3) else it
    }

    fun setStarred(ctx: Context, id: Long, starred: Boolean) {
        val v = ContentValues().apply { put(ContactsContract.Contacts.STARRED, if (starred) 1 else 0) }
        ctx.contentResolver.update(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id), v, null, null)
    }

    fun delete(ctx: Context, id: Long) {
        ctx.contentResolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id), null, null)
        clearCache()
    }

    /** Le compte Google déjà utilisé pour tes contacts (pour que les nouveaux soient synchronisés). */
    private fun googleAccount(ctx: Context): Pair<String, String>? {
        ctx.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts.ACCOUNT_NAME, ContactsContract.RawContacts.ACCOUNT_TYPE),
            "${ContactsContract.RawContacts.ACCOUNT_TYPE} = ?", arrayOf("com.google"), null
        )?.use { c -> if (c.moveToFirst()) return (c.getString(0) ?: return null) to (c.getString(1) ?: return null) }
        return null
    }

    private fun rawContactId(ctx: Context, contactId: Long): Long? {
        ctx.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI, arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.CONTACT_ID} = ?", arrayOf(contactId.toString()), null
        )?.use { c -> if (c.moveToFirst()) return c.getLong(0) }
        return null
    }

    /** Crée (id = null) ou modifie un contact. Retourne l'id du contact, ou null en cas d'échec. */
    fun save(ctx: Context, id: Long?, name: String, phones: List<String>, email: String): Long? {
        val ops = ArrayList<ContentProviderOperation>()
        val cleanPhones = phones.map { it.trim() }.filter { it.isNotEmpty() }
        try {
            if (id == null) {
                val account = googleAccount(ctx)
                ops += ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, account?.second)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, account?.first)
                    .build()
                ops += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(StructuredName.DISPLAY_NAME, name)
                    .build()
                cleanPhones.forEach { p ->
                    ops += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                        .withValue(Phone.NUMBER, p)
                        .withValue(Phone.TYPE, Phone.TYPE_MOBILE)
                        .build()
                }
                if (email.isNotBlank()) {
                    ops += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, Email.CONTENT_ITEM_TYPE)
                        .withValue(Email.ADDRESS, email.trim())
                        .withValue(Email.TYPE, Email.TYPE_HOME)
                        .build()
                }
                val res = ctx.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                val rawId = res.firstOrNull()?.uri?.let { ContentUris.parseId(it) } ?: return null
                clearCache()
                ctx.contentResolver.query(
                    ContactsContract.RawContacts.CONTENT_URI, arrayOf(ContactsContract.RawContacts.CONTACT_ID),
                    "${ContactsContract.RawContacts._ID} = ?", arrayOf(rawId.toString()), null
                )?.use { c -> if (c.moveToFirst()) return c.getLong(0) }
                return null
            } else {
                val raw = rawContactId(ctx, id) ?: return null
                val types = arrayOf(StructuredName.CONTENT_ITEM_TYPE, Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE)
                types.forEach { t ->
                    ops += ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                        .withSelection(
                            "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                            arrayOf(id.toString(), t)
                        ).build()
                }
                ops += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, raw)
                    .withValue(ContactsContract.Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(StructuredName.DISPLAY_NAME, name)
                    .build()
                cleanPhones.forEach { p ->
                    ops += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValue(ContactsContract.Data.RAW_CONTACT_ID, raw)
                        .withValue(ContactsContract.Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                        .withValue(Phone.NUMBER, p)
                        .withValue(Phone.TYPE, Phone.TYPE_MOBILE)
                        .build()
                }
                if (email.isNotBlank()) {
                    ops += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValue(ContactsContract.Data.RAW_CONTACT_ID, raw)
                        .withValue(ContactsContract.Data.MIMETYPE, Email.CONTENT_ITEM_TYPE)
                        .withValue(Email.ADDRESS, email.trim())
                        .withValue(Email.TYPE, Email.TYPE_HOME)
                        .build()
                }
                ctx.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                clearCache()
                return id
            }
        } catch (e: Exception) {
            return null
        }
    }
}
