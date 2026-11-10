package fr.essentiel.launcher.phone

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.essentiel.launcher.Avatar
import fr.essentiel.launcher.EssentielTheme
import fr.essentiel.launcher.Fonts
import fr.essentiel.launcher.Icons
import fr.essentiel.launcher.LineIcon
import fr.essentiel.launcher.LineIconView
import fr.essentiel.launcher.Palette
import fr.essentiel.launcher.bodyStyle
import fr.essentiel.launcher.contacts.ContactsRepo
import fr.essentiel.launcher.paper
import fr.essentiel.launcher.serifStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val Green = Color(0xFF2E9E5B)

/** Écran affiché pendant un appel (entrant, sortant, en cours). S'affiche aussi sur l'écran verrouillé. */
class InCallActivity : ComponentActivity() {

    private var proximity: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Fonts.init(this)

        // Éteint l'écran quand le téléphone est contre l'oreille
        val pm = getSystemService(PowerManager::class.java)
        if (pm != null && pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            proximity = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "essentiel:appel")
        }

        setContent {
            EssentielTheme {
                InCallScreen(
                    onEnded = { Handler(Looper.getMainLooper()).postDelayed({ finish() }, 900) },
                    onProximity = { on -> setProximity(on) },
                )
            }
        }
    }

    private fun setProximity(on: Boolean) {
        val wl = proximity ?: return
        try {
            if (on && !wl.isHeld) wl.acquire(3 * 60 * 60 * 1000L)
            if (!on && wl.isHeld) wl.release()
        } catch (e: Exception) { }
    }

    override fun onDestroy() {
        setProximity(false)
        super.onDestroy()
    }
}

@Composable
private fun InCallScreen(onEnded: () -> Unit, onProximity: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val tick = CallManager.tick.intValue // se met à jour à chaque changement d'état
    val calls = CallManager.calls.toList()
    val ringing = CallManager.ringing()
    val main = CallManager.main()
    val audio = CallManager.audio

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }

    val ended = calls.isEmpty() || calls.all { CallManager.state(it) == Call.STATE_DISCONNECTED }
    LaunchedEffect(ended) { if (ended) onEnded() }

    val speakerOn = audio?.route == CallAudioState.ROUTE_SPEAKER
    val shown = if (ringing != null && main === ringing) ringing else main
    val activeCall = shown != null && CallManager.state(shown) == Call.STATE_ACTIVE
    LaunchedEffect(activeCall, speakerOn) { onProximity(activeCall && !speakerOn) }

    var showKeypad by remember { mutableStateOf(false) }
    var dtmf by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .paper()
            .padding(horizontal = 26.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (shown == null) {
            Spacer(Modifier.weight(1f))
            Text("appel terminé", style = serifStyle(34))
            Spacer(Modifier.weight(1f))
            return@Column
        }
        val number = CallManager.number(shown)
        var name by remember(number) { mutableStateOf<String?>(null) }
        LaunchedEffect(number) { name = withContext(Dispatchers.IO) { ContactsRepo.nameFor(ctx, number) } }
        val title = name ?: number.ifBlank { "numéro masqué" }

        val state = CallManager.state(shown)
        val status = when (state) {
            Call.STATE_RINGING -> "appel entrant"
            Call.STATE_DIALING, Call.STATE_CONNECTING -> "appel…"
            Call.STATE_HOLDING -> "en attente"
            Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> "appel terminé"
            Call.STATE_ACTIVE -> {
                val start = shown.details?.connectTimeMillis ?: 0L
                if (start > 0) {
                    val s = ((now - start) / 1000).coerceAtLeast(0)
                    "%d:%02d".format(s / 60, s % 60)
                } else "en cours"
            }
            else -> ""
        }

        Spacer(Modifier.height(40.dp))
        Avatar(title, null, 96.dp)
        Spacer(Modifier.height(18.dp))
        Text(title, style = serifStyle(36), textAlign = TextAlign.Center, maxLines = 2)
        if (name != null) Text(number, style = bodyStyle(16, Palette.Muted))
        Spacer(Modifier.height(8.dp))
        Text(status, style = bodyStyle(18, Palette.Accent, bold = true))

        // Deuxième appel qui arrive pendant un appel
        if (ringing != null && shown !== ringing) {
            Spacer(Modifier.height(18.dp))
            val other = CallManager.number(ringing)
            Text(
                "2e appel : ${ContactsRepo.nameFor(ctx, other) ?: other}",
                style = bodyStyle(16), modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallPill("refuser", Palette.Accent) { CallManager.hangup(ringing) }
                SmallPill("répondre", Green) { CallManager.answer(ringing) }
            }
        }

        Spacer(Modifier.weight(1f))

        if (state == Call.STATE_RINGING) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RoundButton(Icons.phone, Palette.Accent, "refuser", rotate = 135f) { CallManager.hangup(shown) }
                RoundButton(Icons.phone, Green, "répondre") { CallManager.answer(shown) }
            }
        } else {
            if (showKeypad) {
                Text(dtmf.ifEmpty { " " }, style = serifStyle(30))
                Spacer(Modifier.height(8.dp))
                listOf("123", "456", "789", "*0#").forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { k ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(2.2f)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Palette.Card)
                                    .border(1.dp, Palette.Line, RoundedCornerShape(18.dp))
                                    .clickable {
                                        dtmf += k
                                        shown.playDtmfTone(k)
                                        Handler(Looper.getMainLooper()).postDelayed({ shown.stopDtmfTone() }, 180)
                                    },
                                contentAlignment = Alignment.Center,
                            ) { Text(k.toString(), style = serifStyle(26)) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            } else {
                val muted = audio?.isMuted == true
                val holding = state == Call.STATE_HOLDING
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Toggle(Icons.micOff, "muet", muted) { CallManager.setMuted(!muted) }
                    Toggle(Icons.keypad, "clavier", false) { showKeypad = true }
                    Toggle(Icons.speaker, "haut-parleur", speakerOn) { CallManager.setSpeaker(!speakerOn) }
                }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Toggle(Icons.pause, if (holding) "reprendre" else "attente", holding) {
                        if (holding) shown.unhold() else shown.hold()
                    }
                }
            }
            Spacer(Modifier.height(26.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                if (showKeypad) {
                    Text(
                        "masquer", style = bodyStyle(16, Palette.Muted, bold = true),
                        modifier = Modifier.clickable { showKeypad = false }.padding(20.dp)
                    )
                }
                RoundButton(Icons.phone, Palette.Accent, "raccrocher", rotate = 135f) { CallManager.hangup(shown) }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun RoundButton(icon: LineIcon, color: Color, label: String, rotate: Float = 0f, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(color)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { LineIconView(icon, Color.White, 32.dp, modifier = Modifier.rotate(rotate)) }
        Spacer(Modifier.height(8.dp))
        Text(label, style = bodyStyle(14, Palette.Muted))
    }
}

@Composable
private fun Toggle(icon: LineIcon, label: String, on: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(66.dp)
                .clip(CircleShape)
                .background(if (on) Palette.Accent else Palette.Card)
                .border(1.dp, if (on) Palette.Accent else Palette.Line, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { LineIconView(icon, if (on) Color.White else Palette.Accent, 28.dp) }
        Spacer(Modifier.height(6.dp))
        Text(label, style = bodyStyle(13, Palette.Muted))
    }
}

@Composable
private fun SmallPill(text: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) { Text(text, style = bodyStyle(15, Color.White, bold = true)) }
}
