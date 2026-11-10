package fr.essentiel.launcher.clock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import fr.essentiel.launcher.EssentielTheme
import fr.essentiel.launcher.Fonts
import fr.essentiel.launcher.Icons
import fr.essentiel.launcher.LineIconView
import fr.essentiel.launcher.Palette
import fr.essentiel.launcher.PillButton
import fr.essentiel.launcher.bodyStyle
import fr.essentiel.launcher.paper
import fr.essentiel.launcher.serifStyle
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Écran plein écran quand un réveil ou le minuteur sonne (aussi sur l'écran verrouillé). */
class AlarmRingActivity : ComponentActivity() {

    private val stopped = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { finish() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Fonts.init(this)
        ContextCompat.registerReceiver(
            this, stopped, IntentFilter(AlarmRingService.ACTION_RING_STOPPED), ContextCompat.RECEIVER_NOT_EXPORTED
        )

        val label = intent.getStringExtra("label") ?: "Réveil"
        val isTimer = intent.getStringExtra("kind") == AlarmScheduler.KIND_TIMER

        setContent {
            EssentielTheme {
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
                Column(
                    Modifier.fillMaxSize().paper().padding(30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    LineIconView(if (isTimer) Icons.timer else Icons.bell, Palette.Accent, 56.dp)
                    Spacer(Modifier.height(24.dp))
                    Text(SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(now)), style = serifStyle(84))
                    Spacer(Modifier.height(8.dp))
                    Text(label, style = bodyStyle(22), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(60.dp))
                    PillButton("arrêter", modifier = Modifier.fillMaxWidth()) {
                        AlarmRingService.stop(this@AlarmRingActivity); finish()
                    }
                    if (!isTimer) {
                        Spacer(Modifier.height(14.dp))
                        PillButton("répéter dans 10 min", filled = false, modifier = Modifier.fillMaxWidth()) {
                            AlarmRingService.snooze(this@AlarmRingActivity, label); finish()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(stopped) } catch (e: Exception) { }
        super.onDestroy()
    }
}
