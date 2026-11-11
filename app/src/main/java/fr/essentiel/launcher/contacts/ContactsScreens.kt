package fr.essentiel.launcher.contacts

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.essentiel.launcher.Avatar
import fr.essentiel.launcher.CardGroup
import fr.essentiel.launcher.Divider
import fr.essentiel.launcher.EmptyText
import fr.essentiel.launcher.Fab
import fr.essentiel.launcher.Field
import fr.essentiel.launcher.IconAction
import fr.essentiel.launcher.Icons
import fr.essentiel.launcher.LineIcon
import fr.essentiel.launcher.LineIconView
import fr.essentiel.launcher.Nav
import fr.essentiel.launcher.Palette
import fr.essentiel.launcher.PermissionGate
import fr.essentiel.launcher.PillButton
import fr.essentiel.launcher.Route
import fr.essentiel.launcher.ToolScaffold
import fr.essentiel.launcher.ToolsActivity
import fr.essentiel.launcher.bodyStyle
import fr.essentiel.launcher.phone.PhoneActions
import fr.essentiel.launcher.serifStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer

private fun plain(s: String) =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

private val CONTACT_PERMS = arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)

@Composable
fun ContactsScreen(nav: Nav) {
    var count by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize()) {
        ToolScaffold("contacts", subtitle = if (count > 0) "$count contacts" else null) {
            PermissionGate(CONTACT_PERMS, "Essentiel a besoin d'accéder à tes contacts.") {
                ContactList(onCount = { count = it }, onOpen = { nav.go(Route.ContactDetail(it)) })
            }
        }
        Fab(Icons.plus, Modifier.align(Alignment.BottomEnd).padding(22.dp)) { nav.go(Route.ContactEdit(null)) }
    }
}

/** Barre de recherche ronde réutilisable. */
@Composable
fun SearchBar(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier.fillMaxWidth().clip(shape).background(Palette.Card).border(1.dp, Palette.Line, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineIconView(Icons.search, Palette.Muted, 18.dp)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = bodyStyle(16, Palette.Muted))
            BasicTextField(
                value, onChange, singleLine = true, textStyle = bodyStyle(16),
                cursorBrush = SolidColor(Palette.Accent), modifier = Modifier.fillMaxWidth()
            )
        }
        if (value.isNotEmpty()) {
            Box(Modifier.clip(CircleShape).clickable { onChange("") }.padding(2.dp)) { LineIconView(Icons.close, Palette.Muted, 16.dp) }
        }
    }
}

@Composable
private fun ContactList(onCount: (Int) -> Unit, onOpen: (Long) -> Unit) {
    val ctx = LocalContext.current
    var contacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var numbers by remember { mutableStateOf<Map<Long, List<String>>>(emptyMap()) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            contacts = ContactsRepo.all(ctx)
            numbers = ContactsRepo.allNumbers(ctx)
        }
        onCount(contacts.size)
    }
    Column {
        SearchBar(query, { query = it }, "rechercher un nom ou un numéro")
        val q = plain(query.trim())
        val digits = query.filter { it.isDigit() }
        val shown = if (q.isEmpty()) contacts else contacts.filter { c ->
            plain(c.name).contains(q) ||
                (digits.length >= 3 && numbers[c.id].orEmpty().any { it.filter(Char::isDigit).contains(digits) })
        }
        val starred = if (q.isEmpty()) contacts.filter { it.starred } else emptyList()
        val groups = remember(shown) { shown.groupBy { plain(it.name).firstOrNull()?.uppercaseChar()?.takeIf { ch -> ch.isLetter() } ?: '#' }.toList() }
        LazyColumn(Modifier.fillMaxSize()) {
            if (starred.isNotEmpty()) {
                item { Column {
                    Text("FAVORIS", style = bodyStyle(13, Palette.Muted, bold = true), modifier = Modifier.padding(top = 18.dp, bottom = 10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(end = 10.dp)) {
                        items(starred, key = { "s" + it.id }) { c ->
                            Column(
                                Modifier.width(66.dp).clip(RoundedCornerShape(16.dp)).clickable { onOpen(c.id) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Avatar(c.name, c.photo, 58.dp)
                                Spacer(Modifier.height(6.dp))
                                Text(c.name.substringBefore(" "), style = bodyStyle(13), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                } }
            }
            groups.forEach { (letter, list) ->
                item(key = "g$letter") { Column {
                    Text("$letter", style = serifStyle(22, Palette.Accent), modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
                    CardGroup {
                        list.forEachIndexed { i, c ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onOpen(c.id) }.padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Avatar(c.name, c.photo, 40.dp)
                                Spacer(Modifier.width(14.dp))
                                Text(c.name, style = bodyStyle(17), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                if (c.starred) LineIconView(Icons.star, Palette.Accent, 16.dp)
                            }
                            if (i < list.lastIndex) Divider()
                        }
                    }
                } }
            }
            if (shown.isEmpty()) item { EmptyText(if (q.isEmpty()) "Aucun contact." else "Aucun résultat.") }
            item { Spacer(Modifier.height(100.dp)) }
        }
    }
}

@Composable
fun ContactDetailScreen(nav: Nav, id: Long) {
    val ctx = LocalContext.current
    var contact by remember { mutableStateOf<ContactFull?>(null) }
    var version by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(version) { contact = withContext(Dispatchers.IO) { ContactsRepo.get(ctx, id) } }

    val c = contact
    ToolScaffold("", actions = {
        if (c != null) {
            IconAction(Icons.star, if (c.starred) Palette.Accent else Palette.Muted) {
                ContactsRepo.setStarred(ctx, id, !c.starred); version++
            }
            IconAction(Icons.edit, Palette.Ink) { nav.go(Route.ContactEdit(id)) }
        }
    }) {
        if (c == null) { EmptyText("chargement…"); return@ToolScaffold }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(c.name, c.photo, 110.dp)
                Spacer(Modifier.height(14.dp))
                Text(c.name, style = serifStyle(34), textAlign = TextAlign.Center)
                c.phones.firstOrNull()?.let { Text(it.number, style = bodyStyle(15, Palette.Muted)) }
            }
            Spacer(Modifier.height(20.dp))
            // Actions principales
            val first = c.phones.firstOrNull()?.number
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile(Icons.phone, "appeler", first != null, Modifier.weight(1f)) { first?.let { PhoneActions.call(ctx, it) } }
                ActionTile(Icons.message, "message", first != null, Modifier.weight(1f)) {
                    first?.let { nav.go(Route.Conversation(ToolsActivity.threadFor(ctx, it), it)) }
                }
                val mail = c.emails.firstOrNull()
                ActionTile(Icons.mail, "e-mail", mail != null, Modifier.weight(1f)) {
                    mail?.let { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$it")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }
            Spacer(Modifier.height(20.dp))
            if (c.phones.isNotEmpty()) {
                CardGroup {
                    c.phones.forEachIndexed { i, p ->
                        Row(
                            Modifier.fillMaxWidth().clickable { PhoneActions.call(ctx, p.number) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(p.label, style = bodyStyle(13, Palette.Muted))
                                Text(p.number, style = bodyStyle(18, Palette.Accent))
                            }
                            IconAction(Icons.message, Palette.Muted, 20.dp) {
                                nav.go(Route.Conversation(ToolsActivity.threadFor(ctx, p.number), p.number))
                            }
                        }
                        if (i < c.phones.lastIndex) Divider()
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            if (c.emails.isNotEmpty()) {
                CardGroup {
                    c.emails.forEachIndexed { i, e ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                            Text("e-mail", style = bodyStyle(13, Palette.Muted))
                            Text(e, style = bodyStyle(17))
                        }
                        if (i < c.emails.lastIndex) Divider()
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            CardGroup {
                Text(
                    "supprimer le contact", style = bodyStyle(16, Palette.Accent),
                    modifier = Modifier.fillMaxWidth().clickable { confirmDelete = true }.padding(vertical = 14.dp)
                )
            }
            Spacer(Modifier.height(30.dp))
        }
    }

    if (confirmDelete && c != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer ${c.name} ?") },
            text = { Text("Le contact sera supprimé de ton téléphone et de ton compte.") },
            confirmButton = {
                TextButton(onClick = { ContactsRepo.delete(ctx, id); confirmDelete = false; nav.back() }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
            containerColor = Palette.Bg,
        )
    }
}

@Composable
private fun ActionTile(icon: LineIcon, label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.clip(shape).background(if (enabled) Palette.Accent.copy(alpha = 0.10f) else Palette.Card)
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LineIconView(icon, if (enabled) Palette.Accent else Palette.Muted.copy(alpha = 0.5f), 24.dp)
        Spacer(Modifier.height(6.dp))
        Text(label, style = bodyStyle(13, if (enabled) Palette.Ink else Palette.Muted, bold = true))
    }
}

@Composable
fun ContactEditScreen(nav: Nav, id: Long?, presetNumber: String) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    val phones = remember { mutableStateListOf<String>() }
    var email by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(id == null) }

    LaunchedEffect(Unit) {
        if (id != null) {
            val c = withContext(Dispatchers.IO) { ContactsRepo.get(ctx, id) }
            if (c != null) {
                name = c.name
                phones.addAll(c.phones.map { it.number })
                email = c.emails.firstOrNull().orEmpty()
            }
            loaded = true
        }
        if (presetNumber.isNotBlank() && presetNumber !in phones) phones.add(presetNumber)
        if (phones.isEmpty()) phones.add("")
    }

    ToolScaffold(if (id == null) "nouveau contact" else "modifier") {
        PermissionGate(CONTACT_PERMS, "Essentiel a besoin d'accéder à tes contacts.") {
            if (!loaded) return@PermissionGate
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Avatar(name.ifBlank { "?" }, null, 96.dp) }
                Spacer(Modifier.height(4.dp))
                Field(name, { name = it }, "prénom et nom", Modifier.fillMaxWidth())
                phones.forEachIndexed { i, p ->
                    Field(
                        p, { phones[i] = it }, "numéro de téléphone", Modifier.fillMaxWidth(),
                        keyboard = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                }
                Text(
                    "+ ajouter un numéro", style = bodyStyle(15, Palette.Accent, bold = true),
                    modifier = Modifier.clickable { phones.add("") }.padding(vertical = 4.dp)
                )
                Field(
                    email, { email = it }, "e-mail (facultatif)", Modifier.fillMaxWidth(),
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                Spacer(Modifier.height(8.dp))
                PillButton("enregistrer", modifier = Modifier.fillMaxWidth()) {
                    if (name.isBlank()) {
                        Toast.makeText(ctx, "Indique un nom", Toast.LENGTH_SHORT).show(); return@PillButton
                    }
                    val newId = ContactsRepo.save(ctx, id, name.trim(), phones.toList(), email)
                    if (newId == null) {
                        Toast.makeText(ctx, "Impossible d'enregistrer le contact", Toast.LENGTH_SHORT).show()
                    } else if (id == null) {
                        nav.replace(Route.ContactDetail(newId))
                    } else {
                        nav.back()
                    }
                }
            }
        }
    }
}

@Suppress("unused")
private val keepColor = Color.Unspecified
