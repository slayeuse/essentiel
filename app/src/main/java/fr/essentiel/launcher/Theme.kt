package fr.essentiel.launcher

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Typeface
import kotlin.random.Random

object Palette {
    val Bg = Color(0xFFE6E3DE)
    val Card = Color(0xFFE1DED9)
    val Line = Color(0xFFC9C6C1)
    val Accent = Color(0xFFF2390C)
    val Ink = Color(0xFF1B1B1B)
    val Muted = Color(0xFF6E6C69)
}

/**
 * Polices : Fraunces (titre) et Work Sans (texte), téléchargées par GitHub au moment
 * de compiler. Si elles manquent, on utilise les polices du système.
 */
object Fonts {
    var serif: FontFamily = FontFamily.Serif; private set
    var sans: FontFamily = FontFamily.SansSerif; private set
    var sansBold: FontFamily = FontFamily.SansSerif; private set

    fun init(ctx: Context) {
        load(ctx, "fonts/serif.ttf", "'wght' 320, 'opsz' 72, 'SOFT' 50, 'WONK' 0")?.let { serif = it }
        load(ctx, "fonts/sans.ttf", "'wght' 450")?.let { sans = it }
        load(ctx, "fonts/sans.ttf", "'wght' 600")?.let { sansBold = it }
    }

    private fun load(ctx: Context, path: String, variation: String): FontFamily? = try {
        ctx.assets.open(path).close()
        val tf = android.graphics.Typeface.Builder(ctx.assets, path)
            .setFontVariationSettings(variation)
            .build()
        tf?.let { FontFamily(Typeface(it)) }
    } catch (e: Exception) {
        null
    }
}

/** Léger grain « papier » comme sur la maquette. */
private val grainBitmap: ImageBitmap by lazy {
    val size = 160
    val r = Random(7)
    val px = IntArray(size * size) {
        val a = r.nextInt(0, 12)
        if (r.nextBoolean()) (a shl 24) else (a shl 24) or 0xFFFFFF
    }
    Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}

fun Modifier.paper(): Modifier = drawWithCache {
    val brush = ShaderBrush(ImageShader(grainBitmap, TileMode.Repeated, TileMode.Repeated))
    onDrawBehind {
        drawRect(Palette.Bg)
        drawRect(brush)
    }
}
