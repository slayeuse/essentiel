package fr.essentiel.launcher

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Tous les réglages du launcher, stockés sur le téléphone. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("essentiel", Context.MODE_PRIVATE)

    // ---------- Liste « mes apps » (ouverte en glissant vers le haut) ----------
    var myApps: List<String>
        get() = sp.getString("my_apps", "").orEmpty().split(",").filter { it.isNotBlank() }
        set(value) = sp.edit().putString("my_apps", value.joinToString(",")).apply()

    fun toggleMyApp(pkg: String) {
        val list = myApps.toMutableList()
        if (!list.remove(pkg)) list.add(pkg)
        myApps = list
    }

    // ---------- Les 8 boutons : app choisie (null = app du système par défaut) ----------
    fun buttonTarget(key: String): String? = sp.getString("btn_$key", null)

    fun setButtonTarget(key: String, pkg: String?) {
        val e = sp.edit()
        if (pkg == null) e.remove("btn_$key") else e.putString("btn_$key", pkg)
        e.apply()
    }

    // ---------- Cases de l'écran d'accueil (favoris) ----------
    // Chaque case : "builtin:<id>" (app Essentiel), un nom de paquet (app installée) ou "" (vide).
    var tileCount: Int
        get() = sp.getInt("tile_count", 8).let { if (it in listOf(4, 8, 12)) it else 8 }
        set(value) = sp.edit().putInt("tile_count", value).apply()

    fun slots(): MutableList<String> {
        val stored = sp.getString("slots", null)
        val list = if (stored != null) stored.split("|").toMutableList() else
            // première fois : on reprend les 8 boutons d'origine (et les choix déjà faits)
            Tile.entries.map { t -> buttonTarget(t.key) ?: (AppLauncher.BUILTIN_PREFIX + t.builtIn) }.toMutableList()
        val extras = listOf(AppLauncher.BUILTIN_PREFIX + "memos", AppLauncher.BUILTIN_PREFIX + "map", "", "")
        var i = 0
        while (list.size < 12) { list.add(extras.getOrElse(i) { "" }); i++ }
        return list.take(12).toMutableList()
    }

    fun setSlots(list: List<String>) {
        sp.edit().putString("slots", list.joinToString("|")).apply()
    }

    fun setSlot(index: Int, value: String) {
        val l = slots(); if (index in l.indices) { l[index] = value; setSlots(l) }
    }

    fun moveSlot(from: Int, to: Int) {
        val l = slots()
        if (from !in l.indices || to !in l.indices) return
        val v = l.removeAt(from); l.add(to, v); setSlots(l)
    }

    // ---------- Écran de verrouillage ----------
    var lockScreen: Boolean
        get() = sp.getBoolean("lock_screen", false)
        set(value) = sp.edit().putBoolean("lock_screen", value).apply()

    // ---------- Limites de temps (minutes par jour, 0 = aucune) ----------
    fun limitMinutes(pkg: String): Int {
        applyPending(pkg)
        return sp.getInt("limit_$pkg", 0)
    }

    /** Limite en attente (prendra effet demain), ou null. */
    fun pendingLimit(pkg: String): Int? =
        sp.getString("pending_$pkg", null)?.substringBefore("|")?.toIntOrNull()

    fun allLimits(): Map<String, Int> {
        val pkgs = sp.all.keys
            .filter { it.startsWith("limit_") || it.startsWith("pending_") }
            .map { it.substringAfter("_") }
            .toSet()
        return pkgs.associateWith { limitMinutes(it) }.filterValues { it > 0 }
    }

    /**
     * Blocage strict : rendre une limite plus sévère s'applique tout de suite,
     * l'assouplir (augmenter ou retirer) ne s'applique que demain.
     * Retourne true si appliqué immédiatement.
     */
    fun setLimit(pkg: String, minutes: Int): Boolean {
        val current = limitMinutes(pkg)
        val stricter = minutes > 0 && (current == 0 || minutes <= current)
        val e = sp.edit()
        return if (stricter || minutes == current) {
            e.putInt("limit_$pkg", minutes).remove("pending_$pkg").apply()
            true
        } else {
            e.putString("pending_$pkg", "$minutes|${dayKey(1)}").apply()
            false
        }
    }

    private fun applyPending(pkg: String) {
        val raw = sp.getString("pending_$pkg", null) ?: return
        val minutes = raw.substringBefore("|").toIntOrNull() ?: 0
        val day = raw.substringAfter("|").toIntOrNull() ?: 0
        if (dayKey(0) >= day) {
            val e = sp.edit().remove("pending_$pkg")
            if (minutes > 0) e.putInt("limit_$pkg", minutes) else e.remove("limit_$pkg")
            e.apply()
        }
    }

    private fun dayKey(offsetDays: Int): Int {
        val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offsetDays) }
        return SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(c.timeInMillis)).toInt()
    }

    // ---------- Cache météo ----------
    fun saveWeather(w: Weather.Now) {
        sp.edit()
            .putInt("w_temp", w.temp)
            .putInt("w_code", w.code)
            .putBoolean("w_day", w.isDay)
            .putLong("w_time", System.currentTimeMillis())
            .apply()
    }

    fun cachedWeather(): Weather.Now? {
        if (!sp.contains("w_time")) return null
        return Weather.Now(sp.getInt("w_temp", 0), sp.getInt("w_code", 0), sp.getBoolean("w_day", true))
    }

    fun weatherAgeMs(): Long = System.currentTimeMillis() - sp.getLong("w_time", 0)
}
