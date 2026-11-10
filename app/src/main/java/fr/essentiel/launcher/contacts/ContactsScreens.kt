package fr.essentiel.launcher.contacts

import android.Manifest
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.essentiel.launcher.Avatar
import fr.essentiel.launcher.Divider
import fr.essentiel.launcher.EmptyText
import fr.essentiel.launcher.Field
import fr.essentiel.launcher.IconAction
import fr.essentiel.launcher.Icons
import fr.essentiel.launcher.ListRow
import fr.essentiel.launcher.Nav
import fr.essentiel.launcher.Palette
import fr.essentiel.launcher.PermissionGate
import fr.essentiel.launcher.PillButton
import fr.essentiel.launcher.Route
import fr.essentiel.launcher.SectionTitle
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

@Composable
fun ContactsScreen(nav: Nav) {
    ToolScaffold("contacts", onBack = nav::back, actions = {
        IconAction(Icons.plus) { nav.go(Route.ContactEdit(null)) }
    }) {
        PermissionGate(
            arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS),
            "Essentiel a besoin d'accéder à tes contacts."
        ) {
            ContactList(onOpen = { nav.go(Route.ContactDetail(it)) })
        }
    }
}

@Composable
private fun ContactList(onOpen: (Long) -> Unit) {
    val ctx = LocalContext.current
    var contacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var numbers by remember { mutableStateOf<Map<Long, List<String>>>(emptyMap()) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            contacts = ContactsRepo.all(ctx)
            numbers = ContactsRepo.allNumbers(ctx)
        }
    }
    Column {
        Field(query, { query = it }, "rechercher", Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        val q = plain(query.trim())
        val digits = query.filter { it.isDigit() }
        val shown = if (q.isEmpty()) contacts else contacts.filter { c ->
            plain(c.name).contains(q) ||
                (digits.length >= 3 && numbers[c.id].orEmpty().any { it.filter(Char::isDigit).contains(digits) })
        }
        val starred = if (q.isEmpty()) contacts.filter { it.starred } else emptyList()
        LazyColumn {
            if (starred.isNotEmpty()) {
                item { SectionTitle("favoris") }
                items(starred, key = { "s" + it.id }) { c ->
                    ListRow(c.name, leading = { Avatar(c.name, c.photo) }) { onOpen(c.id) }
                }
                item { SectionTitle("tous") }
            }
            var lastLetter = ""
            shown.forEachIndexed { index, c ->
                val letter = plain(c.name).firstOrNull()?.uppercase() ?: "#"
                if (q.isEmpty() && letter != lastLetter) {
                    lastLetter = letter
                    item(key = "h$letter$index") {
                        Text(letter, style = bodyStyle(14, Palette.Accent, bold = true), modifier = Modifier.padding(top = 10.dp))
                    }
                }
                item(key = "c" + c.id) {
                    ListRow(c.name, leading = { Avatar(c.name, c.photo) }) { onOpen(c.id) }
                }
            }
            if (shown.isEmpty()) item { EmptyText(if (q.isEmpty()) "Aucun contact." else "Aucun résultat.") }
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
    ToolScaffold("", onBack = nav::back, actions = {
        if (c != null) {
            IconAction(Icons.star, if (c.starred) Palette.Accent else Palette.Muted) {
                ContactsRepo.setStarred(ctx, id, !c.starred); version++
            }
            IconAction(Icons.edit) { nav.go(Route.ContactEdit(id)) }
            IconAction(Icons.trash) { confirmDelete = true }
        }
    }) {
        if (c == null) { EmptyText("Contact introuvable."); return@ToolScaffold }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(c.name, c.photo, 96.dp)
                Spacer(Modifier.height(14.dp))
                Text(c.name, style = serifStyle(32), textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(18.dp))
            if (c.phones.isEmpty()) EmptyText("Pas de numéro.")
            c.phones.forEach { p ->
                ListRow(
                    p.number, p.label,
                    trailing = {
                        Row {
                            IconAction(Icons.message) {
                                nav.go(Route.Conversation(ToolsActivity.threadFor(ctx, p.number), p.number))
                            }
                            IconAction(Icons.phone) { PhoneActions.call(ctx, p.number) }
                        }
                    }
                ) { PhoneActions.call(ctx, p.number) }
                Divider()
            }
            c.emails.forEach { e ->
                ListRow(e, "e-mail") {}
                Divider()
            }
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

    ToolScaffold(if (id == null) "nouveau contact" else "modifier", onBack = nav::back) {
        PermissionGate(
            arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS),
            "Essentiel a besoin d'accéder à tes contacts."
        ) {
            if (!loaded) return@PermissionGate
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(name, { name = it }, "nom", Modifier.fillMaxWidth())
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
