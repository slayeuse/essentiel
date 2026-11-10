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
    val title = remember(address) { ContactsRepo.nameFor(ctx, address) ?: address.ifBlank { "conversation" } }

    ToolScaffold(title, onBack = nav::back, actions = {
        if (address.isNotBlank()) IconAction(Icons.phone) { PhoneActions.call(ctx, address) }
    }) {
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
            var text by remember { mutableStateOf(draft) }

            Column(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.weight(1f), reverseLayout = true) {
                    items(messages.asReversed(), key = { it.id }) { m -> Bubble(m) }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Field(
                        text, { text = it }, "message", Modifier.weight(1f), singleLine = false,
                        keyboard = KeyboardOptions(keyboardType = KeyboardType.Text)
                    )
                    Spacer(Modifier.size(8.dp))
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(if (text.isBlank()) Palette.Card else Palette.Accent)
                            .clickable(enabled = text.isNotBlank()) {
                                if (SmsSender.send(ctx, address, text.trim())) text = ""
                            },
                        contentAlignment = Alignment.Center,
                    ) { LineIconView(Icons.send, if (text.isBlank()) Palette.Muted else Color.White, 22.dp) }
                }
            }
        }
    }
}

@Composable
private fun Bubble(m: Message) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalAlignment = if (m.outgoing) Alignment.End else Alignment.Start,
    ) {
        Column(
            Modifier
                .widthIn(max = 290.dp)
                .clip(shape)
                .background(if (m.outgoing) Palette.Accent else Palette.Card)
                .border(1.dp, if (m.outgoing) Palette.Accent else Palette.Line, shape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            m.images.forEach { uri ->
                AsyncImage(
                    model = uri, contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier.widthIn(max = 260.dp).clip(RoundedCornerShape(12.dp))
                )
            }
            if (m.body.isNotBlank()) {
                Text(m.body, style = bodyStyle(16, if (m.outgoing) Color.White else Palette.Ink))
            }
        }
        val status = when {
            m.failed -> "échec de l'envoi"
            m.pending -> "envoi…"
            else -> shortDate(m.date)
        }
        Text(
            status, style = bodyStyle(11, if (m.failed) Palette.Accent else Palette.Muted),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
