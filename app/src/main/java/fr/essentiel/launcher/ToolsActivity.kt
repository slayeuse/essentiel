package fr.essentiel.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import fr.essentiel.launcher.calendar.CalendarScreen
import fr.essentiel.launcher.calendar.EventEditScreen
import fr.essentiel.launcher.camera.CameraScreen
import fr.essentiel.launcher.clock.ClockScreen
import fr.essentiel.launcher.map.MapScreen
import fr.essentiel.launcher.memos.MemosScreen
import fr.essentiel.launcher.contacts.ContactDetailScreen
import fr.essentiel.launcher.contacts.ContactEditScreen
import fr.essentiel.launcher.contacts.ContactsScreen
import fr.essentiel.launcher.gallery.GalleryScreen
import fr.essentiel.launcher.gallery.ViewerScreen
import fr.essentiel.launcher.gallery.EditorScreen
import fr.essentiel.launcher.messages.ConversationScreen
import fr.essentiel.launcher.messages.MessagesScreen
import fr.essentiel.launcher.messages.NewMessageScreen
import fr.essentiel.launcher.phone.PhoneScreen
import fr.essentiel.launcher.reminders.RemindersScreen

/** Les écrans des apps intégrées. */
sealed class Route {
    data object Messages : Route()
    data object NewMessage : Route()
    data class Conversation(val threadId: Long, val address: String, val draft: String = "") : Route()
    data class Phone(val number: String = "") : Route()
    data object Contacts : Route()
    data class ContactDetail(val id: Long) : Route()
    data class ContactEdit(val id: Long?, val number: String = "") : Route()
    data object Camera : Route()
    data object Gallery : Route()
    data class Viewer(val index: Int) : Route()
    data class Editor(val index: Int) : Route()
    data object Calendar : Route()
    data class EventEdit(val eventId: Long?, val day: Long) : Route()
    data object Reminders : Route()
    data object Clock : Route()
    data object Memos : Route()
    data object Map : Route()
}

/** Les apps intégrées, avec leur identifiant (utilisé par les boutons et la liste « mes apps »). */
data class BuiltIn(val id: String, val label: String, val icon: LineIcon)

val BUILT_INS = listOf(
    BuiltIn("phone", "Téléphone", Icons.phone),
    BuiltIn("messages", "Messages", Icons.message),
    BuiltIn("contacts", "Contacts", Icons.users),
    BuiltIn("camera", "Photo", Icons.camera),
    BuiltIn("gallery", "Galerie", Icons.image),
    BuiltIn("calendar", "Agenda", Icons.calendar),
    BuiltIn("reminders", "Rappels", Icons.list),
    BuiltIn("clock", "Horloge", Icons.clock),
    BuiltIn("memos", "Mémos vocaux", Icons.mic),
    BuiltIn("map", "Carte", Icons.map),
)

class Nav(private val stack: SnapshotStateList<Route>, private val finish: () -> Unit) {
    fun go(r: Route) { stack.add(r) }
    fun replace(r: Route) { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex); stack.add(r) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) else finish() }
}

/** Une seule activité pour toutes les apps intégrées (messages, téléphone, photo…). */
class ToolsActivity : ComponentActivity() {

    private val stack = mutableStateListOf<Route>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Fonts.init(this)
        stack.add(routeFrom(intent))
        val nav = Nav(stack) { finish() }
        setContent {
            EssentielTheme { ToolsHost(stack, nav) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        stack.clear()
        stack.add(routeFrom(intent))
    }

    private fun routeFrom(i: Intent?): Route {
        val data: Uri? = i?.data
        val scheme = data?.scheme
        when (i?.action) {
            Intent.ACTION_DIAL, Intent.ACTION_CALL_BUTTON ->
                return Route.Phone(if (scheme == "tel") data?.schemeSpecificPart.orEmpty() else "")
            Intent.ACTION_VIEW -> if (scheme == "tel") return Route.Phone(data?.schemeSpecificPart.orEmpty())
        }
        if (scheme in listOf("sms", "smsto", "mms", "mmsto")) {
            val address = data?.schemeSpecificPart?.substringBefore("?").orEmpty()
            val body = i?.getStringExtra("sms_body") ?: i?.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            if (address.isBlank()) return Route.NewMessage
            return Route.Conversation(threadFor(this, address), address, body)
        }
        val threadId = i?.getLongExtra(EXTRA_THREAD, -1L) ?: -1L
        if (threadId >= 0) return Route.Conversation(threadId, i?.getStringExtra(EXTRA_ADDRESS).orEmpty())
        return when (i?.getStringExtra(EXTRA_ROUTE)) {
            "messages" -> Route.Messages
            "phone" -> Route.Phone()
            "contacts" -> Route.Contacts
            "camera" -> Route.Camera
            "gallery" -> Route.Gallery
            "calendar" -> Route.Calendar
            "event" -> Route.EventEdit((i?.getLongExtra(EXTRA_EVENT, -1L) ?: -1L).takeIf { it >= 0 }, System.currentTimeMillis())
            "reminders" -> Route.Reminders
            "clock" -> Route.Clock
            "memos" -> Route.Memos
            "map" -> Route.Map
            else -> Route.Phone()
        }
    }

    companion object {
        const val EXTRA_ROUTE = "route"
        const val EXTRA_THREAD = "thread_id"
        const val EXTRA_ADDRESS = "address"
        const val EXTRA_EVENT = "event_id"

        fun intent(ctx: Context, route: String): Intent =
            Intent(ctx, ToolsActivity::class.java)
                .putExtra(EXTRA_ROUTE, route)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        fun threadFor(ctx: Context, address: String): Long = try {
            Telephony.Threads.getOrCreateThreadId(ctx, address)
        } catch (e: Exception) {
            -1L
        }
    }
}

@Composable
fun ToolsHost(stack: SnapshotStateList<Route>, nav: Nav) {
    BackHandler { nav.back() }
    val r = stack.lastOrNull() ?: return
    key(stack.size, r) {
        when (r) {
            Route.Messages -> MessagesScreen(nav)
            Route.NewMessage -> NewMessageScreen(nav)
            is Route.Conversation -> ConversationScreen(nav, r.threadId, r.address, r.draft)
            is Route.Phone -> PhoneScreen(nav, r.number)
            Route.Contacts -> ContactsScreen(nav)
            is Route.ContactDetail -> ContactDetailScreen(nav, r.id)
            is Route.ContactEdit -> ContactEditScreen(nav, r.id, r.number)
            Route.Camera -> CameraScreen(nav)
            Route.Gallery -> GalleryScreen(nav)
            is Route.Viewer -> ViewerScreen(nav, r.index)
            is Route.Editor -> EditorScreen(nav, r.index)
            Route.Calendar -> CalendarScreen(nav)
            is Route.EventEdit -> EventEditScreen(nav, r.eventId, r.day)
            Route.Reminders -> RemindersScreen(nav)
            Route.Clock -> ClockScreen(nav)
            Route.Memos -> MemosScreen(nav)
            Route.Map -> MapScreen(nav)
        }
    }
}
