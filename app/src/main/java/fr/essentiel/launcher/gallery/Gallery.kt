package fr.essentiel.launcher.gallery

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import fr.essentiel.launcher.EmptyText
import fr.essentiel.launcher.Icons
import fr.essentiel.launcher.LineIconView
import fr.essentiel.launcher.Nav
import fr.essentiel.launcher.Palette
import fr.essentiel.launcher.PermissionGate
import fr.essentiel.launcher.Route
import fr.essentiel.launcher.ToolScaffold
import fr.essentiel.launcher.bodyStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Media(val uri: Uri, val isVideo: Boolean, val date: Long)

object MediaRepo {
    /** Liste partagée entre la grille et la visionneuse. */
    var items by mutableStateOf<List<Media>>(emptyList())

    private var imageLoader: ImageLoader? = null

    fun loader(ctx: Context): ImageLoader = imageLoader ?: ImageLoader.Builder(ctx.applicationContext)
        .components { add(VideoFrameDecoder.Factory()) }
        .crossfade(true)
        .build()
        .also { imageLoader = it }

    val permissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= 33)
            arrayOf("android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VIDEO")
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    fun load(ctx: Context): List<Media> {
        val out = mutableListOf<Media>()
        try {
            ctx.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE, MediaStore.Files.FileColumns.DATE_ADDED),
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})",
                null,
                "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val video = c.getInt(1) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    val base = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    out += Media(ContentUris.withAppendedId(base, id), video, c.getLong(2))
                }
            }
        } catch (e: Exception) { }
        return out
    }

    fun latest(ctx: Context): Uri? = try { load(ctx).firstOrNull()?.uri } catch (e: Exception) { null }
}

@Composable
fun GalleryScreen(nav: Nav) {
    val ctx = LocalContext.current
    ToolScaffold("galerie", onBack = nav::back) {
        PermissionGate(MediaRepo.permissions, "Pour afficher tes photos et vidéos, Essentiel a besoin d'y accéder.") {
            var loaded by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                MediaRepo.items = withContext(Dispatchers.IO) { MediaRepo.load(ctx) }
                loaded = true
            }
            val items = MediaRepo.items
            if (loaded && items.isEmpty()) { EmptyText("Aucune photo pour l'instant."); return@PermissionGate }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(items, key = { _, m -> m.uri.toString() }) { index, m ->
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Palette.Card)
                            .clickable { nav.go(Route.Viewer(index)) }
                    ) {
                        AsyncImage(
                            model = m.uri, imageLoader = MediaRepo.loader(ctx), contentDescription = null,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                        )
                        if (m.isVideo) {
                            Box(
                                Modifier.align(Alignment.BottomStart).padding(6.dp).size(24.dp)
                                    .clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)),
                                contentAlignment = Alignment.Center,
                            ) { LineIconView(Icons.play, Color.White, 12.dp) }
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

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { items.getOrNull(it)?.uri?.toString() ?: it.toString() }) { page ->
            val m = items.getOrNull(page) ?: return@HorizontalPager
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                    AsyncImage(
                        model = m.uri, imageLoader = MediaRepo.loader(ctx), contentDescription = null,
                        contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()
                    )
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

        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)).clickable { nav.back() },
                contentAlignment = Alignment.Center,
            ) { LineIconView(Icons.back, Color.White, 24.dp) }
            Spacer(Modifier.weight(1f))
            Text("${pager.currentPage + 1} / ${items.size}", style = bodyStyle(14, Color.White))
        }

        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 30.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            val current = items.getOrNull(pager.currentPage)
            Column(
                Modifier.clickable {
                    current ?: return@clickable
                    val share = Intent(Intent.ACTION_SEND)
                        .setType(if (current.isVideo) "video/*" else "image/*")
                        .putExtra(Intent.EXTRA_STREAM, current.uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    ctx.startActivity(Intent.createChooser(share, "Partager"))
                }.padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LineIconView(Icons.share, Color.White, 26.dp)
                Text("partager", style = bodyStyle(13, Color.White))
            }
            Column(
                Modifier.clickable { current?.let { delete(it.uri) } }.padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LineIconView(Icons.trash, Color.White, 26.dp)
                Text("supprimer", style = bodyStyle(13, Color.White))
            }
        }
    }
}
