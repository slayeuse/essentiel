package fr.essentiel.launcher

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

/**
 * Icônes des apps installées (Play Store) :
 * - par défaut, l'icône d'origine de l'app (mise automatiquement) ;
 * - on peut la remplacer par une icône au trait Essentiel ou par l'initiale.
 */
object AppIcons {
    val version = mutableIntStateOf(0)

    /** Icônes au trait proposées (nom → dessin). */
    val LINE: Map<String, LineIcon> = linkedMapOf(
        "phone" to Icons.phone, "message" to Icons.message, "mail" to Icons.mail, "users" to Icons.users,
        "camera" to Icons.camera, "image" to Icons.image, "video" to Icons.video, "music" to Icons.music,
        "mic" to Icons.mic, "map" to Icons.map, "train" to Icons.train, "calendar" to Icons.calendar,
        "clock" to Icons.clock, "bell" to Icons.bell, "note" to Icons.note, "list" to Icons.list,
        "book" to Icons.book, "globe" to Icons.globe, "cart" to Icons.cart, "card" to Icons.card,
        "home" to Icons.home, "heart" to Icons.heart, "star" to Icons.star, "gear" to Icons.gear,
        "search" to Icons.search, "send" to Icons.send, "share" to Icons.share, "sun" to Icons.sun,
        "flash" to Icons.flash, "sparkles" to Icons.sparkles,
    )

    private fun sp(ctx: Context) = ctx.getSharedPreferences("essentiel", Context.MODE_PRIVATE)

    /** "orig", "letter" ou "line:<nom>". */
    fun choice(ctx: Context, pkg: String): String = sp(ctx).getString("icon_$pkg", "orig") ?: "orig"

    fun setChoice(ctx: Context, pkg: String, value: String) {
        sp(ctx).edit().putString("icon_$pkg", value).apply()
        version.intValue = version.intValue + 1
    }

    private val cache = HashMap<String, ImageBitmap?>()

    fun original(ctx: Context, pkg: String): ImageBitmap? = cache.getOrPut(pkg) {
        try {
            ctx.packageManager.getApplicationIcon(pkg).toBitmap(160, 160).asImageBitmap()
        } catch (e: Exception) { null }
    }
}

/** Affiche l'icône d'une app installée selon le choix de l'utilisateur. */
@Composable
fun AppIconView(pkg: String, size: Dp, choiceOverride: String? = null) {
    val ctx = LocalContext.current
    val v = AppIcons.version.intValue
    val choice = choiceOverride ?: remember(pkg, v) { AppIcons.choice(ctx, pkg) }
    when {
        choice.startsWith("line:") -> {
            val icon = AppIcons.LINE[choice.removePrefix("line:")] ?: Icons.star
            LineIconView(icon, Palette.Accent, size)
        }
        choice == "letter" -> {
            val label = remember(pkg) { AppLauncher.label(ctx, pkg) }
            Box(Modifier.size(size), contentAlignment = Alignment.Center) {
                Text(
                    label.firstOrNull()?.uppercase() ?: "?",
                    style = TextStyle(fontFamily = Fonts.serif, fontSize = (size.value * 0.95f).sp, color = Palette.Accent)
                )
            }
        }
        else -> {
            val bmp = remember(pkg) { AppIcons.original(ctx, pkg) }
            if (bmp != null) {
                Image(bmp, contentDescription = null, modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)))
            } else LineIconView(Icons.star, Palette.Accent, size)
        }
    }
}
