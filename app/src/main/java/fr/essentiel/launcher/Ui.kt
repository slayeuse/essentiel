package fr.essentiel.launcher

import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun EssentielTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Palette.Accent,
            background = Palette.Bg,
            surface = Palette.Bg,
            onSurface = Palette.Ink,
        ),
        content = content,
    )
}

fun serifStyle(size: Int, color: Color = Palette.Ink) =
    TextStyle(fontFamily = Fonts.serif, fontSize = size.sp, lineHeight = (size * 1.15f).sp, color = color)

/**
 * Écran type d'une app intégrée : grand titre (+ sous-titre), actions à droite, contenu.
 * Pas de flèche retour : on utilise le geste / bouton retour d'Android. onBack reste pour la compatibilité.
 */
@Composable
fun ToolScaffold(
    title: String,
    @Suppress("UNUSED_PARAMETER") onBack: () -> Unit = {},
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .paper()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (title.isNotEmpty()) {
                    Text(title, style = serifStyle(38), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (subtitle != null) Text(subtitle, style = bodyStyle(14, Palette.Muted), maxLines = 1)
            }
            actions()
        }
        Spacer(Modifier.height(14.dp))
        content()
    }
}

/** Onglets en forme de pilule (ex. réveils / minuteur / chrono). */
@Composable
fun SegmentedTabs(labels: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.Card)
            .border(1.dp, Palette.Line, shape)
            .padding(4.dp)
    ) {
        labels.forEachIndexed { i, l ->
            val sel = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(if (sel) Palette.Accent else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(l, style = bodyStyle(15, if (sel) Color.White else Palette.Ink, bold = true), maxLines = 1) }
        }
    }
}

/** Bloc arrondi qui regroupe des lignes (style « réglages iOS », aux couleurs d'Essentiel). */
@Composable
fun CardGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFFF2EFEA))
            .border(1.dp, Palette.Line, shape)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        content = content,
    )
}

/** Gros bouton rond orange en bas à droite. */
@Composable
fun Fab(icon: LineIcon, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(62.dp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(Palette.Accent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { LineIconView(icon, Color.White, 28.dp, strokeWidth = 2.4f) }
}

@Composable
fun IconAction(icon: LineIcon, color: Color = Palette.Accent, size: Dp = 24.dp, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) { LineIconView(icon, color, size) }
}

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
) {
    val shape = RoundedCornerShape(20.dp)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 5,
        textStyle = bodyStyle(17),
        keyboardOptions = keyboard,
        cursorBrush = SolidColor(Palette.Accent),
        modifier = modifier
            .clip(shape)
            .background(Palette.Card)
            .border(1.dp, Palette.Line, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, style = bodyStyle(17, Palette.Muted))
                inner()
            }
        }
    )
}

@Composable
fun PillButton(text: String, filled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .background(if (filled) Palette.Accent else Palette.Card)
            .border(1.dp, if (filled) Palette.Accent else Palette.Line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = bodyStyle(16, if (filled) Color.White else Palette.Ink, bold = true))
    }
}

@Composable
fun Divider() = HorizontalDivider(color = Palette.Line, thickness = 1.dp)

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = bodyStyle(13, Palette.Accent, bold = true),
        modifier = modifier.padding(top = 18.dp, bottom = 6.dp)
    )
}

@Composable
fun EmptyText(text: String) {
    Text(text, style = bodyStyle(16, Palette.Muted), modifier = Modifier.padding(vertical = 24.dp))
}

/** Rond avec initiales (ou photo du contact). */
@Composable
fun Avatar(name: String, photo: Uri? = null, size: Dp = 44.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Palette.Card)
            .border(1.dp, Palette.Line, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            AsyncImage(
                model = photo, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        } else {
            val initials = name.split(" ", "-").filter { it.isNotBlank() }.take(2)
                .joinToString("") { it.first().uppercase() }
                .ifBlank { "#" }
            Text(initials, style = bodyStyle((size.value / 2.6f).toInt(), Palette.Accent, bold = true))
        }
    }
}

/** Demande les autorisations nécessaires, puis affiche le contenu. */
@Composable
fun PermissionGate(perms: Array<String>, reason: String, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    fun check() = perms.all { ctx.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    var granted by remember { mutableStateOf(check()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = check()
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(perms) }
    if (granted) {
        content()
    } else {
        Column(Modifier.padding(vertical = 24.dp)) {
            Text(reason, style = bodyStyle(17, Palette.Muted))
            Spacer(Modifier.height(16.dp))
            PillButton("autoriser") { launcher.launch(perms) }
        }
    }
}

/** Compteur qui change à chaque modification d'une base Android (SMS, contacts…), pour recharger l'écran. */
@Composable
fun rememberContentVersion(uri: Uri): Int {
    val ctx = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(uri) {
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { version++ }
        }
        try { ctx.contentResolver.registerContentObserver(uri, true, obs) } catch (e: Exception) { }
        onDispose { ctx.contentResolver.unregisterContentObserver(obs) }
    }
    return version
}

/** Ligne de liste cliquable avec titre / sous-titre. */
@Composable
fun ListRow(
    title: String,
    subtitle: String? = null,
    titleColor: Color = Palette.Ink,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(14.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = bodyStyle(17, titleColor), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = bodyStyle(14, Palette.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) trailing()
    }
}
