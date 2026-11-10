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
}
