package fr.essentiel.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {

    private val resumeTick = mutableIntStateOf(0)
    private val homeTick = mutableIntStateOf(0)
    private val blocked = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Fonts.init(this)
        readBlocked(intent)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Palette.Accent,
                    background = Palette.Bg,
                    surface = Palette.Bg,
                    onSurface = Palette.Ink,
                )
            ) {
                EssentielApp(
                    resumeTick = resumeTick.intValue,
                    homeTick = homeTick.intValue,
                    blocked = blocked.value,
                    onBlocked = { blocked.value = it },
                    onDismissBlocked = { blocked.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Appui sur « Accueil » pendant qu'on est déjà sur le launcher : on revient à l'écran principal.
        if (!readBlocked(intent)) homeTick.intValue++
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
        LimitWatcherService.ensure(this)
        fr.essentiel.launcher.lock.LockService.ensure(this)
    }

    private fun readBlocked(i: Intent?): Boolean {
        val pkg = i?.getStringExtra(EXTRA_BLOCKED) ?: return false
        i.removeExtra(EXTRA_BLOCKED)
        blocked.value = pkg
        homeTick.intValue++
        return true
    }

    companion object {
        const val EXTRA_BLOCKED = "blocked_pkg"
    }
}

enum class Screen { HOME, APPS, SETTINGS }

@Composable
fun EssentielApp(
    resumeTick: Int,
    homeTick: Int,
    blocked: String?,
    onBlocked: (String) -> Unit,
    onDismissBlocked: () -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var screen by remember { mutableStateOf(Screen.HOME) }
    var pickerFor by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(homeTick) { screen = Screen.HOME; pickerFor = null }

    // Sur l'écran d'accueil, le bouton retour ne fait rien (comme un vrai launcher).
    BackHandler(enabled = true) {
        when {
            pickerFor != null -> pickerFor = null
            screen != Screen.HOME -> screen = Screen.HOME
        }
    }

    Box(Modifier.fillMaxSize()) {
        HomeScreen(
            prefs = prefs,
            resumeTick = resumeTick,
            onOpenApps = { screen = Screen.APPS },
            onOpenSettings = { screen = Screen.SETTINGS },
            onBlocked = onBlocked,
            onNeedPicker = { pickerFor = it; screen = Screen.SETTINGS },
        )
        AnimatedVisibility(
            visible = screen == Screen.APPS,
            enter = slideInVertically { it / 3 } + fadeIn(),
            exit = slideOutVertically { it / 3 } + fadeOut(),
        ) {
            AppsScreen(
                prefs = prefs,
                resumeTick = resumeTick,
                onClose = { screen = Screen.HOME },
                onOpenSettings = { screen = Screen.SETTINGS },
                onBlocked = onBlocked,
            )
        }
        AnimatedVisibility(
            visible = screen == Screen.SETTINGS,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            SettingsScreen(
                prefs = prefs,
                resumeTick = resumeTick,
                pickerFor = pickerFor,
                onPick = { pickerFor = it },
                onClose = { pickerFor = null; screen = Screen.HOME },
            )
        }
    }

    if (blocked != null) {
        val name = remember(blocked) { AppLauncher.label(ctx, blocked) }
        val limit = remember(blocked) { prefs.limitMinutes(blocked) }
        AlertDialog(
            onDismissRequest = onDismissBlocked,
            confirmButton = { TextButton(onClick = onDismissBlocked) { Text("OK") } },
            title = { Text("$name est bloquée") },
            text = {
                Text("Tu as atteint ta limite de $limit min pour aujourd'hui. Elle se débloque à minuit.")
            },
            containerColor = Palette.Bg,
        )
    }
}
