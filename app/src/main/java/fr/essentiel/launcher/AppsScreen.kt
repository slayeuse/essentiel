package fr.essentiel.launcher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class AppRow(val pkg: String, val label: String, val status: String?, val blocked: Boolean)

/** Liste texte « mes apps », ouverte en glissant vers le haut. */
@Composable
fun AppsScreen(
    prefs: Prefs,
    resumeTick: Int,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    onBlocked: (String) -> Unit,
) {
    val ctx = LocalContext.current
    var rows by remember { mutableStateOf<List<AppRow>>(emptyList()) }

    LaunchedEffect(resumeTick) {
        rows = withContext(Dispatchers.IO) {
            val usage = Usage.todayUsageMs(ctx)
            prefs.myApps.filter { AppLauncher.isInstalled(ctx, it) }.map { pkg ->
                val limit = prefs.limitMinutes(pkg)
                val used = usage[pkg] ?: 0L
                val left = limit * 60_000L - used
                when {
                    limit <= 0 -> AppRow(pkg, AppLauncher.label(ctx, pkg), null, false)
                    !Usage.hasPermission(ctx) -> AppRow(pkg, AppLauncher.label(ctx, pkg), "limite inactive", false)
                    left <= 0 -> AppRow(pkg, AppLauncher.label(ctx, pkg), "bloquée", true)
                    else -> AppRow(pkg, AppLauncher.label(ctx, pkg), "${Usage.formatMinutes(left)} restantes", false)
                }
            }
        }
    }

    var drag by remember { mutableStateOf(0f) }
    Column(
        Modifier
            .fillMaxSize()
            .paper()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = { if (drag > 150f) onClose() },
                    onVerticalDrag = { _, d -> drag += d },
                )
            }
            .padding(horizontal = 26.dp)
    ) {
        Spacer(Modifier.height(28.dp))
        Text(
            "mes apps",
            style = TextStyle(fontFamily = Fonts.serif, fontSize = 40.sp, color = Palette.Ink),
            modifier = Modifier.clickable(interactionSource = null, indication = null) { onClose() }
        )
        Spacer(Modifier.height(20.dp))

        // Apps intégrées qui ne sont pas déjà sur l'écran d'accueil
        val onGrid = remember(resumeTick) {
            Tile.entries.mapNotNull { t ->
                val c = prefs.buttonTarget(t.key)
                when {
                    c == null -> t.builtIn
                    c.startsWith(AppLauncher.BUILTIN_PREFIX) -> c.removePrefix(AppLauncher.BUILTIN_PREFIX)
                    else -> null
                }
            }.toSet()
        }
        val extra = BUILT_INS.filter { it.id !in onGrid }
        if (extra.isNotEmpty()) {
            extra.forEach { b ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { AppLauncher.start(ctx, ToolsActivity.intent(ctx, b.id)) }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LineIconView(b.icon, Palette.Accent, 22.dp)
                    Spacer(Modifier.width(14.dp))
                    Text(b.label.lowercase(), style = bodyStyle(22), modifier = Modifier.weight(1f))
                }
                HorizontalDivider(color = Palette.Line, thickness = 1.dp)
            }
            Spacer(Modifier.height(8.dp))
        }

        if (rows.isEmpty()) {
            Text(
                "Aucune app pour l'instant.\nAjoute celles dont tu as besoin dans les réglages.",
                style = bodyStyle(17, Palette.Muted)
            )
        }

        LazyColumn(Modifier.weight(1f)) {
            items(rows, key = { it.pkg }) { row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (row.blocked) onBlocked(row.pkg)
                            else AppLauncher.launchPackage(ctx, prefs, row.pkg, onBlocked)
                        }
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        row.label.lowercase(),
                        style = bodyStyle(22, if (row.blocked) Palette.Muted else Palette.Ink),
                        modifier = Modifier.weight(1f)
                    )
                    row.status?.let {
                        Text(it, style = bodyStyle(14, if (row.blocked) Palette.Accent else Palette.Muted))
                    }
                }
                HorizontalDivider(color = Palette.Line, thickness = 1.dp)
            }
        }

        Text(
            "réglages",
            style = bodyStyle(16, Palette.Accent, bold = true),
            modifier = Modifier
                .clickable { onOpenSettings() }
                .padding(vertical = 18.dp)
        )
    }
}
