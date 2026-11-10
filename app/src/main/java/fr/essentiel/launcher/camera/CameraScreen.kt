package fr.essentiel.launcher.camera

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import fr.essentiel.launcher.Icons
import fr.essentiel.launcher.LineIconView
import fr.essentiel.launcher.Nav
import fr.essentiel.launcher.Palette
import fr.essentiel.launcher.PermissionGate
import fr.essentiel.launcher.Route
import fr.essentiel.launcher.ToolScaffold
import fr.essentiel.launcher.bodyStyle
import fr.essentiel.launcher.gallery.MediaRepo
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

private suspend fun cameraProvider(ctx: Context): ProcessCameraProvider = suspendCancellableCoroutine { cont ->
    val future = ProcessCameraProvider.getInstance(ctx)
    future.addListener({ cont.resume(future.get()) }, ContextCompat.getMainExecutor(ctx))
}

private fun fileName(prefix: String) = prefix + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

@Composable
fun CameraScreen(nav: Nav) {
    val ctx = LocalContext.current
    val hasCam = ctx.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    if (!hasCam) {
        ToolScaffold("photo", onBack = nav::back) {
            PermissionGate(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                "Pour prendre des photos et des vidéos, Essentiel a besoin de l'appareil photo et du micro."
            ) { LaunchedEffect(Unit) { nav.replace(Route.Camera) } }
        }
        return
    }
    CameraContent(nav)
}

@Composable
private fun CameraContent(nav: Nav) {
    val ctx = LocalContext.current
    @Suppress("DEPRECATION")
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

    var front by remember { mutableStateOf(false) }
    var videoMode by remember { mutableStateOf(false) }
    var flash by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var recordStart by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(0L) }
    var last by remember { mutableStateOf<Uri?>(MediaRepo.latest(ctx)) }
    var blink by remember { mutableStateOf(false) }

    val previewView = remember {
        PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    }
    val videoCapture = remember {
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.FHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
            .build()
        VideoCapture.withOutput(recorder)
    }

    LaunchedEffect(front, videoMode) {
        try {
            val provider = cameraProvider(ctx)
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            provider.unbindAll()
            if (videoMode) provider.bindToLifecycle(lifecycleOwner, selector, preview, videoCapture)
            else provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
        } catch (e: Exception) {
            Toast.makeText(ctx, "Appareil photo indisponible", Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(flash) { imageCapture.flashMode = flash }
    LaunchedEffect(recording) { while (recording != null) { now = System.currentTimeMillis(); delay(500) } }
    LaunchedEffect(blink) { if (blink) { delay(120); blink = false } }
    DisposableEffect(Unit) { onDispose { recording?.stop() } }

    fun takePhoto() {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName("IMG_"))
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Camera")
        }
        val opts = ImageCapture.OutputFileOptions.Builder(
            ctx.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        ).build()
        blink = true
        imageCapture.takePicture(opts, ContextCompat.getMainExecutor(ctx), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) { last = output.savedUri ?: last }
            override fun onError(exception: ImageCaptureException) {
                Toast.makeText(ctx, "Photo non enregistrée", Toast.LENGTH_SHORT).show()
            }
        })
    }

    fun toggleVideo() {
        val current = recording
        if (current != null) { current.stop(); recording = null; return }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName("VID_"))
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Camera")
        }
        val opts = MediaStoreOutputOptions.Builder(ctx.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(values).build()
        var pending = videoCapture.output.prepareRecording(ctx, opts)
        if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            pending = pending.withAudioEnabled()
        }
        recordStart = System.currentTimeMillis()
        recording = pending.start(ContextCompat.getMainExecutor(ctx)) { event ->
            if (event is VideoRecordEvent.Finalize) {
                if (!event.hasError()) last = event.outputResults.outputUri
                else Toast.makeText(ctx, "Vidéo non enregistrée", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        if (blink) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.6f)))

        // Barre du haut
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIcon(Icons.back) { nav.back() }
            Spacer(Modifier.weight(1f))
            if (recording != null) {
                val s = ((now - recordStart) / 1000).coerceAtLeast(0)
                Text(
                    "● %d:%02d".format(s / 60, s % 60), style = bodyStyle(16, Color.White, bold = true),
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Palette.Accent)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
                Spacer(Modifier.weight(1f))
            }
            if (!videoMode) {
                val (icon, label) = when (flash) {
                    ImageCapture.FLASH_MODE_ON -> Icons.flash to "oui"
                    ImageCapture.FLASH_MODE_AUTO -> Icons.flash to "auto"
                    else -> Icons.flashOff to ""
                }
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.35f))
                        .clickable {
                            flash = when (flash) {
                                ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                                ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                                else -> ImageCapture.FLASH_MODE_OFF
                            }
                        }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LineIconView(icon, Color.White, 22.dp)
                    if (label.isNotEmpty()) Text(" $label", style = bodyStyle(14, Color.White))
                }
            }
        }

        // Barre du bas
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (recording == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                    ModeLabel("photo", !videoMode) { videoMode = false }
                    ModeLabel("vidéo", videoMode) { videoMode = true }
                }
                Spacer(Modifier.height(18.dp))
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 34.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.DarkGray)
                        .border(2.dp, Color.White, RoundedCornerShape(14.dp))
                        .clickable { nav.go(Route.Gallery) }
                ) {
                    last?.let {
                        AsyncImage(
                            model = it, imageLoader = MediaRepo.loader(ctx), contentDescription = null,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Box(
                    Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .border(5.dp, Color.White, CircleShape)
                        .padding(9.dp)
                        .clip(if (recording != null) RoundedCornerShape(10.dp) else CircleShape)
                        .background(if (videoMode) Palette.Accent else Color.White)
                        .clickable { if (videoMode) toggleVideo() else takePhoto() }
                )
                Box(
                    Modifier.size(54.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f))
                        .clickable(enabled = recording == null) { front = !front },
                    contentAlignment = Alignment.Center,
                ) { LineIconView(Icons.flip, Color.White, 26.dp) }
            }
        }
    }
}

@Composable
private fun RoundIcon(icon: fr.essentiel.launcher.LineIcon, onClick: () -> Unit) {
    Box(
        Modifier.size(46.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { LineIconView(icon, Color.White, 24.dp) }
}

@Composable
private fun ModeLabel(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = bodyStyle(16, if (selected) Palette.Accent else Color.White, bold = selected),
        modifier = Modifier.clickable(onClick = onClick).padding(6.dp)
    )
}
