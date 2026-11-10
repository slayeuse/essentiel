package fr.essentiel.launcher.phone

import android.Manifest
import android.app.role.RoleManager
import android.provider.CallLog
import android.text.format.DateUtils
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.essentiel.launcher.Divider
import fr.essentiel.launcher.EmptyText
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
import fr.essentiel.launcher.rememberContentVersion
import fr.essentiel.launcher.serifStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PhoneScreen(nav: Nav, initialNumber: String) {
    var tab by remember { mutableIntStateOf(if (initialNumber.isNotBlank()) 0 else 1) }
    ToolScaffold("téléphone", onBack = nav::back, actions = {
        IconAction(Icons.users) { nav.go(Route.Contacts) }
    }) {
        PermissionGate(
            arrayOf(
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_CONTACTS,
            ),
            "Pour passer des appels et voir l'historique, Essentiel a besoin de ces autorisations."
        ) {
            Column(Modifier.fillMaxSize()) {
                DefaultDialerHint()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    TabLabel("clavier", tab == 0) { tab = 0 }
                    TabLabel("récents", tab == 1) { tab = 1 }
                }
                Spacer(Modifier.height(8.dp))
                if (tab == 0) Keypad(nav, initialNumber) else Recents(nav)
            }
        }
    }
}

@Composable
private fun DefaultDialerHint() {
    val ctx = LocalContext.current
    var isDefault by remember { mutableStateOf(PhoneActions.isDefaultDialer(ctx)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = PhoneActions.isDefaultDialer(ctx)
    }
    if (!isDefault) {
        Text(
            "Définir Essentiel comme app Téléphone (écran d'appel)",
            style = bodyStyle(14, Palette.Accent, bold = true),
            modifier = Modifier
                .clickable { PhoneActions.roleIntent(ctx, RoleManager.ROLE_DIALER)?.let { launcher.launch(it) } }
                .padding(bottom = 12.dp)
        )
    }
}

@Composable
private fun TabLabel(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = bodyStyle(17, if (selected) Palette.Ink else Palette.Muted, bold = selected),
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 6.dp)
    )
}

private val KEYS = listOf(
    "1" to "", "2" to "abc", "3" to "def",
    "4" to "ghi", "5" to "jkl", "6" to "mno",
    "7" to "pqrs", "8" to "tuv", "9" to "wxyz",
    "*" to "", "0" to "+", "#" to "",
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Keypad(nav: Nav, initialNumber: String) {
    val ctx = LocalContext.current
    var number by remember { mutableStateOf(initialNumber) }
    var contacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var numbers by remember { mutableStateOf<Map<Long, List<String>>>(emptyMap()) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            contacts = ContactsRepo.all(ctx)
            numbers = ContactsRepo.allNumbers(ctx)
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Suggestions de contacts
        val digits = number.filter { it.isDigit() }
        val suggestions = if (digits.length < 2) emptyList() else contacts.mapNotNull { c ->
            numbers[c.id]?.firstOrNull { ContactsRepo.normalize(it).contains(digits) }?.let { c to it }
        }.take(2)
        Column(Modifier.fillMaxWidth().height(112.dp)) {
            suggestions.forEach { (c, n) ->
                ListRow(c.name, n) { number = n }
            }
        }

        Text(
            number.ifEmpty { " " }, style = serifStyle(36), maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
        )
        if (number.isNotEmpty() && ContactsRepo.nameFor(ctx, number) == null) {
            Text(
                "+ créer un contact", style = bodyStyle(14, Palette.Accent, bold = true),
                modifier = Modifier.clickable { nav.go(Route.ContactEdit(null, number)) }.padding(6.dp)
            )
        } else Spacer(Modifier.height(30.dp))
        Spacer(Modifier.weight(1f))

        KEYS.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { (k, letters) ->
                    Column(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1.9f)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Palette.Card)
                            .border(1.dp, Palette.Line, RoundedCornerShape(22.dp))
                            .combinedClickable(
                                onClick = { number += k },
                                onLongClick = { number += if (k == "0") "+" else k },
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(k, style = serifStyle(28))
                        if (letters.isNotEmpty()) Text(letters, style = bodyStyle(11, Palette.Muted))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Row(
            Modifier.fillMaxWidth().padding(bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Palette.Accent)
                    .clickable { PhoneActions.call(ctx, number) },
                contentAlignment = Alignment.Center,
            ) { LineIconView(Icons.phone, Color.White, 30.dp) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (number.isNotEmpty()) {
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .combinedClickable(onClick = { number = number.dropLast(1) }, onLongClick = { number = "" })
                            .padding(12.dp)
                    ) { LineIconView(Icons.backspace, Palette.Muted, 28.dp) }
                }
            }
        }
    }
}

@Composable
private fun Recents(nav: Nav) {
    val ctx = LocalContext.current
    val version = rememberContentVersion(CallLog.Calls.CONTENT_URI)
    var calls by remember { mutableStateOf<List<CallEntry>>(emptyList()) }
    LaunchedEffect(version) { calls = withContext(Dispatchers.IO) { PhoneActions.recents(ctx) } }

    if (calls.isEmpty()) { EmptyText("Aucun appel récent."); return }
    LazyColumn {
        items(calls) { call ->
            val missed = call.type == CallLog.Calls.MISSED_TYPE || call.type == CallLog.Calls.REJECTED_TYPE
            val icon = when (call.type) {
                CallLog.Calls.OUTGOING_TYPE -> Icons.outgoing
                CallLog.Calls.INCOMING_TYPE -> Icons.incoming
                else -> Icons.missed
            }
            val who = call.name ?: call.number.ifBlank { "numéro masqué" }
            val kind = when {
                call.type == CallLog.Calls.MISSED_TYPE -> "manqué"
                call.type == CallLog.Calls.REJECTED_TYPE -> "refusé"
                call.type == CallLog.Calls.OUTGOING_TYPE -> "sortant"
                else -> "entrant"
            }
            val whenText = DateUtils.getRelativeTimeSpanString(
                call.date, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE
            )
            ListRow(
                title = who + if (call.count > 1) " (${call.count})" else "",
                subtitle = "$kind · $whenText",
                titleColor = if (missed) Palette.Accent else Palette.Ink,
                leading = { LineIconView(icon, if (missed) Palette.Accent else Palette.Muted, 20.dp) },
                trailing = {
                    if (call.number.isNotBlank()) {
                        Row {
                            IconAction(Icons.message, Palette.Muted) {
                                nav.go(Route.Conversation(ToolsActivity.threadFor(ctx, call.number), call.number))
                            }
                            Spacer(Modifier.width(2.dp))
                        }
                    }
                },
            ) { PhoneActions.call(ctx, call.number) }
            Divider()
        }
    }
}
