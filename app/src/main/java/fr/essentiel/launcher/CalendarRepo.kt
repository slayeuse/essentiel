package fr.essentiel.launcher

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.util.Calendar
import java.util.TimeZone

/** Lit les événements du jour dans l'agenda du téléphone (Google Agenda, etc.). */
object CalendarRepo {

    data class Event(val id: Long, val title: String, val begin: Long, val allDay: Boolean)
    data class Agenda(val next: Event?, val remaining: Int)

    fun hasPermission(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun load(ctx: Context): Agenda? {
        if (!hasPermission(ctx)) return null
        val now = System.currentTimeMillis()
        val dayStart = Usage.startOfDay()
        val dayEnd = Calendar.getInstance().apply {
            timeInMillis = dayStart; add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis

        // Les événements « toute la journée » sont stockés en heure UTC.
        val local = Calendar.getInstance()
        val utcToday = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis

        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, dayStart - 86_400_000L)
            ContentUris.appendId(it, dayEnd + 86_400_000L)
        }.build()

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
        )

        val timed = mutableListOf<Event>()
        val allDay = mutableListOf<Event>()
        try {
            ctx.contentResolver.query(
                uri, projection, "${CalendarContract.Instances.VISIBLE} = 1", null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "(sans titre)"
                    val begin = c.getLong(2)
                    val end = c.getLong(3)
                    val isAllDay = c.getInt(4) == 1
                    if (isAllDay) {
                        if (begin <= utcToday && end > utcToday) allDay += Event(id, title, begin, true)
                    } else if (end > now && begin < dayEnd) {
                        timed += Event(id, title, begin, false)
                    }
                }
            }
        } catch (e: SecurityException) {
            return null
        }
        val next = timed.firstOrNull() ?: allDay.firstOrNull()
        return Agenda(next, timed.size + allDay.size)
    }
}
