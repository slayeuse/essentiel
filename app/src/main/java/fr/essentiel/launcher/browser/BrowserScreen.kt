package fr.essentiel.launcher.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import fr.essentiel.launcher.Divider
import fr.essentiel.launcher.EmptyText
import fr.essentiel.launcher.Icons
import fr.essentiel.launcher.LineIcon
import fr.essentiel.launcher.LineIconView
import fr.essentiel.launcher.ListRow
import fr.essentiel.launcher.Nav
import fr.essentiel.launcher.Palette
import fr.essentiel.launcher.PillButton
import fr.essentiel.launcher.SectionTitle
import fr.essentiel.launcher.SegmentedTabs
import fr.essentiel.launcher.bodyStyle
import fr.essentiel.launcher.paper
import fr.essentiel.launcher.serifStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Sheet { NONE, TABS, BOOKMARKS, MENU }

/** Conteneur pour le choix de fichier demandé par une page (formulaire d'envoi). */
private class UploadHolder { var callback: ValueCallback<Array<Uri>>? = null }

@Composable
fun BrowserScreen(nav: Nav, startUrl: String = "") {
    val ctx = LocalContext.current
    val version by BrowserData.version
    var editing by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(Sheet.NONE) }
    val upload = remember { UploadHolder() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val data = res.data
        val uris: Array<Uri>? = if (res.resultCode != Activity.RESULT_OK || data == null) null else {
            val clip = data.clipData
            if (clip != null) Array(clip.itemCount) { clip.getItemAt(it).uri }
            else data.data?.let { arrayOf(it) }
        }
        upload.callback?.onReceiveValue(uris)
        upload.callback = null
    }

    remember { BrowserData.restoreTabs(ctx); 0 }
    LaunchedEffect(startUrl) {
        if (startUrl.isNotBlank()) open(ctx, BrowserData.newTab(), startUrl)
    }
    DisposableEffect(Unit) {
        onDispose {
            BrowserData.saveTabs(ctx)
            // Les pages sont rechargées à la prochaine ouverture (évite de garder l'écran en mémoire).
            BrowserData.tabs.forEach { t -> t.webView?.let { (it.parent as? ViewGroup)?.removeView(it); it.destroy() }; t.webView = null }
        }
    }

    val tab = BrowserData.tabs.getOrNull(BrowserData.current) ?: return

    BackHandler(enabled = editing || sheet != Sheet.NONE || tab.canBack) {
        when {
            sheet != Sheet.NONE -> sheet = Sheet.NONE
            editing -> editing = false
            else -> tab.webView?.goBack()
        }
    }

    Box(Modifier.fillMaxSize().paper()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (tab.url.isBlank()) {
                    StartPage(version, onOpen = { open(ctx, tab, it) }, onSearch = { editing = true })
                } else {
                    key(tab.id) { WebPage(tab, upload) { intent -> try { picker.launch(intent) } catch (e: Exception) { upload.callback?.onReceiveValue(null); upload.callback = null } } }
                }
            }
            BottomBar(
                tab = tab,
                onAddress = { editing = true },
                onReload = { if (tab.progress < 100) tab.webView?.stopLoading() else tab.webView?.reload() },
                onBack = { tab.webView?.goBack() },
                onForward = { tab.webView?.goForward() },
                onShare = {
                    if (tab.url.isNotBlank()) {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, tab.url)
                        ctx.startActivity(Intent.createChooser(send, "Partager"))
                    }
                },
                onBookmarks = { sheet = Sheet.BOOKMARKS },
                onTabs = { sheet = Sheet.TABS },
                onMenu = { sheet = Sheet.MENU },
            )
        }

        if (editing) {
            AddressEditor(
                initial = tab.url,
                version = version,
                onCancel = { editing = false },
                onGo = { input -> editing = false; open(ctx, tab, BrowserData.toUrl(ctx, input)) },
            )
        }
        when (sheet) {
            Sheet.TABS -> TabsOverview(onClose = { sheet = Sheet.NONE }, onNew = { BrowserData.newTab(); sheet = Sheet.NONE; editing = true })
            Sheet.BOOKMARKS -> BookmarksSheet(version, onClose = { sheet = Sheet.NONE }) { sheet = Sheet.NONE; open(ctx, tab, it) }
            Sheet.MENU -> MenuSheet(tab, version, onClose = { sheet = Sheet.NONE })
            Sheet.NONE -> {}
        }
    }
}

/** Ouvre une adresse dans l'onglet (ou affiche la page « bloqué »). */
private fun open(ctx: Context, tab: Tab, url: String) {
    if (BrowserData.isBlocked(ctx, url)) {
        tab.url = url
        tab.title = "Bloqué"
        tab.webView?.loadDataWithBaseURL(null, blockedHtml(BrowserData.domain(url)), "text/html", "utf-8", null)
            ?: run { tab.url = url }
        return
    }
    tab.url = url
    tab.webView?.loadUrl(url)
}

private fun blockedHtml(domain: String) = """
<html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
<body style="background:#E6E3DE;color:#1B1B1B;font-family:serif;display:flex;flex-direction:column;justify-content:center;align-items:center;height:90vh;text-align:center;padding:24px">
<div style="font-size:54px;color:#F2390C">●</div>
<h1 style="font-weight:400;font-size:34px;margin:10px 0">Pas maintenant.</h1>
<p style="font-family:sans-serif;color:#6E6C69;font-size:17px;line-height:1.5">$domain est un réseau social.<br>Essentiel le bloque pour vous aider à rester concentré.<br><br>Vous pouvez le réactiver dans le menu ••• du navigateur.</p>
</body></html>
""".trimIndent()

// ---------------------------------------------------------------- Page web

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebPage(tab: Tab, upload: UploadHolder, launchPicker: (Intent) -> Unit) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { c ->
            val existing = tab.webView
            if (existing != null) {
                (existing.parent as? ViewGroup)?.removeView(existing)
                return@AndroidView existing
            }
            WebView(c).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(0xFFE6E3DE.toInt())
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.setSupportMultipleWindows(false)
                settings.mediaPlaybackRequiresUserGesture = true
                settings.setGeolocationEnabled(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val u = request.url.toString()
                        val scheme = request.url.scheme.orEmpty()
                        if (scheme != "http" && scheme != "https") {
                            openExternal(c, u)
                            return true
                        }
                        if (request.isForMainFrame && BrowserData.isBlocked(c, u)) {
                            tab.url = u
                            tab.title = "Bloqué"
                            view.loadDataWithBaseURL(null, blockedHtml(BrowserData.domain(u)), "text/html", "utf-8", null)
                            return true
                        }
                        return false
                    }

                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                        if (url != null && !url.startsWith("data:") && !url.startsWith("about:")) tab.url = url
                        tab.canBack = view.canGoBack(); tab.canForward = view.canGoForward()
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        tab.canBack = view.canGoBack(); tab.canForward = view.canGoForward()
                        if (url != null && !url.startsWith("data:") && !url.startsWith("about:")) {
                            tab.title = view.title.orEmpty()
                            BrowserData.addHistory(c, tab.title, url)
                        }
                    }

                    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                        if (url != null && !url.startsWith("data:") && !url.startsWith("about:")) tab.url = url
                        tab.canBack = view.canGoBack(); tab.canForward = view.canGoForward()
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView, newProgress: Int) { tab.progress = newProgress }
                    override fun onReceivedTitle(view: WebView, title: String?) { tab.title = title.orEmpty() }
                    override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                        callback?.invoke(origin, true, false)
                    }
                    override fun onShowFileChooser(
                        webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: WebChromeClient.FileChooserParams?,
                    ): Boolean {
                        upload.callback?.onReceiveValue(null)
                        upload.callback = filePathCallback
                        val intent = try { fileChooserParams?.createIntent() } catch (e: Exception) { null }
                            ?: Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                        if (fileChooserParams?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                        launchPicker(intent)
                        return true
                    }
                }

                setDownloadListener { url, userAgent, disposition, mime, _ ->
                    download(c, url, userAgent, disposition, mime)
                }

                tab.webView = this
                val u = tab.url
                if (BrowserData.isBlocked(c, u)) loadDataWithBaseURL(null, blockedHtml(BrowserData.domain(u)), "text/html", "utf-8", null)
                else loadUrl(u)
            }
        },
    )
}

private fun openExternal(ctx: Context, url: String) {
    try {
        val intent = if (url.startsWith("intent:")) Intent.parseUri(url, Intent.URI_INTENT_SCHEME) else Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(ctx, "Aucune app pour ouvrir ce lien", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(ctx, "Lien impossible à ouvrir", Toast.LENGTH_SHORT).show()
    }
}

private fun download(ctx: Context, url: String, userAgent: String?, disposition: String?, mime: String?) {
    try {
        val name = URLUtil.guessFileName(url, disposition, mime)
        val req = DownloadManager.Request(Uri.parse(url))
            .setMimeType(mime)
            .addRequestHeader("User-Agent", userAgent.orEmpty())
            .addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url).orEmpty())
            .setTitle(name)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
        (ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
        Toast.makeText(ctx, "Téléchargement de $name…", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(ctx, "Téléchargement impossible", Toast.LENGTH_SHORT).show()
    }
}

// ---------------------------------------------------------------- Barre du bas (façon Safari)

@Composable
private fun BottomBar(
    tab: Tab,
    onAddress: () -> Unit,
    onReload: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onShare: () -> Unit,
    onBookmarks: () -> Unit,
    onTabs: () -> Unit,
    onMenu: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(Palette.Bg)) {
        // Barre de progression orange
        Box(Modifier.fillMaxWidth().height(2.dp)) {
            if (tab.progress in 1..99 && tab.url.isNotBlank()) {
                Box(Modifier.fillMaxWidth(tab.progress / 100f).fillMaxHeight().background(Palette.Accent))
            }
        }
        Divider()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val shape = RoundedCornerShape(50)
            Row(
                Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(shape)
                    .background(Palette.Card)
                    .border(1.dp, Palette.Line, shape)
                    .clickable(onClick = onAddress)
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val secure = tab.url.startsWith("https://")
                if (tab.url.isBlank()) {
                    LineIconView(Icons.search, Palette.Muted, 18.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Rechercher ou saisir une adresse", style = bodyStyle(16, Palette.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                } else {
                    if (secure) { LineIconView(Icons.lock, Palette.Muted, 14.dp); Spacer(Modifier.width(6.dp)) }
                    Text(
                        BrowserData.domain(tab.url), style = bodyStyle(16, bold = true), maxLines = 1,
                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
                    )
                    Box(Modifier.size(38.dp).clip(CircleShape).clickable(onClick = onReload), contentAlignment = Alignment.Center) {
                        LineIconView(if (tab.progress < 100) Icons.close else Icons.rotate, Palette.Ink, 18.dp)
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp).padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarButton(Icons.back, tab.canBack, onBack)
            BarButton(Icons.chevronRight, tab.canForward, onForward)
            BarButton(Icons.share, tab.url.isNotBlank(), onShare)
            BarButton(Icons.book, true, onBookmarks)
            Box(contentAlignment = Alignment.Center) {
                BarButton(Icons.tabs, true, onTabs)
                Text(
                    "${BrowserData.tabs.size}", style = bodyStyle(10, Palette.Accent, bold = true),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            BarButton(Icons.more, true, onMenu)
        }
    }
}

@Composable
private fun BarButton(icon: LineIcon, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { LineIconView(icon, if (enabled) Palette.Accent else Palette.Line, 24.dp) }
}

// ---------------------------------------------------------------- Page d'accueil

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StartPage(version: Int, onOpen: (String) -> Unit, onSearch: () -> Unit) {
    val ctx = LocalContext.current
    val favorites = remember(version) { BrowserData.favorites(ctx) }
    val recent = remember(version) { BrowserData.history(ctx).take(6) }
    val engine = remember(version) { BrowserData.engine(ctx) }
    var removing by remember { mutableStateOf<Site?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(40.dp))
        Text("Web", style = serifStyle(54))
        Text("Cherchez, lisez, refermez.", style = bodyStyle(15, Palette.Muted))
        Spacer(Modifier.height(22.dp))
        val shape = RoundedCornerShape(50)
        Row(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(shape)
                .background(Palette.Card)
                .border(1.dp, Palette.Line, shape)
                .clickable(onClick = onSearch)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LineIconView(Icons.search, Palette.Accent, 20.dp)
            Spacer(Modifier.width(10.dp))
            Text("Rechercher avec ${engine.label}", style = bodyStyle(17, Palette.Muted))
        }

        SectionTitle("Favoris", Modifier.padding(top = 18.dp))
        if (favorites.isEmpty()) {
            Text("Touchez ★ dans le menu ••• d'une page pour l'ajouter ici.", style = bodyStyle(15, Palette.Muted))
        } else {
            favorites.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    row.forEach { s ->
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .combinedClickable(onClick = { onOpen(s.url) }, onLongClick = { removing = s })
                                .padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            SiteIcon(s, 56)
                            Spacer(Modifier.height(6.dp))
                            Text(s.title, style = bodyStyle(12), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        if (recent.isNotEmpty()) {
            SectionTitle("Récemment consulté", Modifier.padding(top = 18.dp))
            recent.forEach { s ->
                ListRow(
                    title = s.title.ifBlank { BrowserData.domain(s.url) },
                    subtitle = BrowserData.domain(s.url),
                    leading = { SiteIcon(s, 36) },
                ) { onOpen(s.url) }
            }
        }
        Spacer(Modifier.height(30.dp))
    }

    val r = removing
    if (r != null) {
        ConfirmBox(
            text = "Retirer « ${r.title} » des favoris ?",
            confirm = "Retirer",
            onDismiss = { removing = null },
            onConfirm = { BrowserData.toggleFavorite(ctx, r.title, r.url); removing = null },
        )
    }
}

@Composable
private fun SiteIcon(s: Site, size: Int) {
    val domain = BrowserData.domain(s.url)
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size / 3.5f).dp)).background(Palette.Card).border(1.dp, Palette.Line, RoundedCornerShape((size / 3.5f).dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            (s.title.ifBlank { domain }).take(1).uppercase(),
            style = serifStyle((size * 0.42f).toInt(), Palette.Accent)
        )
        AsyncImage(
            model = "https://www.google.com/s2/favicons?domain=$domain&sz=128",
            contentDescription = null,
            modifier = Modifier.size((size * 0.5f).dp),
        )
    }
}

@Composable
private fun ConfirmBox(text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0x66000000)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(30.dp).clip(RoundedCornerShape(24.dp)).background(Palette.Bg).clickable(enabled = false) {}.padding(22.dp)
        ) {
            Text(text, style = bodyStyle(17))
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                PillButton("Annuler", filled = false, onClick = onDismiss)
                Spacer(Modifier.width(10.dp))
                PillButton(confirm, onClick = onConfirm)
            }
        }
    }
}

// ---------------------------------------------------------------- Saisie d'adresse + suggestions

@Composable
private fun AddressEditor(initial: String, version: Int, onCancel: () -> Unit, onGo: (String) -> Unit) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    val kb = LocalSoftwareKeyboardController.current
    var online by remember { mutableStateOf<List<String>>(emptyList()) }
    val sites = remember(version) { (BrowserData.favorites(ctx) + BrowserData.history(ctx)).distinctBy { it.url } }
    val q = text.trim()
    val local = remember(q, sites) {
        if (q.isEmpty() || q == initial) emptyList()
        else sites.filter { it.title.contains(q, true) || it.url.contains(q, true) }.take(4)
    }

    LaunchedEffect(Unit) { delay(80); try { focus.requestFocus() } catch (e: Exception) {}; kb?.show() }
    LaunchedEffect(q) {
        if (q.length < 2 || q == initial) { online = emptyList(); return@LaunchedEffect }
        delay(220)
        online = withContext(Dispatchers.IO) { suggestions(q) }
    }

    Column(Modifier.fillMaxSize().paper()) {
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            val shape = RoundedCornerShape(50)
            Row(
                Modifier.weight(1f).height(50.dp).clip(shape).background(Palette.Card).border(1.dp, Palette.Accent, shape).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LineIconView(Icons.search, Palette.Accent, 18.dp)
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = bodyStyle(17),
                    cursorBrush = SolidColor(Palette.Accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { if (q.isNotEmpty()) onGo(q) }),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    decorationBox = { inner ->
                        Box {
                            if (text.isEmpty()) Text("Rechercher ou saisir une adresse", style = bodyStyle(17, Palette.Muted), maxLines = 1)
                            inner()
                        }
                    }
                )
                if (text.isNotEmpty()) {
                    Box(Modifier.size(30.dp).clip(CircleShape).clickable { text = "" }, contentAlignment = Alignment.Center) {
                        LineIconView(Icons.close, Palette.Muted, 16.dp)
                    }
                }
            }
            Text(
                "Annuler", style = bodyStyle(16, Palette.Accent, bold = true),
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onCancel).padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            if (q.isNotEmpty()) {
                item {
                    ListRow(
                        title = q,
                        subtitle = if (BrowserData.toUrl(ctx, q).startsWith(BrowserData.engine(ctx).prefix)) "Rechercher avec ${BrowserData.engine(ctx).label}" else "Ouvrir le site",
                        leading = { RoundIcon(Icons.search) },
                    ) { onGo(q) }
                }
            }
            items(online.filter { it != q }.take(6)) { s ->
                ListRow(title = s, leading = { RoundIcon(Icons.search, muted = true) }) { onGo(s) }
            }
            if (local.isNotEmpty()) {
                item { SectionTitle("Favoris et historique") }
                items(local) { s ->
                    ListRow(title = s.title.ifBlank { BrowserData.domain(s.url) }, subtitle = s.url, leading = { SiteIcon(s, 36) }) { onGo(s.url) }
                }
            }
            if (q.isEmpty()) {
                item { Spacer(Modifier.height(10.dp)) }
                item { Text("Tapez un mot pour chercher, ou une adresse comme wikipedia.org.", style = bodyStyle(15, Palette.Muted)) }
            }
        }
    }
}

@Composable
private fun RoundIcon(icon: LineIcon, muted: Boolean = false) {
    Box(Modifier.size(36.dp).clip(CircleShape).background(Palette.Card), contentAlignment = Alignment.Center) {
        LineIconView(icon, if (muted) Palette.Muted else Palette.Accent, 18.dp)
    }
}

/** Suggestions de recherche (service public de Google, sans compte). */
private fun suggestions(q: String): List<String> = try {
    val url = URL("https://suggestqueries.google.com/complete/search?client=firefox&hl=fr&q=" + URLEncoder.encode(q, "UTF-8"))
    val conn = url.openConnection() as HttpURLConnection
    conn.connectTimeout = 3000; conn.readTimeout = 3000
    val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    conn.disconnect()
    val arr = JSONArray(body).getJSONArray(1)
    (0 until arr.length()).map { arr.getString(it) }
} catch (e: Exception) { emptyList() }

// ---------------------------------------------------------------- Onglets

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabsOverview(onClose: () -> Unit, onNew: () -> Unit) {
    Column(Modifier.fillMaxSize().paper().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Onglets", style = serifStyle(38), modifier = Modifier.weight(1f))
            Text("${BrowserData.tabs.size}", style = bodyStyle(16, Palette.Muted))
        }
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(BrowserData.tabs, key = { _, t -> t.id }) { i, t ->
                val selected = i == BrowserData.current
                val shape = RoundedCornerShape(22.dp)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(shape)
                        .background(Palette.Card)
                        .border(if (selected) 2.dp else 1.dp, if (selected) Palette.Accent else Palette.Line, shape)
                        .clickable { BrowserData.current = i; onClose() }
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            t.title.ifBlank { if (t.url.isBlank()) "Nouvel onglet" else BrowserData.domain(t.url) },
                            style = bodyStyle(15, bold = true), maxLines = 3, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).background(Palette.Bg).clickable { BrowserData.closeTab(i) },
                            contentAlignment = Alignment.Center,
                        ) { LineIconView(Icons.close, Palette.Ink, 14.dp) }
                    }
                    Spacer(Modifier.weight(1f))
                    if (t.url.isNotBlank()) SiteIcon(Site(t.title, t.url), 34)
                    else LineIconView(Icons.globe, Palette.Muted, 30.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(if (t.url.isBlank()) "Page d'accueil" else BrowserData.domain(t.url), style = bodyStyle(13, Palette.Muted), maxLines = 1)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Tout fermer", style = bodyStyle(16, Palette.Muted, bold = true),
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable {
                    while (BrowserData.tabs.size > 1) BrowserData.closeTab(BrowserData.tabs.lastIndex)
                    BrowserData.closeTab(0)
                    onClose()
                }.padding(10.dp)
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(54.dp).clip(CircleShape).background(Palette.Accent).clickable(onClick = onNew),
                contentAlignment = Alignment.Center,
            ) { LineIconView(Icons.plus, Color.White, 26.dp, strokeWidth = 2.4f) }
            Spacer(Modifier.weight(1f))
            Text(
                "Terminé", style = bodyStyle(16, Palette.Accent, bold = true),
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClose).padding(10.dp)
            )
        }
    }
}

// ---------------------------------------------------------------- Favoris / historique

@Composable
private fun BookmarksSheet(version: Int, onClose: () -> Unit, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    var tabIndex by remember { mutableStateOf(0) }
    val favorites = remember(version) { BrowserData.favorites(ctx) }
    val history = remember(version) { BrowserData.history(ctx) }
    val day = remember { SimpleDateFormat("EEEE d MMMM", Locale.FRENCH) }
    val time = remember { SimpleDateFormat("HH:mm", Locale.FRENCH) }

    Column(Modifier.fillMaxSize().paper().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (tabIndex == 0) "Favoris" else "Historique", style = serifStyle(38), modifier = Modifier.weight(1f))
            Text(
                "Terminé", style = bodyStyle(16, Palette.Accent, bold = true),
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClose).padding(10.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        SegmentedTabs(listOf("Favoris", "Historique"), tabIndex, Modifier.fillMaxWidth()) { tabIndex = it }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f)) {
            if (tabIndex == 0) {
                if (favorites.isEmpty()) item { EmptyText("Aucun favori. Ouvrez une page puis ••• → Ajouter aux favoris.") }
                items(favorites, key = { "f" + it.url }) { s ->
                    ListRow(
                        title = s.title, subtitle = BrowserData.domain(s.url), leading = { SiteIcon(s, 36) },
                        trailing = {
                            Box(Modifier.size(36.dp).clip(CircleShape).clickable { BrowserData.toggleFavorite(ctx, s.title, s.url) }, contentAlignment = Alignment.Center) {
                                LineIconView(Icons.trash, Palette.Muted, 18.dp)
                            }
                        },
                    ) { onOpen(s.url) }
                }
            } else {
                if (history.isEmpty()) item { EmptyText("L'historique est vide.") }
                var last = ""
                history.forEach { s ->
                    val d = day.format(Date(s.time)).replaceFirstChar { it.uppercase() }
                    if (d != last) {
                        last = d
                        item(key = "d" + d + s.time) { SectionTitle(d) }
                    }
                    item(key = "h" + s.url + s.time) {
                        ListRow(
                            title = s.title.ifBlank { BrowserData.domain(s.url) },
                            subtitle = time.format(Date(s.time)) + " · " + BrowserData.domain(s.url),
                            leading = { SiteIcon(s, 36) },
                        ) { onOpen(s.url) }
                    }
                }
            }
        }
        if (tabIndex == 1 && history.isNotEmpty()) {
            PillButton("Effacer l'historique", filled = false, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                BrowserData.clearHistory(ctx)
                CookieManager.getInstance().removeAllCookies(null)
                android.webkit.WebStorage.getInstance().deleteAllData()
            }
        }
    }
}

// ---------------------------------------------------------------- Menu •••

@Composable
private fun MenuSheet(tab: Tab, version: Int, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val fav = remember(version, tab.url) { tab.url.isNotBlank() && BrowserData.isFavorite(ctx, tab.url) }
    val engine = remember(version) { BrowserData.engine(ctx) }
    val block = remember(version) { BrowserData.blockSocial(ctx) }

    Box(Modifier.fillMaxSize().background(Color(0x55000000)).clickable(onClick = onClose), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Palette.Bg)
                .clickable(enabled = false) {}
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(50)).background(Palette.Line))
            Spacer(Modifier.height(10.dp))
            if (tab.url.isNotBlank()) {
                Text(tab.title.ifBlank { BrowserData.domain(tab.url) }, style = serifStyle(24), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(tab.url, style = bodyStyle(13, Palette.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                MenuRow(Icons.star, if (fav) "Retirer des favoris" else "Ajouter aux favoris") {
                    BrowserData.toggleFavorite(ctx, tab.title, tab.url); onClose()
                }
                MenuRow(Icons.search, "Rechercher dans la page") {
                    tab.webView?.evaluateJavascript("(function(){var q=prompt('Rechercher dans la page');if(q)window.find(q);})()", null)
                    onClose()
                }
                MenuRow(Icons.globe, "Ouvrir dans une autre app") {
                    try {
                        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, Uri.parse(tab.url)), "Ouvrir avec"))
                    } catch (e: Exception) {}
                    onClose()
                }
                Divider()
            }
            SectionTitle("Moteur de recherche")
            Row(Modifier.fillMaxWidth()) {
                Engine.entries.forEach { e ->
                    val sel = e == engine
                    Text(
                        e.label,
                        style = bodyStyle(13, if (sel) Color.White else Palette.Ink, bold = true),
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (sel) Palette.Accent else Palette.Card)
                            .clickable { BrowserData.setEngine(ctx, e) }
                            .padding(vertical = 9.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Bloquer les réseaux sociaux", style = bodyStyle(17))
                    Text("Facebook, Instagram, TikTok, X, Snapchat…", style = bodyStyle(13, Palette.Muted))
                }
                Switch(
                    checked = block,
                    onCheckedChange = { BrowserData.setBlockSocial(ctx, it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Palette.Accent, checkedThumbColor = Color.White),
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun MenuRow(icon: LineIcon, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineIconView(icon, Palette.Accent, 22.dp)
        Spacer(Modifier.width(14.dp))
        Text(text, style = bodyStyle(17))
    }
}
