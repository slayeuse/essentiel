package fr.essentiel.launcher.camera

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import fr.essentiel.launcher.Icons
import fr.essentiel.launcher.LineIcon
import fr.essentiel.launcher.LineIconView
import fr.essentiel.launcher.Nav
import fr.essentiel.launcher.Palette
import fr.essentiel.launcher.PermissionGate
import fr.essentiel.launcher.Route
import fr.essentiel.launcher.ToolScaffold
import fr.essentiel.launcher.bodyStyle
import fr.essentiel.launcher.gallery.MediaRepo
import fr.essentiel.launcher.serifStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.atan
import kotlin.math.roundToInt

private suspend fun cameraProvider(ctx: Context): ProcessCameraProvider = suspendCancellableCoroutine { cont ->
    val future = ProcessCameraProvider.getInstance(ctx)
    future.addListener({ cont.resume(future.get()) }, ContextCompat.getMainExecutor(ctx))
}

private fun fileName(prefix: String) = prefix + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

/** Caméras arrière : principale + ultra grand-angle (« 0,5 ») si le téléphone la donne aux apps. */
private data class BackCameras(val mainId: String?, val wideId: String?)

private fun fov(info: CameraInfo): Double? = try {
    val c = Camera2CameraInfo.from(info)
    val focal = c.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.minOrNull()
    val sensor = c.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
    if (focal != null && sensor != null && focal > 0) Math.toDegrees(2 * atan(sensor.width / (2.0 * focal))) else null
} catch (e: Exception) { null }

private fun findBackCameras(provider: ProcessCameraProvider): BackCameras = try {
    val backs = provider.availableCameraInfos.filter {
        Camera2CameraInfo.from(it).getCameraCharacteristic(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
    }
    val main = backs.firstOrNull()
    val mainFov = main?.let { fov(it) } ?: 0.0
    val wide = backs.drop(1).mapNotNull { info -> fov(info)?.let { info to it } }
        .filter { it.second > mainFov * 1.25 }.maxByOrNull { it.second }?.first
    BackCameras(main?.let { Camera2CameraInfo.from(it).cameraId }, wide?.let { Camera2CameraInfo.from(it).cameraId })
} catch (e: Exception) { BackCameras(null, null) }

private fun selectorFor(id: String): CameraSelector = CameraSelector.Builder()
    .addCameraFilter { infos -> infos.filter { Camera2CameraInfo.from(it).cameraId == id } }
    .build()

@Composable
fun CameraScreen(nav: Nav) {
    val ctx = LocalContext.current
    val hasCam = ctx.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    if (!hasCam) {
        ToolScaffold("photo") {
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
    val scope = rememberCoroutineScope()
    @Suppress("DEPRECATION")
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

    var front by remember { mutableStateOf(false) }
    var useWide by remember { mutableStateOf(false) }
    var videoMode by remember { mutableStateOf(false) }
    var flash by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var grid by remember { mutableStateOf(false) }
    var timer by remember { mutableIntStateOf(0) }          // 0, 3 ou 10 s
    var countdown by remember { mutableIntStateOf(0) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var recordStart by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(0L) }
    var last by remember { mutableStateOf<Uri?>(MediaRepo.latest(ctx)) }
    var blink by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var cams by remember { mutableStateOf(BackCameras(null, null)) }
    var zoom by remember { mutableFloatStateOf(1f) }         // rapport de zoom de la caméra active
    var minZoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(8f) }
    var zoomLabelVisible by remember { mutableStateOf(false) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }

    val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
    }
    val videoCapture = remember {
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.FHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
            .build()
        VideoCapture.withOutput(recorder)
    }

    LaunchedEffect(front, videoMode, useWide) {
        try {
            val provider = cameraProvider(ctx)
            if (cams.mainId == null) cams = findBackCameras(provider)
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val wideId = cams.wideId
            val selector = when {
                front -> CameraSelector.DEFAULT_FRONT_CAMERA
                useWide && wideId != null -> selectorFor(wideId)
                else -> CameraSelector.DEFAULT_BACK_CAMERA
            }
            provider.unbindAll()
            val cam = if (videoMode) provider.bindToLifecycle(lifecycleOwner, selector, preview, videoCapture)
            else provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
            camera = cam
            cam.cameraInfo.zoomState.value?.let { z -> minZoom = z.minZoomRatio; maxZoom = z.maxZoomRatio }
            // Téléphones qui gèrent le 0,5 dans la caméra principale (zoom < 1)
            val start = if (useWide && wideId == null) minZoom else 1f
            zoom = start.coerceIn(minZoom, maxZoom)
            cam.cameraControl.setZoomRatio(zoom)
        } catch (e: Exception) {
            Toast.makeText(ctx, "Appareil photo indisponible", Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(flash) { imageCapture.flashMode = flash }
    LaunchedEffect(recording) { while (recording != null) { now = System.currentTimeMillis(); delay(500) } }
    LaunchedEffect(blink) { if (blink) { delay(110); blink = false } }
    LaunchedEffect(zoomLabelVisible, zoom) { if (zoomLabelVisible) { delay(1200); zoomLabelVisible = false } }
    LaunchedEffect(focusPoint) { if (focusPoint != null) { delay(1100); focusPoint = null } }
    DisposableEffect(Unit) { onDispose { recording?.stop() } }

    val canHalf = cams.wideId != null || minZoom < 0.99f
    var showHalfHelp by remember { mutableStateOf(false) }
    // Facteur affiché : 0,5 sur l'ultra grand-angle
    val shownFactor = if (useWide && cams.wideId != null) 0.5f * zoom else zoom

    fun setZoom(r: Float) {
        zoom = r.coerceIn(minZoom, maxZoom)
        camera?.cameraControl?.setZoomRatio(zoom)
    }

    fun takePhoto() {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName("IMG_"))
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Camera")
        }
        val opts = ImageCapture.OutputFileOptions.Builder(ctx.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values).build()
        blink = true
        imageCapture.takePicture(opts, ContextCompat.getMainExecutor(ctx), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) { last = output.savedUri ?: last }
            override fun onError(exception: ImageCaptureException) {
                Toast.makeText(ctx, "Photo non enregistrée", Toast.LENGTH_SHORT).show()
            }
        })
    }

    fun shoot() {
        if (timer == 0 || countdown > 0) { if (countdown == 0) takePhoto(); return }
        scope.launch {
            for (s in timer downTo 1) { countdown = s; delay(1000) }
            countdown = 0
            takePhoto()
        }
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
        // ----- Aperçu + gestes (pincer pour zoomer, toucher pour la mise au point) -----
        Box(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, z, _ ->
                        if (z != 1f) {
                            zoomLabelVisible = true
                            setZoom(zoom * z)
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { p ->
                        focusPoint = p
                        try {
                            val point = previewView.meteringPointFactory.createPoint(p.x, p.y)
                            camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                        } catch (e: Exception) { }
                    }
                }
        ) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            if (grid) {
                Canvas(Modifier.fillMaxSize()) {
                    val c = Color.White.copy(alpha = 0.35f)
                    for (k in 1..2) {
                        drawLine(c, Offset(size.width * k / 3, 0f), Offset(size.width * k / 3, size.height), 1.5f)
                        drawLine(c, Offset(0f, size.height * k / 3), Offset(size.width, size.height * k / 3), 1.5f)
                    }
                }
            }
            focusPoint?.let { p ->
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Palette.Accent, 46f, p, style = Stroke(3f))
                    drawCircle(Color.White, 4f, p)
                }
            }
        }
        if (blink) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)))
        if (countdown > 0) {
            Text("$countdown", style = serifStyle(120, Color.White), modifier = Modifier.align(Alignment.Center))
        }
        if (zoomLabelVisible) {
            Text(
                String.format(Locale.FRANCE, "%.1f×", shownFactor), style = bodyStyle(18, Color.White, bold = true),
                modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }

        // ----- Barre du haut -----
        Row(
            Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (!videoMode) {
                TopToggle(
                    if (flash == ImageCapture.FLASH_MODE_OFF) Icons.flashOff else Icons.flash,
                    when (flash) { ImageCapture.FLASH_MODE_ON -> "oui"; ImageCapture.FLASH_MODE_AUTO -> "auto"; else -> "" },
                    flash != ImageCapture.FLASH_MODE_OFF
                ) {
                    flash = when (flash) {
                        ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                        ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                        else -> ImageCapture.FLASH_MODE_OFF
                    }
                }
            } else Spacer(Modifier.width(40.dp))
            if (recording != null) {
                val s = ((now - recordStart) / 1000).coerceAtLeast(0)
                Text(
                    "● %d:%02d".format(s / 60, s % 60), style = bodyStyle(16, Color.White, bold = true),
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Palette.Accent).padding(horizontal = 12.dp, vertical = 5.dp)
                )
            } else {
                TopToggle(Icons.clock, if (timer > 0) "${timer}s" else "", timer > 0) {
                    timer = when (timer) { 0 -> 3; 3 -> 10; else -> 0 }
                }
            }
            TopToggle(Icons.grid, "", grid) { grid = !grid }
        }

        // ----- Bas : zoom, mode, déclencheur -----
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f)).padding(top = 14.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!front) {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f)).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val options = buildList {
                        add(0.5f) // toujours visible : si le téléphone le bloque, on explique et on propose l'appareil ASUS
                        add(1f); add(2f)
                        if (maxZoom >= 5f) add(5f)
                    }
                    options.forEach { f ->
                        val active = kotlin.math.abs(shownFactor - f) < 0.05f ||
                            (f == 1f && !useWide && shownFactor in 1f..1.95f) ||
                            (f == 2f && !useWide && shownFactor in 2f..4.9f)
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .background(if (active) Color.Black.copy(alpha = 0.55f) else Color.Transparent)
                                .clickable {
                                    if (f == 0.5f && !canHalf) {
                                        showHalfHelp = true
                                    } else if (f == 0.5f) {
                                        if (cams.wideId != null) { if (!useWide) useWide = true else setZoom(1f) } else setZoom(minZoom)
                                    } else {
                                        if (useWide) { useWide = false; scope.launch { delay(600); setZoom(f) } } else setZoom(f)
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            val label = if (active && kotlin.math.abs(shownFactor - f) >= 0.05f)
                                String.format(Locale.FRANCE, "%.1f", shownFactor) else if (f == 0.5f) ",5" else f.roundToInt().toString()
                            Text(label + if (active) "×" else "", style = bodyStyle(13, if (active) Palette.Accent else Color.White, bold = true))
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            if (recording == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    ModeLabel("VIDÉO", videoMode) { videoMode = true }
                    ModeLabel("PHOTO", !videoMode) { videoMode = false }
                }
                Spacer(Modifier.height(16.dp))
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 34.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(Color.DarkGray)
                        .border(1.5.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
                        .clickable { nav.go(Route.Gallery) }
                ) {
                    last?.let {
                        AsyncImage(
                            model = it, imageLoader = MediaRepo.loader(ctx), contentDescription = null,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                // Déclencheur
                Box(
                    Modifier.size(82.dp).clip(CircleShape).border(4.dp, Color.White, CircleShape)
                        .clickable { if (videoMode) toggleVideo() else shoot() },
                    contentAlignment = Alignment.Center,
                ) {
                    val inner = when {
                        recording != null -> 30.dp
                        else -> 66.dp
                    }
                    Box(
                        Modifier.size(inner)
                            .clip(if (recording != null) RoundedCornerShape(8.dp) else CircleShape)
                            .background(if (videoMode) Palette.Accent else Color.White)
                    )
                }
                Box(
                    Modifier.size(54.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f))
                        .clickable(enabled = recording == null) { front = !front; useWide = false },
                    contentAlignment = Alignment.Center,
                ) { LineIconView(Icons.flip, Color.White, 26.dp) }
            }
        }
    }

    if (showHalfHelp) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showHalfHelp = false },
            containerColor = Palette.Bg,
            title = { Text("Grand-angle 0,5", style = serifStyle(24)) },
            text = {
                Text(
                    "Ton téléphone ne laisse pas les autres apps utiliser la caméra grand-angle : ASUS la réserve à son " +
                        "appareil photo. Tu peux l'ouvrir pour cette photo ; elle arrivera quand même dans la galerie.",
                    style = bodyStyle(15)
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showHalfHelp = false
                    val i = systemCameraIntent(ctx)
                    if (i != null) ctx.startActivity(i) else Toast.makeText(ctx, "Aucun autre appareil photo trouvé", Toast.LENGTH_SHORT).show()
                }) { Text("Ouvrir l'appareil ASUS") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { showHalfHelp = false }) { Text("Annuler") } },
        )
    }
}

/** L'appareil photo du fabricant (ASUS), qui a accès au grand-angle. */
private fun systemCameraIntent(ctx: Context): android.content.Intent? {
    val base = android.content.Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
    val apps = ctx.packageManager.queryIntentActivities(base, 0).map { it.activityInfo.packageName }
        .filter { it != ctx.packageName }
    val pkg = apps.firstOrNull { "asus" in it } ?: apps.firstOrNull() ?: return null
    return base.setPackage(pkg).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
}

@Composable
private fun TopToggle(icon: LineIcon, label: String, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineIconView(icon, if (on) Palette.Accent else Color.White, 22.dp)
        if (label.isNotEmpty()) Text(" $label", style = bodyStyle(13, if (on) Palette.Accent else Color.White, bold = true))
    }
}

@Composable
private fun ModeLabel(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = bodyStyle(14, if (selected) Palette.Accent else Color.White, bold = true).copy(letterSpacing = 2.sp),
        modifier = Modifier.clickable(onClick = onClick).padding(6.dp)
    )
}
