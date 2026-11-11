package fr.essentiel.launcher

import android.Manifest
import android.app.role.RoleManager
import fr.essentiel.launcher.phone.PhoneActions
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val LIMIT_CHOICES = listOf(0, 5, 10, 15, 20, 30, 45, 60, 90, 120)

@Composable
fun SettingsScreen(
    prefs: Prefs,
    resumeTick: Int,
    pickerFor: Int?,
    onPick: (Int?) -> Unit,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var refresh by remember { mutableIntStateOf(0) }
    var limitFor by remember { mutableStateOf<AppEntry?>(null) }

    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { AppLauncher.installedApps(ctx) } }

    val askPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val askRole = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh++ }

    // ---------- Choix de l'app d'une case de l'accueil ----------
    if (pickerFor != null) {
        val slot = pickerFor
        val current = remember(slot) { prefs.slots().getOrElse(slot) { "" } }
        fun choose(value: String) { prefs.setSlot(slot, value); onPick(null) }
        Column(Modifier.fillMaxSize().paper().padding(horizontal = 26.dp)) {
            Spacer(Modifier.height(28.dp))
            Title("case ${slot + 1}")
            Text("Quelle app mettre ici ?", style = bodyStyle(16, Palette.Muted))
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.weight(1f)) {
                // Icône de l'app installée dans cette case
                val pkgHere = current.takeIf { it.isNotBlank() && !it.startsWith(AppLauncher.BUILTIN_PREFIX) }
                if (pkgHere != null) {
                    item { Section("Icône de ${AppLauncher.label(ctx, pkgHere)}") }
                    item {
                        val v = AppIcons.version.intValue
                        val chosen = remember(v) { AppIcons.choice(ctx, pkgHere) }
                        val options = listOf("orig", "letter") + AppIcons.LINE.keys.map { "line:$it" }
                        Column {
                            options.chunked(6).forEach { row ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    row.forEach { opt ->
                                        Box(
                                            Modifier.weight(1f).aspectRatio(1f).padding(3.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(if (opt == chosen) Palette.Accent.copy(alpha = 0.15f) else Palette.Card)
                                                .border(if (opt == chosen) 2.dp else 1.dp, if (opt == chosen) Palette.Accent else Palette.Line, RoundedCornerShape(14.dp))
                                                .clickable { AppIcons.setChoice(ctx, pkgHere, opt) },
                                            contentAlignment = Alignment.Center,
                                        ) { AppIconView(pkgHere, 26.dp, choiceOverride = opt) }
                                    }
                                    repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                            Text("La 1re case = icône d'origine de l'app, la 2e = son initiale.", style = bodyStyle(12, Palette.Muted))
                        }
                    }
                }
                item { Section("App de cette case") }
                item { PickRow("Case vide", current.isBlank()) { choose("") } }
                item { Section("Apps Essentiel") }
                items(BUILT_INS, key = { "b_" + it.id }) { b ->
                    val value = AppLauncher.BUILTIN_PREFIX + b.id
                    PickRow(b.label, current == value) { choose(value) }
                }
                item { Section("Apps installées") }
                items(apps, key = { it.pkg }) { app ->
                    PickRow(app.label, current == app.pkg) { choose(app.pkg) }
                }
            }
            BottomLink("retour") { onPick(null) }
        }
        return
    }

    // ---------- Réglages principaux ----------
    val states = remember(resumeTick, refresh) {
        mapOf(
            "calendar" to CalendarRepo.hasPermission(ctx),
            "location" to Weather.hasPermission(ctx),
            "usage" to Usage.hasPermission(ctx),
            "overlay" to Usage.canDrawOverlays(ctx),
        )
    }

    Column(Modifier.fillMaxSize().paper().padding(horizontal = 26.dp)) {
        Spacer(Modifier.height(28.dp))
        Title("réglages")

        LazyColumn(Modifier.weight(1f)) {
            item { Section("Écran d'accueil") }
            item {
                SettingRow("Choisir Essentiel comme launcher", "Réglages Android › Appli d'écran d'accueil") {
                    AppLauncher.start(ctx, Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }

            item { Section("Écran de verrouillage") }
            item {
                val on = remember(resumeTick, refresh) { prefs.lockScreen }
                val overlay = remember(resumeTick, refresh) { Usage.canDrawOverlays(ctx) }
                SettingRow(
                    "Écran de verrouillage Essentiel",
                    when {
                        on && !overlay -> "activé · autorise « Afficher par-dessus les autres apps » ci-dessous"
                        on -> "activé · ton code Android reste demandé après"
                        else -> "désactivé · touche pour l'activer"
                    }
                ) {
                    prefs.lockScreen = !on
                    fr.essentiel.launcher.lock.LockService.ensure(ctx)
                    refresh++
                }
            }

            item { Section("Apps intégrées") }
            item {
                val ok = remember(resumeTick, refresh) { PhoneActions.isDefaultDialer(ctx) }
                SettingRow("App Téléphone par défaut", if (ok) "Essentiel · écran d'appel actif" else "à activer pour l'écran d'appel Essentiel") {
                    PhoneActions.roleIntent(ctx, RoleManager.ROLE_DIALER)?.let { askRole.launch(it) }
                }
            }
            item {
                val ok = remember(resumeTick, refresh) { PhoneActions.isDefaultSms(ctx) }
                SettingRow(
                    "App SMS par défaut",
                    if (ok) "Essentiel · les MMS ne sont pas encore reçus"
                    else "facultatif · sans ça, Essentiel lit et envoie quand même les SMS"
                ) {
                    if (ok) {
                        AppLauncher.start(ctx, Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } else {
                        PhoneActions.roleIntent(ctx, RoleManager.ROLE_SMS)?.let { askRole.launch(it) }
                    }
                }
            }

            item { Section("Autorisations") }
            item {
                SettingRow("Agenda", status(states["calendar"] == true)) {
                    askPerm.launch(Manifest.permission.READ_CALENDAR)
                }
            }
            item {
                SettingRow("Position (météo)", status(states["location"] == true)) {
                    askPerm.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            }
            item {
                SettingRow(
                    "Accès aux données d'utilisation",
                    status(states["usage"] == true) + " · nécessaire pour les limites"
                ) {
                    AppLauncher.start(ctx, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            item {
                SettingRow(
                    "Afficher par-dessus les autres apps",
                    status(states["overlay"] == true) + " · pour fermer une app bloquée"
                ) {
                    AppLauncher.start(
                        ctx,
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }

            item { Section("Écran d'accueil : favoris") }
            item {
                Text(
                    "Appuie longuement sur une case de l'accueil pour modifier les favoris : nombre de cases (4, 8 ou 12), " +
                        "ordre (flèches ‹ ›) et app de chaque case (touche la case).",
                    style = bodyStyle(14, Palette.Muted), modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            items(prefs.slots().take(prefs.tileCount).indices.toList(), key = { "slot_$it" }) { i ->
                val v = prefs.slots()[i]
                SettingRow("Case ${i + 1}", AppLauncher.slotInfo(ctx, v)?.label ?: "vide") { onPick(i) }
            }

            item { Section("Mes apps et limites") }
            item {
                Text(
                    "Touche le nom pour l'ajouter à ta liste. Touche la limite pour la changer. " +
                        "Réduire une limite s'applique tout de suite ; l'augmenter ou la retirer ne compte qu'à partir de demain.",
                    style = bodyStyle(14, Palette.Muted),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            items(apps, key = { "app_" + it.pkg }) { app ->
                // relit les préférences quand quelque chose change
                val inList = remember(refresh) { prefs.myApps.contains(app.pkg) }
                val limit = remember(refresh) { prefs.limitMinutes(app.pkg) }
                val pending = remember(refresh) { prefs.pendingLimit(app.pkg) }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        (if (inList) "●  " else "○  ") + app.label,
                        style = bodyStyle(17, if (inList) Palette.Ink else Palette.Muted),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { prefs.toggleMyApp(app.pkg); refresh++ }
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.clickable { limitFor = app }) {
                        Text(
                            if (limit > 0) "$limit min/jour" else "sans limite",
                            style = bodyStyle(14, if (limit > 0) Palette.Accent else Palette.Muted, bold = limit > 0)
                        )
                        if (pending != null) {
                            Text(
                                "demain : " + if (pending > 0) "$pending min" else "sans limite",
                                style = bodyStyle(12, Palette.Muted)
                            )
                        }
                    }
                }
                HorizontalDivider(color = Palette.Line, thickness = 1.dp)
            }
        }
        BottomLink("fermer", onClose)
    }

    limitFor?.let { app ->
        AlertDialog(
            onDismissRequest = { limitFor = null },
            title = { Text("Limite pour ${app.label}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    LIMIT_CHOICES.forEach { m ->
                        Text(
                            if (m == 0) "Sans limite" else "$m minutes par jour",
                            style = bodyStyle(17),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val now = prefs.setLimit(app.pkg, m)
                                    if (!now) {
                                        Toast.makeText(ctx, "Ce changement s'appliquera demain", Toast.LENGTH_LONG).show()
                                    } else if (m > 0 && !Usage.hasPermission(ctx)) {
                                        Toast.makeText(ctx, "Active « Accès aux données d'utilisation »", Toast.LENGTH_LONG).show()
                                    }
                                    LimitWatcherService.ensure(ctx)
                                    limitFor = null
                                    refresh++
                                }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { limitFor = null }) { Text("Annuler") } },
            containerColor = Palette.Bg,
        )
    }
}

private fun status(ok: Boolean) = if (ok) "activée" else "à activer"

@Composable
private fun Title(text: String) {
    Text(text, style = TextStyle(fontFamily = Fonts.serif, fontSize = 40.sp, color = Palette.Ink))
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun Section(text: String) {
    Text(
        text.uppercase(),
        style = bodyStyle(13, Palette.Accent, bold = true),
        modifier = Modifier.padding(top = 26.dp, bottom = 6.dp)
    )
}

@Composable
private fun SettingRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Text(title, style = bodyStyle(17))
        Text(subtitle, style = bodyStyle(14, Palette.Muted))
    }
    HorizontalDivider(color = Palette.Line, thickness = 1.dp)
}

@Composable
private fun PickRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        (if (selected) "●  " else "○  ") + label,
        style = bodyStyle(17, if (selected) Palette.Ink else Palette.Muted),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp)
    )
    HorizontalDivider(color = Palette.Line, thickness = 1.dp)
}

@Composable
private fun BottomLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = bodyStyle(16, Palette.Accent, bold = true),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp)
    )
}
