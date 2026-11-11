package fr.essentiel.launcher.keyboard

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf

/** Réglages du clavier Essentiel. */
object KbPrefs {
    val version = mutableIntStateOf(0)
    private fun sp(ctx: Context) = ctx.getSharedPreferences("clavier_reglages", Context.MODE_PRIVATE)

    fun vibration(ctx: Context) = sp(ctx).getBoolean("vibration", true)
    fun numberRow(ctx: Context) = sp(ctx).getBoolean("chiffres", false)
    fun accentFix(ctx: Context) = sp(ctx).getBoolean("accents", true)
    fun doubleSpace(ctx: Context) = sp(ctx).getBoolean("double_espace", true)
    fun tall(ctx: Context) = sp(ctx).getBoolean("grand", false)

    fun set(ctx: Context, key: String, value: Boolean) {
        sp(ctx).edit().putBoolean(key, value).apply()
        version.intValue = version.intValue + 1
    }
}
