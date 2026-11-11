package fr.essentiel.launcher.browser

import android.content.Context
import android.net.Uri
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class Tab(val id: Long, startUrl: String) {
    var url by mutableStateOf(startUrl)
    var title by mutableStateOf("")
    var progress by mutableIntStateOf(100)
    var canBack by mutableStateOf(false)
    var canForward by mutableStateOf(false)
    var webView: WebView? = null
}

data class Site(val title: String, val url: String, val time: Long = 0)

enum class Engine(val label: String, val prefix: String) {
    GOOGLE("Google", "https://www.google.com/search?q="),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
    QWANT("Qwant", "https://www.qwant.com/?q="),
    ECOSIA("Ecosia", "https://www.ecosia.org/search?q="),
    BING("Bing", "https://www.bing.com/search?q="),
}

/** Onglets, favoris, historique et réglages du navigateur (gardés sur le téléphone). */
object BrowserData {
    val tabs = mutableStateListOf<Tab>()
    var current by mutableIntStateOf(0)
    val version = mutableIntStateOf(0)
    private var restored = false

    val SOCIAL = listOf(
        "facebook.com", "instagram.com", "tiktok.com", "twitter.com", "x.com", "snapchat.com",
        "threads.net", "pinterest.com", "fb.com", "messenger.com",
    )

    private fun sp(ctx: Context) = ctx.getSharedPreferences("navigateur", Context.MODE_PRIVATE)

    fun restoreTabs(ctx: Context) {
        if (restored) return
        restored = true
        val urls = sp(ctx).getString("onglets", "").orEmpty().split("\n").filter { it.isNotBlank() }
        if (urls.isEmpty()) tabs += Tab(System.nanoTime(), "")
        else urls.forEach { tabs += Tab(System.nanoTime(), it) }
        current = sp(ctx).getInt("onglet", 0).coerceIn(0, tabs.lastIndex)
    }

    fun saveTabs(ctx: Context) {
        sp(ctx).edit().putString("onglets", tabs.joinToString("\n") { it.url }).putInt("onglet", current).apply()
    }

    fun newTab(url: String = ""): Tab {
        val t = Tab(System.nanoTime(), url)
        tabs += t
        current = tabs.lastIndex
        return t
    }

    fun closeTab(i: Int) {
        if (i !in tabs.indices) return
        tabs[i].webView?.destroy()
        tabs.removeAt(i)
        if (tabs.isEmpty()) tabs += Tab(System.nanoTime(), "")
        current = current.coerceIn(0, tabs.lastIndex)
    }

    // ---------- Réglages ----------
    fun engine(ctx: Context): Engine = Engine.entries.firstOrNull { it.name == sp(ctx).getString("moteur", "GOOGLE") } ?: Engine.GOOGLE
    fun setEngine(ctx: Context, e: Engine) { sp(ctx).edit().putString("moteur", e.name).apply(); version.intValue++ }
    fun blockSocial(ctx: Context) = sp(ctx).getBoolean("bloquer_reseaux", true)
    fun setBlockSocial(ctx: Context, on: Boolean) { sp(ctx).edit().putBoolean("bloquer_reseaux", on).apply(); version.intValue++ }

    fun isBlocked(ctx: Context, url: String): Boolean {
        if (!blockSocial(ctx)) return false
        val host = try { Uri.parse(url).host.orEmpty().lowercase() } catch (e: Exception) { "" }
        return SOCIAL.any { host == it || host.endsWith(".$it") }
    }

    /** Ce qui est tapé dans la barre → adresse web ou recherche. */
    fun toUrl(ctx: Context, input: String): String {
        val t = input.trim()
        if (t.startsWith("http://") || t.startsWith("https://")) return t
        val looksLikeSite = !t.contains(' ') && t.contains('.') && !t.endsWith('.')
        return if (looksLikeSite) "https://$t" else engine(ctx).prefix + URLEncoder.encode(t, "UTF-8")
    }

    fun domain(url: String): String = try { Uri.parse(url).host.orEmpty().removePrefix("www.") } catch (e: Exception) { url }

    // ---------- Favoris & historique ----------
    private fun readSites(ctx: Context, key: String): List<Site> = try {
        val a = JSONArray(sp(ctx).getString(key, "[]"))
        (0 until a.length()).map { a.getJSONObject(it).let { o -> Site(o.optString("t"), o.getString("u"), o.optLong("d")) } }
    } catch (e: Exception) { emptyList() }

    private fun writeSites(ctx: Context, key: String, list: List<Site>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("t", it.title).put("u", it.url).put("d", it.time)) }
        sp(ctx).edit().putString(key, a.toString()).apply()
        version.intValue++
    }

    fun favorites(ctx: Context): List<Site> {
        val list = readSites(ctx, "favoris")
        return if (sp(ctx).contains("favoris")) list else listOf(
            Site("Wikipédia", "https://fr.wikipedia.org"),
            Site("Météo-France", "https://meteofrance.com"),
            Site("SNCF Connect", "https://www.sncf-connect.com"),
            Site("Le Monde", "https://www.lemonde.fr"),
        )
    }

    fun isFavorite(ctx: Context, url: String) = favorites(ctx).any { it.url.trimEnd('/') == url.trimEnd('/') }

    fun toggleFavorite(ctx: Context, title: String, url: String) {
        val list = favorites(ctx)
        writeSites(
            ctx, "favoris",
            if (list.any { it.url.trimEnd('/') == url.trimEnd('/') }) list.filterNot { it.url.trimEnd('/') == url.trimEnd('/') }
            else list + Site(title.ifBlank { domain(url) }, url)
        )
    }

    fun history(ctx: Context) = readSites(ctx, "historique")

    fun addHistory(ctx: Context, title: String, url: String) {
        if (url.isBlank() || url.startsWith("data:") || url.startsWith("about:")) return
        val list = listOf(Site(title, url, System.currentTimeMillis())) + history(ctx).filterNot { it.url == url }
        writeSites(ctx, "historique", list.take(300))
    }

    fun clearHistory(ctx: Context) = writeSites(ctx, "historique", emptyList())
}
