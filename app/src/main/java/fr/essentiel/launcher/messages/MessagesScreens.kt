package fr.essentiel.launcher.messages

import android.Manifest
import android.app.role.RoleManager
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.width
import fr.essentiel.launcher.paper
import coil.compose.AsyncImage
import fr.essentiel.launcher.Avatar
import fr.essentiel.launcher.Divider
import fr.essentiel.launcher.EmptyText
import fr.essentiel.launcher.Field
import fr.essentiel.launcher.IconAction
import fr.essentiel.launcher.Icons
import fr.essentiel.launcher.LineIconView
import fr.essentiel.launcher.ListRow
import fr.essentiel.launcher.Nav
import fr.essentiel.launcher.Palette
import fr.essentiel.launcher.PermissionGate
import fr.essentiel.launcher.Route
import fr.essentiel.launcher.ToolScaffold
import fr.essentiel.launcher.ToolsActivity
import fr.essentiel.launcher.bodyStyle
import fr.essentiel.launcher.contacts.Contact
import fr.essentiel.launcher.contacts.ContactsRepo
import fr.essentiel.launcher.phone.PhoneActions
import fr.essentiel.launcher.rememberContentVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SMS_PERMS = arrayOf(
    Manifest.permission.READ_SMS,
    Manifest.permission.SEND_SMS,
    Manifest.permission.RECEIVE_SMS,
    Manifest.permission.READ_CONTACTS,
)

private fun shortDate(ms: Long): String =
    if (DateUtils.isToday(ms)) SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(ms))
    else SimpleDateFormat("d MMM", Locale.FRANCE).format(Date(ms))

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessagesScreen(nav: Nav) {
    val ctx = LocalContext.current
    ToolScaffold("messages", onBack = nav::back, actions = {
        IconAction(Icons.plus) { nav.go(Route.NewMessage) }
    }) {
        PermissionGate(SMS_PERMS, "Pour lire et envoyer tes SMS, Essentiel a besoin de ces autorisations.") {
            DefaultSmsHint()
            val version = rememberContentVersion(SmsRepo.ALL_URI)
            var list by remember { mutableStateOf<List<Conversation>?>(null) }
            var toDelete by remember { mutableStateOf<Conversation?>(null) }
            LaunchedEffect(version) { list = withContext(Dispatchers.IO) { SmsRepo.conversations(ctx) } }
            val l = list
            when {
                l == null -> EmptyText("chargement…")
                l.isEmpty() -> EmptyText("Aucune conversation.")
                else -> LazyColumn {
                    items(l, key = { it.threadId }) { c ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { nav.go(Route.Conversation(c.threadId, c.addresses.firstOrNull().orEmpty())) },
                                    onLongClick = { toDelete = c },
                                )
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(c.title)
                            Spacer(Modifier.size(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.title, style = bodyStyle(17, bold = c.unread), maxLines = 1)
                                Text(
                                    c.snippet.replace("\n", " "),
                                    style = bodyStyle(14, if (c.unread) Palette.Ink else Palette.Muted), maxLines = 1
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(shortDate(c.date), style = bodyStyle(12, Palette.Muted))
                                if (c.unread) {
                                    Spacer(Modifier.height(4.dp))
                                    Box(Modifier.size(10.dp).clip(CircleShape).background(Palette.Accent))
                                }
                            }
                        }
                        Divider()
                    }
                }
            }
            toDelete?.let { c ->
                AlertDialog(
                    onDismissRequest = { toDelete = null },
                    title = { Text("Supprimer la conversation ?") },
                    text = { Text("Tous les messages avec ${c.title} seront supprimés.") },
                    confirmButton = {
                        TextButton(onClick = {
                            if (!SmsRepo.deleteThread(ctx, c.threadId)) {
                                Toast.makeText(ctx, "Possible seulement si Essentiel est l'app SMS par défaut", Toast.LENGTH_LONG).show()
                            }
                            toDelete = null
                        }) { Text("Supprimer") }
                    },
                    dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Annuler") } },
                    containerColor = Palette.Bg,
                )
            }
        }
    }
}

@Composable
private fun DefaultSmsHint() {
    val ctx = LocalContext.current
    var isDefault by remember { mutableStateOf(PhoneActions.isDefaultSms(ctx)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = PhoneActions.isDefaultSms(ctx)
    }
    if (!isDefault) {
        Text(
            "Les messages arrivent dans ton app SMS actuelle. Touche ici pour faire d'Essentiel l'app SMS par défaut " +
                "(attention : les MMS ne seront pas encore reçus).",
            style = bodyStyle(13, Palette.Muted),
            modifier = Modifier
                .clickable { PhoneActions.roleIntent(ctx, RoleManager.ROLE_SMS)?.let { launcher.launch(it) } }
                .padding(bottom = 10.dp)
        )
    }
}

@Composable
fun NewMessageScreen(nav: Nav) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var contacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var numbers by remember { mutableStateOf<Map<Long, List<String>>>(emptyMap()) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { contacts = ContactsRepo.all(ctx); numbers = ContactsRepo.allNumbers(ctx) }
    }
    fun open(number: String) = nav.replace(Route.Conversation(ToolsActivity.threadFor(ctx, number), number))

    ToolScaffold("nouveau message", onBack = nav::back) {
        PermissionGate(SMS_PERMS, "Pour envoyer des SMS, Essentiel a besoin de ces autorisations.") {
            Field(query, { query = it }, "nom ou numéro", Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            val q = query.trim().lowercase()
            val typedNumber = query.filter { it.isDigit() || it == '+' }
            LazyColumn {
                if (typedNumber.length >= 3 && typedNumber.length == query.trim().replace(" ", "").length) {
                    item { ListRow("envoyer à $typedNumber", titleColor = Palette.Accent) { open(typedNumber) } }
                }
                val matches = contacts.filter { c -> numbers.containsKey(c.id) && (q.isEmpty() || c.name.lowercase().contains(q)) }
                matches.forEach { c ->
                    numbers[c.id].orEmpty().distinctBy { ContactsRepo.normalize(it) }.forEach { n ->
                        item { ListRow(c.name, n, leading = { Avatar(c.name, c.photo, 38.dp) }) { open(n) } }
                    }
                }
            }
        }
    }
}

@Composable
fun ConversationScreen(nav: Nav, threadIdArg: Long, addressArg: String, draft: String) {
    val ctx = LocalContext.current
    var threadId by remember { mutableStateOf(threadIdArg) }
    var address by remember { mutableStateOf(addressArg) }
    val name = remember(address) { ContactsRepo.nameFor(ctx, address) }
    val title = name ?: address.ifBlank { "conversation" }

    Column(Modifier.fillMaxSize().paper()) {
        // ----- En-tête -----
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(Icons.back, Palette.Ink, 26.dp) { nav.back() }
            Spacer(Modifier.width(4.dp))
            Avatar(title, null, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = bodyStyle(18, bold = true), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (name != null) Text(address, style = bodyStyle(13, Palette.Muted), maxLines = 1)
            }
            if (address.isNotBlank()) IconAction(Icons.phone) { PhoneActions.call(ctx, address) }
        }
        Divider()

        Box(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            PermissionGate(SMS_PERMS, "Pour lire et envoyer tes SMS, Essentiel a besoin de ces autorisations.") {
                val version = rememberContentVersion(SmsRepo.ALL_URI)
                var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
                LaunchedEffect(version, threadId) {
                    if (threadId < 0 && address.isNotBlank()) threadId = ToolsActivity.threadFor(ctx, address)
                    if (address.isBlank() && threadId >= 0) {
                        address = withContext(Dispatchers.IO) {
                            SmsRepo.conversations(ctx).firstOrNull { it.threadId == threadId }?.addresses?.firstOrNull().orEmpty()
                        }
                    }
                    messages = withContext(Dispatchers.IO) { SmsRepo.messages(ctx, threadId) }
                    withContext(Dispatchers.IO) { SmsRepo.markRead(ctx, threadId) }
                    ctx.getSystemService(android.app.NotificationManager::class.java)?.cancel(MessageNotifier.idFor(address))
                }
                if (messages.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Pas encore de message.\nÉcris le premier ci-dessous.", style = bodyStyle(15, Palette.Muted), textAlign = TextAlign.Center)
                    }
                } else {
                    val reversed = messages.asReversed()
                    LazyColumn(Modifier.fillMaxSize(), reverseLayout = true) {
                        items(reversed.size, key = { reversed[it].id }) { i ->
                            val m = reversed[i]
                            val older = reversed.getOrNull(i + 1)   // message juste au-dessus
                            val newer = reversed.getOrNull(i - 1)   // message juste en dessous
                            val sameDayAsOlder = older != null && sameDay(older.date, m.date)
                            val groupedAbove = older != null && older.outgoing == m.outgoing && sameDayAsOlder && m.date - older.date < 5 * 60_000
                            val groupedBelow = newer != null && newer.outgoing == m.outgoing && sameDay(newer.date, m.date) && newer.date - m.date < 5 * 60_000
                            Column {
                                if (!sameDayAsOlder) DaySeparator(m.date)
                                Bubble(m, groupedAbove, groupedBelow)
                            }
                        }
                    }
                }
            }
        }

        // ----- Barre de saisie -----
        var text by remember { mutableStateOf(draft) }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            val shape = RoundedCornerShape(26.dp)
            Box(
                Modifier.weight(1f).clip(shape).background(Palette.Card).border(1.dp, Palette.Line, shape)
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                if (text.isEmpty()) Text("SMS", style = bodyStyle(17, Palette.Muted))
                androidx.compose.foundation.text.BasicTextField(
                    value = text, onValueChange = { text = it }, maxLines = 6,
                    textStyle = bodyStyle(17), cursorBrush = androidx.compose.ui.graphics.SolidColor(Palette.Accent),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.size(8.dp))
            val canSend = text.isNotBlank() && address.isNotBlank()
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(if (canSend) Palette.Accent else Palette.Card)
                    .border(1.dp, if (canSend) Palette.Accent else Palette.Line, CircleShape)
                    .clickable(enabled = canSend) {
                        if (SmsSender.send(ctx, address, text.trim())) text = ""
                    },
                contentAlignment = Alignment.Center,
            ) { LineIconView(Icons.send, if (canSend) Color.White else Palette.Muted, 22.dp) }
        }
    }
}

private fun sameDay(a: Long, b: Long): Boolean {
    val ca = java.util.Calendar.getInstance().apply { timeInMillis = a }
    val cb = java.util.Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(java.util.Calendar.YEAR) == cb.get(java.util.Calendar.YEAR) &&
        ca.get(java.util.Calendar.DAY_OF_YEAR) == cb.get(java.util.Calendar.DAY_OF_YEAR)
}

@Composable
private fun DaySeparator(date: Long) {
    val label = when {
        DateUtils.isToday(date) -> "aujourd'hui"
        DateUtils.isToday(date + 86_400_000L) -> "hier"
        else -> SimpleDateFormat("EEEE d MMMM", Locale.FRANCE).format(Date(date))
    }
    Text(
        label, style = bodyStyle(12, Palette.Muted, bold = true), textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp)
    )
}

@Composable
private fun Bubble(m: Message, groupedAbove: Boolean, groupedBelow: Boolean) {
    val big = 22.dp
    val small = 6.dp
    // Coins resserrés du côté de l'expéditeur quand plusieurs messages se suivent
    val shape = if (m.outgoing) RoundedCornerShape(
        topStart = big, topEnd = if (groupedAbove) small else big,
        bottomStart = big, bottomEnd = if (groupedBelow) small else big
    ) else RoundedCornerShape(
        topStart = if (groupedAbove) small else big, topEnd = big,
        bottomStart = if (groupedBelow) small else big, bottomEnd = big
    )
    val fg = if (m.outgoing) Color.White else Palette.Ink
    Column(
        Modifier.fillMaxWidth().padding(top = if (groupedAbove) 2.dp else 8.dp),
        horizontalAlignment = if (m.outgoing) Alignment.End else Alignment.Start,
    ) {
        Column(
            Modifier
                .widthIn(max = 280.dp)
                .clip(shape)
                .background(if (m.outgoing) Palette.Accent else Color(0xFFF4F2EE))
                .border(1.dp, if (m.outgoing) Palette.Accent else Palette.Line, shape)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            m.images.forEach { uri ->
                AsyncImage(
                    model = uri, contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier.widthIn(max = 250.dp).clip(RoundedCornerShape(14.dp))
                )
            }
            if (m.body.isNotBlank()) Text(m.body, style = bodyStyle(16, fg).copy(lineHeight = 22.sp))
            if (!groupedBelow || m.failed || m.pending) {
                Text(
                    when {
                        m.failed -> "échec de l'envoi"
                        m.pending -> "envoi…"
                        else -> SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(m.date))
                    },
                    style = bodyStyle(11, if (m.outgoing) Color.White.copy(alpha = 0.8f) else Palette.Muted),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}
