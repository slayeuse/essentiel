package fr.essentiel.launcher.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.util.Calendar
import java.util.TimeZone

data class CalEvent(
    val id: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val color: Int,
    val location: String = "",
    val recurring: Boolean = false,
)

object CalendarData {

    fun canWrite(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Convertit un événement « journée entière » (stocké en UTC) en heure locale. */
    private fun utcDayToLocal(utc: Long): Long {
        val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
        return Calendar.getInstance().apply {
            clear()
            set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }

    private fun localDayToUtc(local: Long): Long {
        val l = Calendar.getInstance().apply { timeInMillis = local }
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(l.get(Calendar.YEAR), l.get(Calendar.MONTH), l.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }

    /** Événements entre deux dates (heure locale). Les « journée entière » sont ramenés en heure locale. */
    fun between(ctx: Context, start: Long, end: Long): List<CalEvent> {
        if (ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return emptyList()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, start - 86_400_000L)
            ContentUris.appendId(it, end + 86_400_000L)
        }.build()
        val out = mutableListOf<CalEvent>()
        try {
            ctx.contentResolver.query(
                uri,
                arrayOf(
                    CalendarContract.Instances.EVENT_ID,
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END,
                    CalendarContract.Instances.ALL_DAY,
                    CalendarContract.Instances.DISPLAY_COLOR,
                    CalendarContract.Instances.EVENT_LOCATION,
                    CalendarContract.Instances.RRULE,
                ),
                "${CalendarContract.Instances.VISIBLE} = 1", null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val allDay = c.getInt(4) == 1
                    var b = c.getLong(2)
                    var e = c.getLong(3)
                    if (allDay) { b = utcDayToLocal(b); e = utcDayToLocal(e) }
                    if (e <= start || b >= end) continue
                    out += CalEvent(
                        id = c.getLong(0),
                        title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "(sans titre)",
                        begin = b, end = e, allDay = allDay, color = c.getInt(5),
                        location = c.getString(6).orEmpty(),
                        recurring = !c.getString(7).isNullOrBlank(),
                    )
                }
            }
        } catch (e: Exception) { }
        return out.sortedWith(compareBy({ !it.allDay }, { it.begin }))
    }

    fun get(ctx: Context, id: Long): CalEvent? {
        try {
            ctx.contentResolver.query(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id),
                arrayOf(
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.DTEND,
                    CalendarContract.Events.ALL_DAY,
                    CalendarContract.Events.DISPLAY_COLOR,
                    CalendarContract.Events.EVENT_LOCATION,
                    CalendarContract.Events.RRULE,
                ), null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val allDay = c.getInt(3) == 1
                    var b = c.getLong(1)
                    var e = if (c.isNull(2)) b + 3_600_000L else c.getLong(2)
                    if (allDay) { b = utcDayToLocal(b); e = utcDayToLocal(e) }
                    return CalEvent(
                        id, c.getString(0).orEmpty(), b, e, allDay, c.getInt(4),
                        c.getString(5).orEmpty(), !c.getString(6).isNullOrBlank()
                    )
                }
            }
        } catch (e: Exception) { }
        return null
    }

    data class Cal(val id: Long, val name: String, val account: String, val color: Int, val primary: Boolean)

    /** Les agendas dans lesquels on peut écrire (Google en premier). */
    fun writableCalendars(ctx: Context): List<Cal> {
        val out = mutableListOf<Pair<Cal, Int>>()
        try {
            ctx.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.ACCOUNT_NAME,
                    CalendarContract.Calendars.ACCOUNT_TYPE,
                    CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                    CalendarContract.Calendars.CALENDAR_COLOR,
                    CalendarContract.Calendars.OWNER_ACCOUNT,
                    CalendarContract.Calendars.VISIBLE,
                ),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val level = c.getInt(4)
                    if (level < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) continue
                    val accountName = c.getString(2).orEmpty()
                    val type = c.getString(3).orEmpty()
                    val owner = c.getString(6).orEmpty()
                    val primary = owner.isNotBlank() && owner == accountName
                    var score = 0
                    if (type == "com.google") score += 4
                    if (primary) score += 2
                    if (c.getInt(7) == 1) score += 1
                    out += Cal(c.getLong(0), c.getString(1) ?: accountName.ifBlank { "Agenda" }, accountName, c.getInt(5), primary) to score
                }
            }
        } catch (e: Exception) { }
        return out.sortedByDescending { it.second }.map { it.first }
    }

    /** Si aucun agenda n'existe sur le téléphone, on crée un agenda local « Essentiel ». */
    fun createLocalCalendar(ctx: Context): Long? = try {
        val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, "Essentiel")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()
        val v = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, "Essentiel")
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, "Essentiel")
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "Essentiel (sur le téléphone)")
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFFF2390C.toInt())
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, "Essentiel")
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
        }
        ctx.contentResolver.insert(uri, v)?.let { ContentUris.parseId(it) }
    } catch (e: Exception) { null }

    /** Dernière erreur d'enregistrement (affichée à l'utilisateur). */
    var lastError: String = ""
        private set

    /** Crée (id = null) ou modifie un événement. calendarId = agenda choisi (null = le meilleur). */
    fun save(
        ctx: Context, id: Long?, title: String, location: String, begin: Long, end: Long, allDay: Boolean,
        calendarId: Long? = null,
    ): Boolean {
        lastError = ""
        if (!canWrite(ctx)) { lastError = "Autorise l'accès à l'agenda (écriture)."; return false }
        val v = ContentValues().apply {
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.EVENT_LOCATION, location)
            put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
            if (allDay) {
                val b = localDayToUtc(begin)
                val e = maxOf(localDayToUtc(end), b + 86_400_000L)
                put(CalendarContract.Events.DTSTART, b)
                put(CalendarContract.Events.DTEND, e)
                put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
            } else {
                put(CalendarContract.Events.DTSTART, begin)
                put(CalendarContract.Events.DTEND, maxOf(end, begin + 15 * 60_000L))
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }
        }
        return try {
            if (id == null) {
                val cal = calendarId ?: writableCalendars(ctx).firstOrNull()?.id ?: createLocalCalendar(ctx)
                if (cal == null) { lastError = "Aucun agenda sur le téléphone et impossible d'en créer un."; return false }
                v.put(CalendarContract.Events.CALENDAR_ID, cal)
                val uri = ctx.contentResolver.insert(CalendarContract.Events.CONTENT_URI, v)
                if (uri == null) lastError = "Android a refusé l'ajout."
                uri != null
            } else {
                if (calendarId != null) v.put(CalendarContract.Events.CALENDAR_ID, calendarId)
                val n = ctx.contentResolver.update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), v, null, null)
                if (n == 0) lastError = "Android a refusé la modification."
                n > 0
            }
        } catch (e: Exception) {
            lastError = e.message ?: e.javaClass.simpleName
            false
        }
    }

    fun calendarOf(ctx: Context, eventId: Long): Long? = try {
        ctx.contentResolver.query(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
            arrayOf(CalendarContract.Events.CALENDAR_ID), null, null, null
        )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
    } catch (e: Exception) { null }

    fun delete(ctx: Context, id: Long): Boolean = try {
        ctx.contentResolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), null, null) > 0
    } catch (e: Exception) { false }
}
