package fr.essentiel.launcher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.widget.Toast

/** Les 8 boutons de la grille. builtIn = écran d'une app intégrée à Essentiel (null = app du système). */
enum class Tile(val key: String, val label: String, val icon: LineIcon, val builtIn: String?) {
    EVENT("event", "Agenda", Icons.calendar, "calendar"),
    REMINDERS("todo", "Rappels", Icons.bell, "reminders"),
    TIMER("timer", "Minuteur", Icons.timer, null),
    GALLERY("gallery", "Galerie", Icons.image, "gallery"),
    CALL("call", "Appel", Icons.phone, "phone"),
    MESSAGE("message", "Message", Icons.message, "messages"),
    CAMERA("camera", "Photo", Icons.camera, "camera"),
    CONTACTS("contacts", "Contacts", Icons.users, "contacts"),
}

data class AppEntry(val pkg: String, val label: String)

object AppLauncher {

    /** Toutes les apps qui ont une icône dans le téléphone (pour les réglages). */
    fun installedApps(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.pkg != ctx.packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    fun label(ctx: Context, pkg: String): String = try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

    fun isInstalled(ctx: Context, pkg: String) =
        ctx.packageManager.getLaunchIntentForPackage(pkg) != null

    /** Vrai si l'app a dépassé sa limite du jour. */
    fun isBlocked(ctx: Context, prefs: Prefs, pkg: String): Boolean {
        val limit = prefs.limitMinutes(pkg)
        if (limit <= 0 || !Usage.hasPermission(ctx)) return false
        val used = Usage.todayUsageMs(ctx)[pkg] ?: 0L
        return used >= limit * 60_000L
    }

    fun launchPackage(ctx: Context, prefs: Prefs, pkg: String, onBlocked: (String) -> Unit) {
        if (isBlocked(ctx, prefs, pkg)) { onBlocked(pkg); return }
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg)
        if (i == null) {
            Toast.makeText(ctx, "Cette app n'est plus installée", Toast.LENGTH_SHORT).show(); return
        }
        start(ctx, i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Intention par défaut de chaque bouton : l'app intégrée, ou l'app du système pour le minuteur. */
    private fun defaultIntent(ctx: Context, tile: Tile): Intent? {
        tile.builtIn?.let { return ToolsActivity.intent(ctx, it) }
        val intent = Intent(AlarmClock.ACTION_SHOW_TIMERS)
        return intent.takeIf { it.resolveActivity(ctx.packageManager) != null }
    }

    /** Retourne false si aucune app n'est disponible pour ce bouton (il faut en choisir une). */
    fun launchTile(ctx: Context, prefs: Prefs, tile: Tile, onBlocked: (String) -> Unit): Boolean {
        val chosen = prefs.buttonTarget(tile.key)
        if (chosen != null && isInstalled(ctx, chosen)) {
            launchPackage(ctx, prefs, chosen, onBlocked); return true
        }
        val intent = defaultIntent(ctx, tile) ?: return false
        val target = intent.component?.packageName ?: intent.resolveActivity(ctx.packageManager)?.packageName
        if (target != null && target != "android" && target != ctx.packageName && isBlocked(ctx, prefs, target)) {
            onBlocked(target); return true
        }
        start(ctx, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    fun start(ctx: Context, intent: Intent) {
        try {
            ctx.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(ctx, "Impossible d'ouvrir cette app", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(ctx, "Impossible d'ouvrir cette app", Toast.LENGTH_SHORT).show()
        }
    }
}
