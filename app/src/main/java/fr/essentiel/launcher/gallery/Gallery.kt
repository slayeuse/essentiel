package fr.essentiel.launcher.gallery

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.format.DateUtils
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.exifinterface.media.ExifInterface
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import fr.essentiel.launcher.CardGroup
import fr.essentiel.launcher.Divider
import fr.essentiel.launcher.EmptyText
import fr.essentiel.launcher.Icons
import fr.essentiel.launcher.LineIcon
import fr.essentiel.launcher.LineIconView
import fr.essentiel.launcher.Nav
import fr.essentiel.launcher.Palette
import fr.essentiel.launcher.PermissionGate
import fr.essentiel.launcher.Route
import fr.essentiel.launcher.ToolScaffold
import fr.essentiel.launcher.bodyStyle
import fr.essentiel.launcher.map.MapApi
import fr.essentiel.launcher.serifStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class Media(
    val uri: Uri,
    val isVideo: Boolean,
    val date: Long,        // ms, date de prise de vue si connue
    val name: String = "",
    val size: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0,
)

/** Infos détaillées d'une photo / vidéo. */
data class MediaInfo(
    val device: String = "",
    val lens: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
    val place: String = "",
)

object MediaRepo {
    var items by mutableStateOf<List<Media>>(emptyList())

    private var imageLoader: ImageLoader? = null

    fun loader(ctx: Context): ImageLoader = imageLoader ?: ImageLoader.Builder(ctx.applicationContext)
        .components { add(VideoFrameDecoder.Factory()) }
        .crossfade(true)
        .build()
        .also { imageLoader = it }

    val permissions: Array<String>
        get() = when {
            Build.VERSION.SDK_INT >= 33 -> arrayOf(
                "android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VIDEO",
                Manifest.permission.ACCESS_MEDIA_LOCATION
            )
            Build.VERSION.SDK_INT >= 29 -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.ACCESS_MEDIA_LOCATION)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    fun load(ctx: Context): List<Media> {
        val out = mutableListOf<Media>()
        try {
            ctx.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.MEDIA_TYPE,
                    MediaStore.Files.FileColumns.DATE_ADDED,
                    MediaStore.MediaColumns.DATE_TAKEN,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.WIDTH,
                    MediaStore.MediaColumns.HEIGHT,
                    MediaStore.MediaColumns.DURATION,
                ),
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})",
                null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val video = c.getInt(1) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    val base = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    val taken = if (c.isNull(3)) 0L else c.getLong(3)
                    out += Media(
                        ContentUris.withAppendedId(base, id), video,
                        if (taken > 0) taken else c.getLong(2) * 1000L,
                        c.getString(4).orEmpty(), c.getLong(5), c.getInt(6), c.getInt(7),
                        if (c.isNull(8)) 0L else c.getLong(8)
                    )
                }
            }
        } catch (e: Exception) { }
        return out.sortedByDescending { it.date }
    }

    fun latest(ctx: Context): Uri? = try { load(ctx).firstOrNull()?.uri } catch (e: Exception) { null }

    private val placeCache = ConcurrentHashMap<String, String>()

    /** Appareil, objectif et lieu (lus dans les infos EXIF de la photo). */
    fun info(ctx: Context, m: Media): MediaInfo {
        var device = ""; var lens = ""; var lat: Double? = null; var lon: Double? = null
        try {
            if (m.isVideo) {
                val r = MediaMetadataRetriever()
                r.setDataSource(ctx, m.uri)
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)?.let { s ->
                    Regex("([+-][0-9.]+)([+-][0-9.]+)").find(s)?.let { mm ->
                        lat = mm.groupValues[1].toDoubleOrNull(); lon = mm.groupValues[2].toDoubleOrNull()
                    }
                }
                r.release()
            } else {
                val uri = if (Build.VERSION.SDK_INT >= 29) try { MediaStore.setRequireOriginal(m.uri) } catch (e: Exception) { m.uri } else m.uri
                val stream = try { ctx.contentResolver.openInputStream(uri) } catch (e: Exception) { ctx.contentResolver.openInputStream(m.uri) }
                stream?.use { s ->
                    val exif = ExifInterface(s)
                    val make = exif.getAttribute(ExifInterface.TAG_MAKE).orEmpty().trim()
                    val model = exif.getAttribute(ExifInterface.TAG_MODEL).orEmpty().trim()
                    device = if (model.startsWith(make, ignoreCase = true)) model else "$make $model".trim()
                    val parts = mutableListOf<String>()
                    exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0).takeIf { it > 0 }?.let {
                        parts += String.format(Locale.FRANCE, "%.1f mm", it)
                    }
                    exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0).takeIf { it > 0 }?.let {
                        parts += String.format(Locale.FRANCE, "ƒ/%.1f", it)
                    }
                    exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0).takeIf { it > 0 }?.let {
                        parts += if (it < 1) "1/${(1 / it).toInt()} s" else String.format(Locale.FRANCE, "%.1f s", it)
                    }
                    exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0).takeIf { it > 0 }?.let { parts += "ISO $it" }
                    lens = parts.joinToString(" · ")
                    exif.latLong?.let { ll -> if (ll[0] != 0.0 || ll[1] != 0.0) { lat = ll[0]; lon = ll[1] } }
                }
            }
        } catch (e: Exception) { }
        var place = ""
        val la = lat; val lo = lon
        if (la != null && lo != null) {
            val key = String.format(Locale.US, "%.3f,%.3f", la, lo)
            place = placeCache[key] ?: (MapApi.reverse(la, lo)?.let { p ->
                listOf(p.address.substringAfterLast(", ").ifBlank { p.name }).firstOrNull().orEmpty()
                    .replace(Regex("^\\d{5} "), "")
            } ?: "").also { if (it.isNotBlank()) placeCache[key] = it }
        }
        return MediaInfo(device, lens, la, lo, place)
    }
}

private fun dayLabel(ms: Long): String {
    val now = Calendar.getInstance()
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    return when {
        DateUtils.isToday(ms) -> "Aujourd'hui"
        DateUtils.isToday(ms + 86_400_000L) -> "Hier"
        c.get(Calendar.YEAR) == now.get(Calendar.YEAR) -> SimpleDateFormat("EEEE d MMMM", Locale.FRANCE).format(Date(ms)).replaceFirstChar { it.uppercase() }
        else -> SimpleDateFormat("d MMMM yyyy", Locale.FRANCE).format(Date(ms))
    }
}

private fun dayKey(ms: Long): Int {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
}

@Composable
fun GalleryScreen(nav: Nav) {
    val ctx = LocalContext.current
    ToolScaffold("galerie", subtitle = if (MediaRepo.items.isNotEmpty()) "${MediaRepo.items.size} photos et vidéos" else null) {
        PermissionGate(MediaRepo.permissions, "Pour afficher tes photos et vidéos, Essentiel a besoin d'y accéder.") {
            var loaded by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                MediaRepo.items = withContext(Dispatchers.IO) { MediaRepo.load(ctx) }
                loaded = true
            }
            val items = MediaRepo.items
            if (loaded && items.isEmpty()) { EmptyText("Aucune photo pour l'instant."); return@PermissionGate }
            // Regroupement par jour
            val groups = remember(items) { items.withIndex().groupBy { dayKey(it.value.date) }.values.toList() }
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                groups.forEach { group ->
                    val first = group.first().value
                    item(key = "h" + dayKey(first.date), span = { GridItemSpan(maxLineSpan) }) {
                        Row(Modifier.padding(top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
                            Text(dayLabel(first.date), style = bodyStyle(16, bold = true), modifier = Modifier.weight(1f))
                            Text("${group.size}", style = bodyStyle(13, Palette.Muted))
                        }
                    }
                    group.forEach { (index, m) ->
                        item(key = m.uri.toString()) {
                            Box(
                                Modifier.aspectRatio(1f).clip(RoundedCornerShape(4.dp)).background(Palette.Card)
                                    .clickable { nav.go(Route.Viewer(index)) }
                            ) {
                                AsyncImage(
                                    model = m.uri, imageLoader = MediaRepo.loader(ctx), contentDescription = null,
                                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                                )
                                if (m.isVideo) {
                                    Text(
                                        if (m.durationMs > 0) {
                                            val s = m.durationMs / 1000; "%d:%02d".format(s / 60, s % 60)
                                        } else "▶",
                                        style = bodyStyle(11, Color.White, bold = true),
                                        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ViewerScreen(nav: Nav, startIndex: Int) {
    val ctx = LocalContext.current
    val items = MediaRepo.items
    if (items.isEmpty()) { LaunchedEffect(Unit) { nav.back() }; return }
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, items.lastIndex)) { items.size }
    var playing by remember { mutableStateOf<Uri?>(null) }
    var pendingDelete by remember { mutableStateOf<Uri?>(null) }
    var chrome by remember { mutableStateOf(true) }      // barres visibles
    var showInfo by remember { mutableStateOf(false) }   // panneau d'infos
    var zoomed by remember { mutableStateOf(false) }     // photo zoomée : pas de défilement

    val current = items.getOrNull(pager.currentPage)
    var info by remember { mutableStateOf<MediaInfo?>(null) }
    LaunchedEffect(current?.uri) {
        info = null
        val m = current ?: return@LaunchedEffect
        info = withContext(Dispatchers.IO) { MediaRepo.info(ctx, m) }
    }

    fun removeFromList(uri: Uri) {
        MediaRepo.items = MediaRepo.items.filter { it.uri != uri }
        if (MediaRepo.items.isEmpty()) nav.back()
    }

    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        val uri = pendingDelete
        if (res.resultCode == Activity.RESULT_OK && uri != null) {
            try { ctx.contentResolver.delete(uri, null, null) } catch (e: Exception) { }
            removeFromList(uri)
        }
        pendingDelete = null
    }

    fun delete(uri: Uri) {
        try {
            ctx.contentResolver.delete(uri, null, null)
            removeFromList(uri)
        } catch (e: RecoverableSecurityException) {
            pendingDelete = uri
            deleteLauncher.launch(IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build())
        } catch (e: Exception) {
            Toast.makeText(ctx, "Impossible de supprimer", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        Modifier.fillMaxSize().background(if (chrome) Palette.Bg else Color.Black)
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        // Glisser (vers le haut ou le bas) : affiche les infos ; vers le bas quand elles sont ouvertes : les ferme
                        if (!zoomed) {
                            if (total < -80f) showInfo = true
                            else if (total > 80f) showInfo = !showInfo
                        }
                    },
                    onVerticalDrag = { _, d -> total += d },
                )
            }
    ) {
        HorizontalPager(
            state = pager, modifier = Modifier.fillMaxSize(), userScrollEnabled = !zoomed,
            key = { items.getOrNull(it)?.uri?.toString() ?: it.toString() }
        ) { page ->
            val m = items.getOrNull(page) ?: return@HorizontalPager
            Box(
                Modifier.fillMaxSize().clickable(interactionSource = null, indication = null) { chrome = !chrome },
                contentAlignment = Alignment.Center
            ) {
                if (m.isVideo && playing == m.uri && pager.currentPage == page) {
                    AndroidView(
                        factory = { c ->
                            VideoView(c).apply {
                                setVideoURI(m.uri)
                                val controller = MediaController(c)
                                controller.setAnchorView(this)
                                setMediaController(controller)
                                setOnPreparedListener { start() }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    if (m.isVideo) {
                        AsyncImage(
                            model = m.uri, imageLoader = MediaRepo.loader(ctx), contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().fillMaxHeight(if (showInfo) 0.5f else 1f)
                                .align(if (showInfo) Alignment.TopCenter else Alignment.Center)
                        )
                    } else {
                        ZoomableImage(
                            model = m.uri, loader = MediaRepo.loader(ctx),
                            modifier = Modifier.fillMaxWidth().fillMaxHeight(if (showInfo) 0.5f else 1f)
                                .align(if (showInfo) Alignment.TopCenter else Alignment.Center),
                            onZoomChange = { z -> if (pager.currentPage == page) zoomed = z },
                            onTap = { chrome = !chrome },
                        )
                    }
                    if (m.isVideo) {
                        Box(
                            Modifier.size(72.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
                                .clickable { playing = m.uri },
                            contentAlignment = Alignment.Center,
                        ) { LineIconView(Icons.play, Color.White, 30.dp) }
                    }
                }
            }
        }

        // ----- En haut : date et lieu (comme sur iPhone) -----
        AnimatedVisibility(chrome && !showInfo, enter = fadeIn(), exit = fadeOut()) {
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Palette.Bg, Palette.Bg.copy(alpha = 0.85f), Color.Transparent)))
                    .padding(top = 20.dp, bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (current != null) {
                    Text(
                        info?.place?.takeIf { it.isNotBlank() } ?: dayLabel(current.date),
                        style = bodyStyle(17, bold = true), textAlign = TextAlign.Center
                    )
                    Text(
                        (if (info?.place.isNullOrBlank()) "" else dayLabel(current.date) + " · ") +
                            SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(current.date)),
                        style = bodyStyle(13, Palette.Muted)
                    )
                }
            }
        }

        // ----- En bas : actions -----
        AnimatedVisibility(
            chrome && !showInfo, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Palette.Bg.copy(alpha = 0.9f), Palette.Bg)))
                    .padding(top = 20.dp, bottom = 26.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ViewerAction(Icons.share, "partager") {
                    val m = current ?: return@ViewerAction
                    val share = Intent(Intent.ACTION_SEND)
                        .setType(if (m.isVideo) "video/*" else "image/*")
                        .putExtra(Intent.EXTRA_STREAM, m.uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    ctx.startActivity(Intent.createChooser(share, "Partager"))
                }
                if (current?.isVideo == false) {
                    ViewerAction(Icons.sliders, "modifier") { nav.go(Route.Editor(pager.currentPage)) }
                }
                ViewerAction(Icons.info, "infos") { showInfo = true }
                ViewerAction(Icons.trash, "supprimer") { current?.let { delete(it.uri) } }
            }
        }

        // ----- Panneau d'infos -----
        AnimatedVisibility(
            showInfo && current != null,
            enter = slideInVertically { it }, exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val m = current ?: return@AnimatedVisibility
            val i = info
            Column(
                Modifier.fillMaxWidth().fillMaxHeight(0.52f)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(Palette.Bg)
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp)
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(50)).background(Palette.Line))
                Spacer(Modifier.height(14.dp))
                Text(
                    SimpleDateFormat("EEEE d MMMM yyyy · HH:mm", Locale.FRANCE).format(Date(m.date)).replaceFirstChar { it.uppercase() },
                    style = serifStyle(22)
                )
                Spacer(Modifier.height(12.dp))
                CardGroup {
                    InfoRow(Icons.camera, i?.device?.ifBlank { null } ?: if (i == null) "lecture des infos…" else "appareil inconnu", i?.lens ?: "")
                    Divider()
                    val mp = if (m.width > 0 && m.height > 0) String.format(Locale.FRANCE, "%.1f Mpx", m.width * m.height / 1_000_000.0) else ""
                    InfoRow(
                        Icons.image, m.name.ifBlank { "fichier" },
                        listOf(
                            mp, if (m.width > 0) "${m.width} × ${m.height}" else "",
                            if (m.size > 0) String.format(Locale.FRANCE, "%.1f Mo", m.size / 1_048_576.0) else ""
                        ).filter { it.isNotBlank() }.joinToString(" · ")
                    )
                    Divider()
                    val la = i?.lat; val lo = i?.lon
                    InfoRow(
                        Icons.map,
                        when {
                            i == null -> "…"
                            i.place.isNotBlank() -> i.place
                            la != null -> "position enregistrée"
                            else -> "pas de lieu enregistré"
                        },
                        if (la != null && lo != null) String.format(Locale.FRANCE, "%.5f, %.5f", la, lo) else "",
                        onClick = if (la != null && lo != null) ({
                            ctx.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("geo:$la,$lo?q=$la,$lo")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }) else null
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "fermer", style = bodyStyle(15, Palette.Accent, bold = true),
                    modifier = Modifier.align(Alignment.CenterHorizontally).clickable { showInfo = false }.padding(10.dp)
                )
            }
        }
    }
}

@Composable
private fun InfoRow(icon: LineIcon, title: String, sub: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineIconView(icon, Palette.Accent, 22.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = bodyStyle(16))
            if (sub.isNotBlank()) Text(sub, style = bodyStyle(13, Palette.Muted))
        }
    }
}

@Composable
private fun ViewerAction(icon: LineIcon, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LineIconView(icon, Palette.Ink, 24.dp)
        Spacer(Modifier.height(4.dp))
        Text(label, style = bodyStyle(12, Palette.Muted))
    }
}
