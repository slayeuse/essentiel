package fr.essentiel.launcher.control

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import fr.essentiel.launcher.AppIconView
import fr.essentiel.launcher.AppLauncher
import fr.essentiel.launcher.EssentielTheme
import fr.essentiel.launcher.Fonts
import fr.essentiel.launcher.Icons
import fr.essentiel.launcher.LineIcon
import fr.essentiel.launcher.LineIconView
import fr.essentiel.launcher.Palette
import fr.essentiel.launcher.PillButton
import fr.essentiel.launcher.SectionTitle
import fr.essentiel.launcher.SegmentedTabs
import fr.essentiel.launcher.ToolsActivity
import fr.essentiel.launcher.bodyStyle
import fr.essentiel.launcher.paper
import fr.essentiel.launcher.serifStyle
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Centre de contrôle ouvert depuis n'importe quelle app (poignée en haut à droite). */
class ControlActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Fonts.init(this)
        setContent { EssentielTheme { ControlCenter(onClose = { finish() }) } }
    }

    override fun onPause() {
        super.onPause()
        if (!isChangingConfigurations) finish()
    }
}

@Composable
fun ControlCenter(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        Controls.initTorch(ctx)
        while (true) { delay(1000); tick++ }
    }
    BackHandler(onBack = onClose)

    // Lit l'état du téléphone (rafraîchi chaque seconde et après chaque action)
    val wifi = remember(tick) { Controls.wifiOn(ctx) }
    val bt = remember(tick) { Controls.bluetoothOn() }
    val plane = remember(tick) { Controls.airplaneOn(ctx) }
    val torch = remember(tick) { Controls.torchOn }
    val dnd = remember(tick) { Controls.dndOn(ctx) }
    val rotate = remember(tick) { Controls.autoRotate(ctx) }
    val loc = remember(tick) { Controls.locationOn(ctx) }
    val autoBright = remember(tick) { Controls.autoBrightness(ctx) }
    val ringer = remember(tick) { Controls.ringer(ctx) }
    val batt = remember(tick) { Controls.battery(ctx) }
    val charging = remember(tick) { Controls.charging(ctx) }
    val playing = remember(tick) { NotifListener.nowPlaying(ctx) }
    val notifAccess = remember(tick) { Controls.hasNotifAccess(ctx) }

    var bright by remember { mutableFloatStateOf(Controls.brightness(ctx)) }
    var vol by remember { mutableFloatStateOf(Controls.volume(ctx)) }
    var lastTouch by remember { mutableLongStateOf(0L) }
    LaunchedEffect(tick) {
        if (System.currentTimeMillis() - lastTouch > 1500) {
            bright = Controls.brightness(ctx)
            vol = Controls.volume(ctx)
        }
    }
    val refresh: () -> Unit = { tick++ }
    var closeDrag by remember { mutableFloatStateOf(0f) }

    Column(
        Modifier
            .fillMaxSize()
            .paper()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
    ) {
        // ----- En-tête : heure, batterie (glisser vers le haut pour fermer) -----
        val now = remember(tick / 10) { Date() }
        Row(
            Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { closeDrag = 0f },
                        onDragEnd = { if (closeDrag < -80f) onClose() },
                        onVerticalDrag = { _, d -> closeDrag += d },
                    )
                }
                .padding(top = 22.dp, bottom = 14.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f)) {
                Text(SimpleDateFormat("HH:mm", Locale.FRENCH).format(now), style = serifStyle(54))
                Text(SimpleDateFormat("EEEE d MMMM", Locale.FRENCH).format(now), style = bodyStyle(15, Palette.Muted))
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                LineIconView(if (charging) Icons.flash else Icons.battery, if (batt in 0..15) Palette.Accent else Palette.Ink, 20.dp)
                Spacer(Modifier.width(6.dp))
                Text(if (batt >= 0) "$batt %" else "", style = bodyStyle(17, bold = true))
            }
        }

        // ----- Connexions + musique -----
        Row(Modifier.fillMaxWidth().height(176.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        RoundToggle(Icons.plane, plane, "Avion") { Controls.openAirplane(ctx) }
                        RoundToggle(Icons.signal, !plane, "Données") { Controls.openData(ctx) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        RoundToggle(Icons.wifi, wifi, "Wi-Fi") { Controls.openWifi(ctx) }
                        RoundToggle(Icons.bluetooth, bt, "Bluetooth") { Controls.setBluetooth(ctx, !bt); refresh() }
                    }
                }
            }
            Card(Modifier.weight(1f).fillMaxHeight()) { MediaCard(playing, notifAccess, refresh) }
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(Icons.torch, "Lampe", torch, Modifier.weight(1f)) { Controls.setTorch(ctx, !torch); refresh() }
            Tile(Icons.moon, "Ne pas\ndéranger", dnd, Modifier.weight(1f)) { Controls.setDnd(ctx, !dnd); refresh() }
            Tile(Icons.rotate, "Rotation\nauto", rotate, Modifier.weight(1f)) { Controls.setAutoRotate(ctx, !rotate); refresh() }
            Tile(Icons.locate, "Position", loc, Modifier.weight(1f)) { Controls.setLocation(ctx, !loc); refresh() }
        }

        // ----- Curseurs -----
        Spacer(Modifier.height(12.dp))
        var askedWrite by remember { mutableStateOf(false) }
        BigSlider(bright, Icons.sun, "Luminosité") { v ->
            lastTouch = System.currentTimeMillis()
            bright = v
            if (!Controls.setBrightness(ctx, v) && !askedWrite) {
                askedWrite = true
                android.widget.Toast.makeText(ctx, "Autorise « Modifier les paramètres système » pour Essentiel", android.widget.Toast.LENGTH_LONG).show()
                Controls.openWriteSettings(ctx)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Luminosité automatique", style = bodyStyle(14, Palette.Muted), modifier = Modifier.weight(1f))
            SmallPill(if (autoBright) "activée" else "désactivée", autoBright) { Controls.setAutoBrightness(ctx, !autoBright); refresh() }
        }
        Spacer(Modifier.height(10.dp))
        BigSlider(vol, if (vol < 0.01f) Icons.speakerOff else Icons.speaker, "Volume") { v ->
            lastTouch = System.currentTimeMillis()
            vol = v
            Controls.setVolume(ctx, v)
        }
        Spacer(Modifier.height(12.dp))
        val modes = listOf(AudioManager.RINGER_MODE_NORMAL, AudioManager.RINGER_MODE_VIBRATE, AudioManager.RINGER_MODE_SILENT)
        SegmentedTabs(listOf("Sonnerie", "Vibreur", "Silence"), modes.indexOf(ringer).coerceAtLeast(0), Modifier.fillMaxWidth()) {
            Controls.setRinger(ctx, modes[it]); refresh()
        }

        // ----- Raccourcis -----
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Shortcut(Icons.timer, "Minuteur", Modifier.weight(1f)) { openTool(ctx, "clock"); onClose() }
            Shortcut(Icons.calc, "Calcul", Modifier.weight(1f)) { openTool(ctx, "calc"); onClose() }
            Shortcut(Icons.camera, "Photo", Modifier.weight(1f)) { openTool(ctx, "camera"); onClose() }
            Shortcut(Icons.hotspot, "Partage", Modifier.weight(1f)) { Controls.openHotspot(ctx) }
        }

        // ----- Notifications -----
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Notifications", Modifier.weight(1f))
            if (NotifListener.items.any { it.clearable }) {
                Text(
                    "tout effacer", style = bodyStyle(13, Palette.Accent, bold = true),
                    modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(50)).clickable { NotifListener.clearAll() }.padding(6.dp)
                )
            }
        }
        if (!notifAccess) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Affiche tes notifications ici, dans le style Essentiel.", style = bodyStyle(15))
                    Spacer(Modifier.height(10.dp))
                    PillButton("autoriser l'accès aux notifications") { Controls.openNotifAccess(ctx) }
                }
            }
        } else if (NotifListener.items.isEmpty()) {
            Text("Aucune notification. Profite.", style = bodyStyle(15, Palette.Muted), modifier = Modifier.padding(vertical = 8.dp))
        } else {
            NotifListener.items.forEach { n ->
                key(n.key) { NotifCard(n) { NotifListener.open(ctx, n); onClose() } }
            }
        }

        Spacer(Modifier.height(18.dp))
        PillButton("Terminé", filled = false, modifier = Modifier.fillMaxWidth(), onClick = onClose)
        Spacer(Modifier.height(24.dp))
    }
}

private fun openTool(ctx: Context, route: String) = AppLauncher.start(ctx, ToolsActivity.intent(ctx, route))

// ---------------------------------------------------------------- Briques

@Composable
private fun Card(modifier: Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Box(modifier.clip(shape).background(Palette.Card).border(1.dp, Palette.Line, shape)) { content() }
}

@Composable
private fun RoundToggle(icon: LineIcon, on: Boolean, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(if (on) Palette.Accent else Palette.Bg)
                .border(1.dp, if (on) Palette.Accent else Palette.Line, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { LineIconView(icon, if (on) Color.White else Palette.Ink, 22.dp) }
        Spacer(Modifier.height(3.dp))
        Text(label, style = bodyStyle(11, Palette.Muted))
    }
}

@Composable
private fun Tile(icon: LineIcon, label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(if (on) Palette.Accent else Palette.Card)
            .border(1.dp, if (on) Palette.Accent else Palette.Line, shape)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        LineIconView(icon, if (on) Color.White else Palette.Accent, 24.dp)
        Spacer(Modifier.weight(1f))
        Text(label, style = bodyStyle(12, if (on) Color.White else Palette.Ink, bold = true), maxLines = 2)
    }
}

@Composable
private fun Shortcut(icon: LineIcon, label: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.clip(shape).background(Palette.Card).border(1.dp, Palette.Line, shape).clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LineIconView(icon, Palette.Accent, 24.dp)
        Spacer(Modifier.height(6.dp))
        Text(label, style = bodyStyle(12))
    }
}

@Composable
private fun SmallPill(text: String, on: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = bodyStyle(13, if (on) Color.White else Palette.Ink, bold = true),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (on) Palette.Accent else Palette.Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    )
}

/** Grand curseur façon iOS : on touche ou on glisse n'importe où dessus. */
@Composable
private fun BigSlider(value: Float, icon: LineIcon, label: String, onChange: (Float) -> Unit) {
    val change by rememberUpdatedState(onChange)
    val shape = RoundedCornerShape(26.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(shape)
            .background(Palette.Card)
            .border(1.dp, Palette.Line, shape)
            .pointerInput(Unit) { detectTapGestures { o -> change((o.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { c, _ -> change((c.position.x / size.width).coerceIn(0f, 1f)) }
            }
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(value.coerceIn(0f, 1f)).background(Palette.Accent))
        Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            LineIconView(icon, Palette.Ink, 22.dp)
            Spacer(Modifier.width(12.dp))
            Text(label, style = bodyStyle(16, bold = true), modifier = Modifier.weight(1f))
            Text("${(value * 100).roundToInt()} %", style = bodyStyle(14))
        }
    }
}

@Composable
private fun MediaCard(np: NowPlaying?, access: Boolean, refresh: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        if (np == null) {
            LineIconView(Icons.music, Palette.Accent, 26.dp)
            Spacer(Modifier.weight(1f))
            Text("Rien en lecture", style = bodyStyle(15, bold = true))
            Text(if (access) "Lance de la musique, elle s'affichera ici." else "Autorise les notifications pour voir la musique.", style = bodyStyle(12, Palette.Muted), maxLines = 3)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val art = np.art
                if (art != null) {
                    Image(
                        art.asImageBitmap(), null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(Palette.Bg), contentAlignment = Alignment.Center) {
                        AppIconView(np.pkg, 30.dp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(np.title, style = bodyStyle(15, bold = true), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(np.artist, style = bodyStyle(12, Palette.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                MediaButton(Icons.prev) { NotifListener.previous(ctx); refresh() }
                MediaButton(if (np.playing) Icons.pause else Icons.play, big = true) { NotifListener.playPause(ctx); refresh() }
                MediaButton(Icons.next) { NotifListener.next(ctx); refresh() }
            }
        }
    }
}

@Composable
private fun MediaButton(icon: LineIcon, big: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(if (big) 44.dp else 36.dp).clip(CircleShape).background(if (big) Palette.Accent else Color.Transparent).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { LineIconView(icon, if (big) Color.White else Palette.Ink, if (big) 20.dp else 18.dp) }
}

@Composable
private fun NotifCard(n: NItem, onOpen: () -> Unit) {
    val ctx = LocalContext.current
    val label = remember(n.pkg) { AppLauncher.label(ctx, n.pkg) }
    var dx by remember { mutableFloatStateOf(0f) }
    var gone by remember { mutableStateOf(false) }
    if (gone) return
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .padding(vertical = 5.dp)
            .fillMaxWidth()
            .offset { IntOffset(dx.roundToInt(), 0) }
            .clip(shape)
            .background(Palette.Card)
            .border(1.dp, Palette.Line, shape)
            .pointerInput(n.key) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (n.clearable && abs(dx) > size.width * 0.35f) { gone = true; NotifListener.dismiss(n.key) } else { dx = 0f }
                    },
                    onDragCancel = { dx = 0f },
                ) { _, d -> dx += d }
            }
            .clickable(onClick = onOpen)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AppIconView(n.pkg, 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row {
                Text(label, style = bodyStyle(12, Palette.Muted, bold = true), maxLines = 1, modifier = Modifier.weight(1f))
                Text(ago(n.time), style = bodyStyle(12, Palette.Muted))
            }
            if (n.title.isNotBlank()) Text(n.title, style = bodyStyle(15, bold = true), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (n.text.isNotBlank()) Text(n.text, style = bodyStyle(14), maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (n.clearable) {
            Box(
                Modifier.padding(start = 6.dp).size(28.dp).clip(CircleShape).clickable { gone = true; NotifListener.dismiss(n.key) },
                contentAlignment = Alignment.Center,
            ) { LineIconView(Icons.close, Palette.Muted, 14.dp) }
        }
    }
}

private fun ago(t: Long): String {
    val m = (System.currentTimeMillis() - t) / 60_000
    return when {
        m < 1 -> "maintenant"
        m < 60 -> "il y a $m min"
        m < 24 * 60 -> SimpleDateFormat("HH:mm", Locale.FRENCH).format(Date(t))
        else -> SimpleDateFormat("d MMM", Locale.FRENCH).format(Date(t))
    }
}
