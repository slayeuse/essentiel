package fr.essentiel.launcher.control

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.essentiel.launcher.AppIconView
import fr.essentiel.launcher.CardGroup
import fr.essentiel.launcher.Divider
import fr.essentiel.launcher.Icons
import fr.essentiel.launcher.LineIconView
import fr.essentiel.launcher.Nav
import fr.essentiel.launcher.Palette
import fr.essentiel.launcher.PillButton
import fr.essentiel.launcher.SectionTitle
import fr.essentiel.launcher.SegmentedTabs
import fr.essentiel.launcher.ToolScaffold
import fr.essentiel.launcher.bodyStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Réglages « téléphone Essentiel » : autorisations, centre de contrôle, barre d'état, nettoyage. */
@Composable
fun SystemScreen(nav: Nav) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }

    @Suppress("DEPRECATION")
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) tick++
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    ToolScaffold("Système", onBack = nav::back, subtitle = "ton téléphone, façon Essentiel") {
        SegmentedTabs(listOf("Réglages", "Nettoyer"), tab, Modifier.fillMaxWidth()) { tab = it }
        Spacer(Modifier.height(8.dp))
        if (tab == 0) SettingsTab(tick) { tick++ } else CleanupTab()
    }
}

// ---------------------------------------------------------------- Réglages

@Composable
private fun SettingsTab(tick: Int, refresh: () -> Unit) {
    val ctx = LocalContext.current
    val notif = remember(tick) { Controls.hasNotifAccess(ctx) }
    val dnd = remember(tick) { Controls.hasDndAccess(ctx) }
    val write = remember(tick) { Controls.canWriteSettings(ctx) }
    val overlay = remember(tick) { Controls.canOverlay(ctx) }
    val secure = remember(tick) { Controls.hasSecureSettings(ctx) }
    val edge = remember(tick) { NotifListener.edgeEnabled(ctx) }
    val hidden = remember(tick) { Controls.hiddenIcons(ctx) }

    LazyColumn {
        item { SectionTitle("Autorisations") }
        item {
            CardGroup {
                PermRow("Notifications", "pour les afficher dans le centre de contrôle", notif) { Controls.openNotifAccess(ctx) }
                Divider()
                PermRow("Ne pas déranger", "pour le bouton lune et le mode silence", dnd) { Controls.openDndAccess(ctx) }
                Divider()
                PermRow("Modifier les paramètres système", "luminosité et rotation", write) { Controls.openWriteSettings(ctx) }
                Divider()
                PermRow("Afficher par-dessus les autres apps", "poignée du centre de contrôle", overlay) { Controls.openOverlay(ctx) }
                Divider()
                PermRow(
                    "Mode avancé",
                    if (secure) "activé par le script Mac" else "à activer une fois avec le script Mac (voir plus bas)",
                    secure,
                ) {}
            }
        }

        item { SectionTitle("Centre de contrôle") }
        item {
            CardGroup {
                Text("Sur l'accueil : glisse vers le bas.", style = bodyStyle(15))
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Partout, depuis le coin en haut à droite", style = bodyStyle(16, bold = true))
                        Text(
                            "Une petite zone invisible juste sous la barre d'état, à droite : glisse vers le bas dessus. " +
                                "Elle peut gêner les boutons tout en haut à droite de certaines apps.",
                            style = bodyStyle(13, Palette.Muted)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Switch(
                        checked = edge,
                        onCheckedChange = { on ->
                            when {
                                on && !notif -> Controls.openNotifAccess(ctx)
                                on && !overlay -> Controls.openOverlay(ctx)
                            }
                            NotifListener.setEdgeEnabled(ctx, on)
                            refresh()
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = Palette.Accent, checkedThumbColor = Color.White),
                    )
                }
                if (edge && (!notif || !overlay)) {
                    Text("Il manque : " + listOfNotNull(if (!notif) "Notifications" else null, if (!overlay) "Afficher par-dessus" else null).joinToString(", "),
                        style = bodyStyle(13, Palette.Accent, bold = true), modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        item { SectionTitle("Barre d'état : icônes à masquer") }
        item {
            CardGroup {
                if (!secure) {
                    Text("Disponible après le script Mac (mode avancé).", style = bodyStyle(15, Palette.Muted))
                } else {
                    Controls.STATUS_ICONS.forEachIndexed { i, (slot, label) ->
                        if (i > 0) Divider()
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(label, style = bodyStyle(16), modifier = Modifier.weight(1f))
                            Switch(
                                checked = slot in hidden,
                                onCheckedChange = { Controls.setIconHidden(ctx, slot, it); refresh() },
                                colors = SwitchDefaults.colors(checkedTrackColor = Palette.Accent, checkedThumbColor = Color.White),
                            )
                        }
                    }
                    Text("Activé = masqué. Si une icône ne disparaît pas, c'est que l'interface d'ASUS l'impose.", style = bodyStyle(12, Palette.Muted), modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        item { SectionTitle("Script Mac (une seule fois)") }
        item {
            CardGroup {
                Step("1", "Sur le téléphone : Paramètres → Système → À propos → Informations sur le logiciel → touche 7 fois « Numéro de build ».")
                Step("2", "Paramètres → Système → Options pour les développeurs → active « Débogage USB ».")
                Step("3", "Branche le téléphone au Mac en USB.")
                Step("4", "Sur le Mac, ouvre Terminal, tape « bash » suivi d'un espace, glisse le fichier outils/Essentiel-Mac.command dans la fenêtre, puis Entrée.")
                Step("5", "Accepte « Autoriser le débogage USB » sur le téléphone, puis choisis 1 dans le menu.")
                Spacer(Modifier.height(4.dp))
                Text("Rien n'est rooté, la garantie et les mises à jour ne changent pas. Tout se défait avec le même script.", style = bodyStyle(13, Palette.Muted))
            }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }
}

@Composable
private fun PermRow(title: String, sub: String, ok: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = bodyStyle(16, bold = true))
            Text(sub, style = bodyStyle(13, Palette.Muted))
        }
        Box(
            Modifier.size(28.dp).clip(RoundedCornerShape(50)).background(if (ok) Palette.Accent else Palette.Bg).border(1.dp, if (ok) Palette.Accent else Palette.Line, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) { if (ok) LineIconView(Icons.check, Color.White, 16.dp) }
    }
}

@Composable
private fun Step(n: String, text: String) {
    Row(Modifier.padding(vertical = 5.dp)) {
        Text(n, style = bodyStyle(15, Palette.Accent, bold = true), modifier = Modifier.width(22.dp))
        Text(text, style = bodyStyle(15))
    }
}

// ---------------------------------------------------------------- Nettoyage

private data class AppEntry(val pkg: String, val label: String, val system: Boolean, val advice: Int) // 1 conseillé, 0 neutre, -1 à garder

private val SOCIAL_PKGS = listOf(
    "com.facebook.", "com.instagram.", "com.zhiliaoapp.musically", "com.ss.android.ugc", "com.twitter.android",
    "com.snapchat.android", "com.pinterest", "com.tinder", "com.bereal",
)
private val NEVER = listOf(
    "android", "com.android.systemui", "com.android.settings", "com.android.phone", "com.android.vending",
    "com.google.android.gms", "com.google.android.gsf", "com.android.providers.", "com.android.packageinstaller",
    "com.google.android.packageinstaller", "com.android.permissioncontroller", "com.google.android.permissioncontroller",
    "com.android.shell", "com.android.server.", "com.qualcomm.", "com.qti.", "org.codeaurora.", "com.android.bluetooth",
    "com.android.nfc", "com.android.se", "com.android.stk", "com.google.android.webview", "com.android.webview",
)
private val KEEP_HINTS = listOf("dialer", "contacts", "mms", "messaging", "camera", "documentsui", "filemanager", "launcher", "inputmethod", "keyboard", "deskclock")

private fun isNever(pkg: String) = NEVER.any { if (it.endsWith(".")) pkg.startsWith(it) else pkg == it }

private fun candidates(ctx: Context): List<AppEntry> {
    val pm = ctx.packageManager
    return pm.getInstalledApplications(0).mapNotNull { ai ->
        val pkg = ai.packageName
        if (pkg == ctx.packageName || isNever(pkg)) return@mapNotNull null
        val social = SOCIAL_PKGS.any { pkg.startsWith(it) }
        if (!social && pm.getLaunchIntentForPackage(pkg) == null) return@mapNotNull null
        val advice = when {
            social -> 1
            KEEP_HINTS.any { pkg.contains(it) } -> -1
            else -> 0
        }
        AppEntry(pkg, pm.getApplicationLabel(ai).toString(), ai.flags and ApplicationInfo.FLAG_SYSTEM != 0, advice)
    }.sortedWith(compareByDescending<AppEntry> { it.advice }.thenBy { it.label.lowercase() })
}

private fun listFile(ctx: Context) = File(ctx.getExternalFilesDir(null), "nettoyage.txt")

private fun readSelection(ctx: Context): Set<String> = try {
    listFile(ctx).takeIf { it.exists() }?.readLines()?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
} catch (e: Exception) { emptySet() }

private fun saveSelection(ctx: Context, set: Set<String>): Boolean = try {
    listFile(ctx).parentFile?.mkdirs()
    listFile(ctx).writeText(set.sorted().joinToString("\n") + "\n")
    true
} catch (e: Exception) { false }

@Composable
private fun CleanupTab() {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var selected by remember { mutableStateOf(readSelection(ctx)) }
    var saved by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { candidates(ctx) } }

    Column {
        Text(
            "Coche les apps à faire disparaître du téléphone (même celles qu'Android ne laisse pas désinstaller). " +
                "Le script Mac les retire pour toi, choix 2. Elles restent récupérables à tout moment (choix 3 ou 4).",
            style = bodyStyle(14, Palette.Muted)
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${selected.size} app(s) choisie(s)" + if (!saved) " · non enregistré" else "",
                style = bodyStyle(14, if (saved) Palette.Ink else Palette.Accent, bold = true),
                modifier = Modifier.weight(1f)
            )
            PillButton("Enregistrer", filled = !saved) {
                saved = saveSelection(ctx, selected)
                android.widget.Toast.makeText(ctx, if (saved) "Liste prête pour le script Mac" else "Impossible d'enregistrer", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        Spacer(Modifier.height(8.dp))
        val list = apps
        if (list == null) {
            Text("Recherche des apps…", style = bodyStyle(15, Palette.Muted))
        } else {
            LazyColumn {
                items(list, key = { it.pkg }) { a ->
                    val on = a.pkg in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { selected = if (on) selected - a.pkg else selected + a.pkg; saved = false }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIconView(a.pkg, 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.label, style = bodyStyle(16), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                when (a.advice) {
                                    1 -> "réseau social · conseillé"
                                    -1 -> "à garder en secours"
                                    else -> if (a.system) "préinstallée" else "installée par toi"
                                },
                                style = bodyStyle(12, if (a.advice == 1) Palette.Accent else Palette.Muted, bold = a.advice != 0)
                            )
                        }
                        Box(
                            Modifier.size(26.dp).clip(RoundedCornerShape(8.dp))
                                .background(if (on) Palette.Accent else Palette.Bg)
                                .border(1.dp, if (on) Palette.Accent else Palette.Line, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center,
                        ) { if (on) LineIconView(Icons.check, Color.White, 16.dp) }
                    }
                }
                item { Spacer(Modifier.height(30.dp)) }
            }
        }
    }
}
