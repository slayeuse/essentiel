package fr.essentiel.launcher.map

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import org.json.JSONArray
import org.json.JSONObject

/** Lieux favoris et recherches récentes de la carte (gardés sur le téléphone). */
object MapPlaces {
    val version = mutableIntStateOf(0)

    private fun sp(ctx: Context) = ctx.getSharedPreferences("carte", Context.MODE_PRIVATE)

    private fun read(ctx: Context, key: String): List<Place> = try {
        val arr = JSONArray(sp(ctx).getString(key, "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Place(o.getString("name"), o.optString("address"), o.getDouble("lat"), o.getDouble("lon"), o.optString("category"))
        }
    } catch (e: Exception) { emptyList() }

    private fun write(ctx: Context, key: String, list: List<Place>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("name", it.name).put("address", it.address)
                    .put("lat", it.lat).put("lon", it.lon).put("category", it.category)
            )
        }
        sp(ctx).edit().putString(key, arr.toString()).apply()
        version.intValue = version.intValue + 1
    }

    private fun same(a: Place, b: Place) =
        MapApi.distance(a.lat, a.lon, b.lat, b.lon) < 15 && a.name == b.name

    fun favorites(ctx: Context) = read(ctx, "favoris")
    fun recents(ctx: Context) = read(ctx, "recents")

    fun isFavorite(ctx: Context, p: Place) = favorites(ctx).any { same(it, p) }

    fun toggleFavorite(ctx: Context, p: Place) {
        val list = favorites(ctx)
        write(ctx, "favoris", if (list.any { same(it, p) }) list.filterNot { same(it, p) } else list + p)
    }

    fun addRecent(ctx: Context, p: Place) {
        write(ctx, "recents", (listOf(p) + recents(ctx).filterNot { same(it, p) }).take(8))
    }

    fun clearRecents(ctx: Context) = write(ctx, "recents", emptyList())

    // ---------- Réglages de la carte ----------
    fun voice(ctx: Context) = sp(ctx).getBoolean("voix", true)
    fun setVoice(ctx: Context, on: Boolean) { sp(ctx).edit().putBoolean("voix", on).apply(); version.intValue++ }

    /** Clé collée dans l'app, sinon celle fournie à la compilation (secret GitHub TOMTOM_KEY). */
    fun tomtomKey(ctx: Context): String =
        sp(ctx).getString("tomtom", "").orEmpty().ifBlank { fr.essentiel.launcher.BuildConfig.TOMTOM_KEY }
    fun setTomtomKey(ctx: Context, key: String) { sp(ctx).edit().putString("tomtom", key.trim()).apply(); version.intValue++ }

    fun showTraffic(ctx: Context) = sp(ctx).getBoolean("trafic", true)
    fun setShowTraffic(ctx: Context, on: Boolean) { sp(ctx).edit().putBoolean("trafic", on).apply(); version.intValue++ }

    fun avoid(ctx: Context): Set<Avoid> =
        sp(ctx).getString("eviter", "").orEmpty().split(",").mapNotNull { n -> Avoid.entries.firstOrNull { it.name == n } }.toSet()
    fun setAvoid(ctx: Context, set: Set<Avoid>) {
        sp(ctx).edit().putString("eviter", set.joinToString(",") { it.name }).apply(); version.intValue++
    }
}
