package fr.essentiel.launcher.gallery

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import fr.essentiel.launcher.Icons
import fr.essentiel.launcher.LineIcon
import fr.essentiel.launcher.LineIconView
import fr.essentiel.launcher.Nav
import fr.essentiel.launcher.Palette
import fr.essentiel.launcher.PillButton
import fr.essentiel.launcher.SegmentedTabs
import fr.essentiel.launcher.bodyStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import android.graphics.ColorMatrix as AColorMatrix
import android.graphics.ColorMatrixColorFilter as AColorMatrixColorFilter
import androidx.compose.ui.graphics.ColorMatrix as CColorMatrix

/** Filtres prêts à l'emploi. */
private enum class Filter(val label: String) {
    NONE("original"), BW("noir & blanc"), SEPIA("sépia"), WARM("chaud"), COOL("froid"), VIVID("vif"), FADE("doux"), NOIR("contrasté")
}

private data class Adjust(
    val brightness: Float = 0f, // -1..1
    val contrast: Float = 0f,   // -1..1
    val saturation: Float = 0f, // -1..1
    val warmth: Float = 0f,     // -1..1
)

private fun scaleMatrix(r: Float, g: Float, b: Float, add: Float = 0f) = AColorMatrix(
    floatArrayOf(r, 0f, 0f, 0f, add, 0f, g, 0f, 0f, add, 0f, 0f, b, 0f, add, 0f, 0f, 0f, 1f, 0f)
)

/** Matrice de couleurs : filtre puis réglages. */
private fun buildMatrix(filter: Filter, a: Adjust): AColorMatrix {
    val m = AColorMatrix()
    when (filter) {
        Filter.NONE -> {}
        Filter.BW -> m.setSaturation(0f)
        Filter.SEPIA -> {
            m.setSaturation(0f)
            m.postConcat(scaleMatrix(1.07f, 0.92f, 0.72f, 8f))
        }
        Filter.WARM -> m.postConcat(scaleMatrix(1.08f, 1.0f, 0.88f, 4f))
        Filter.COOL -> m.postConcat(scaleMatrix(0.9f, 1.0f, 1.1f, 0f))
        Filter.VIVID -> m.setSaturation(1.45f)
        Filter.FADE -> {
            m.setSaturation(0.75f)
            m.postConcat(scaleMatrix(0.85f, 0.85f, 0.85f, 30f))
        }
        Filter.NOIR -> {
            m.setSaturation(0f)
            m.postConcat(scaleMatrix(1.35f, 1.35f, 1.35f, -45f))
        }
    }
    if (a.saturation != 0f) m.postConcat(AColorMatrix().apply { setSaturation(1f + a.saturation) })
    if (a.contrast != 0f) {
        val c = 1f + a.contrast * 0.8f
        m.postConcat(scaleMatrix(c, c, c, (1f - c) * 128f))
    }
    if (a.brightness != 0f) m.postConcat(scaleMatrix(1f, 1f, 1f, a.brightness * 80f))
    if (a.warmth != 0f) m.postConcat(scaleMatrix(1f + a.warmth * 0.12f, 1f, 1f - a.warmth * 0.12f))
    return m
}

/** Charge la photo en taille raisonnable (l'orientation EXIF est appliquée). */
private fun loadBitmap(ctx: Context, m: Media): Bitmap? = try {
    val src = ImageDecoder.createSource(ctx.contentResolver, m.uri)
    ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
        val w = info.size.width; val h = info.size.height
        val scale = min(1f, 2400f / max(w, h))
        decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.isMutableRequired = true
    }
} catch (e: Exception) { null }

/** Enregistre une copie modifiée dans DCIM/Essentiel (l'original n'est jamais touché). */
private fun saveCopy(ctx: Context, bmp: Bitmap, crop: Rect, filter: Filter, a: Adjust): Boolean = try {
    val x = (crop.left * bmp.width).toInt().coerceIn(0, bmp.width - 1)
    val y = (crop.top * bmp.height).toInt().coerceIn(0, bmp.height - 1)
    val w = (crop.width * bmp.width).toInt().coerceIn(1, bmp.width - x)
    val h = (crop.height * bmp.height).toInt().coerceIn(1, bmp.height - y)
    val cropped = Bitmap.createBitmap(bmp, x, y, w, h)
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = AColorMatrixColorFilter(buildMatrix(filter, a)) }
    canvas.drawBitmap(cropped, 0f, 0f, paint)
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + "_edit.jpg")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Essentiel")
    }
    val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    uri != null && (ctx.contentResolver.openOutputStream(uri)?.use { os -> out.compress(Bitmap.CompressFormat.JPEG, 94, os) } ?: false)
} catch (e: Exception) { false }

@Composable
fun EditorScreen(nav: Nav, index: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val media = MediaRepo.items.getOrNull(index)
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var filter by remember { mutableStateOf(Filter.NONE) }
    var adjust by remember { mutableStateOf(Adjust()) }
    var crop by remember { mutableStateOf(Rect(0f, 0f, 1f, 1f)) }
    var aspect by remember { mutableStateOf<Float?>(null) } // null = libre
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(media?.uri) {
        val m = media ?: return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) { loadBitmap(ctx, m) }
        if (bitmap == null) { Toast.makeText(ctx, "Impossible d'ouvrir la photo", Toast.LENGTH_SHORT).show(); nav.back() }
    }

    fun setAspect(r: Float?) {
        aspect = r
        val b = bitmap ?: return
        if (r == null) { crop = Rect(0f, 0f, 1f, 1f); return }
        val imgRatio = b.width.toFloat() / b.height
        // plus grand rectangle centré au bon format, en coordonnées 0..1
        val (w, h) = if (r > imgRatio) 1f to (imgRatio / r) else (r / imgRatio) to 1f
        crop = Rect((1 - w) / 2, (1 - h) / 2, (1 + w) / 2, (1 + h) / 2)
    }

    val matrix = remember(filter, adjust) { CColorMatrix(buildMatrix(filter, adjust).array) }

    Column(Modifier.fillMaxSize().background(Color(0xFF151413))) {
        // ----- Barre du haut -----
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("annuler", style = bodyStyle(16, Color.White), modifier = Modifier.clickable { nav.back() }.padding(6.dp))
            Spacer(Modifier.weight(1f))
            Text(
                "réinitialiser", style = bodyStyle(14, Color.White.copy(alpha = 0.7f)),
                modifier = Modifier.clickable { filter = Filter.NONE; adjust = Adjust(); setAspect(null) }.padding(6.dp)
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (saving) "…" else "enregistrer", style = bodyStyle(16, Palette.Accent, bold = true),
                modifier = Modifier.clickable(enabled = !saving && bitmap != null) {
                    val b = bitmap ?: return@clickable
                    saving = true
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { saveCopy(ctx, b, crop, filter, adjust) }
                        saving = false
                        if (ok) {
                            Toast.makeText(ctx, "Copie enregistrée dans la galerie", Toast.LENGTH_SHORT).show()
                            MediaRepo.items = withContext(Dispatchers.IO) { MediaRepo.load(ctx) }
                            nav.back()
                        } else Toast.makeText(ctx, "Échec de l'enregistrement", Toast.LENGTH_SHORT).show()
                    }
                }.padding(6.dp)
            )
        }

        // ----- Photo + cadre de recadrage -----
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            val b = bitmap
            if (b != null) {
                val density = LocalDensity.current
                val boxW = with(density) { maxWidth.toPx() }
                val boxH = with(density) { maxHeight.toPx() }
                val scale = min(boxW / b.width, boxH / b.height)
                val imgW = b.width * scale
                val imgH = b.height * scale
                val left = (boxW - imgW) / 2
                val top = (boxH - imgH) / 2
                val image = remember(b) { b.asImageBitmap() }
                Image(
                    image, contentDescription = null, contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.colorMatrix(matrix), modifier = Modifier.fillMaxSize()
                )
                if (tab == 0) {
                    var handle by remember { mutableIntStateOf(-1) } // 0..3 coins, 4 = déplacer
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(b, aspect) {
                            detectDragGestures(
                                onDragStart = { p ->
                                    val r = crop
                                    val corners = listOf(
                                        Offset(left + r.left * imgW, top + r.top * imgH),
                                        Offset(left + r.right * imgW, top + r.top * imgH),
                                        Offset(left + r.left * imgW, top + r.bottom * imgH),
                                        Offset(left + r.right * imgW, top + r.bottom * imgH),
                                    )
                                    val near = corners.indexOfFirst { (it - p).getDistance() < 70f }
                                    handle = if (near >= 0) near else {
                                        val inside = p.x in corners[0].x..corners[1].x && p.y in corners[0].y..corners[2].y
                                        if (inside) 4 else -1
                                    }
                                },
                                onDragEnd = { handle = -1 },
                                onDrag = { change, d ->
                                    change.consume()
                                    val dx = d.x / imgW
                                    val dy = d.y / imgH
                                    val r = crop
                                    val minS = 0.08f
                                    crop = when (handle) {
                                        4 -> {
                                            val nx = (r.left + dx).coerceIn(0f, 1f - r.width)
                                            val ny = (r.top + dy).coerceIn(0f, 1f - r.height)
                                            Rect(nx, ny, nx + r.width, ny + r.height)
                                        }
                                        in 0..3 -> {
                                            var l = r.left; var t = r.top; var rr = r.right; var bb = r.bottom
                                            when (handle) {
                                                0 -> { l += dx; t += dy }
                                                1 -> { rr += dx; t += dy }
                                                2 -> { l += dx; bb += dy }
                                                3 -> { rr += dx; bb += dy }
                                            }
                                            l = l.coerceIn(0f, rr - minS); rr = rr.coerceIn(l + minS, 1f)
                                            t = t.coerceIn(0f, bb - minS); bb = bb.coerceIn(t + minS, 1f)
                                            val asp = aspect
                                            if (asp != null) {
                                                // garde le format choisi (largeur pilote la hauteur)
                                                val wantH = (rr - l) * imgW / asp / imgH
                                                if (handle == 0 || handle == 1) t = (bb - wantH).coerceAtLeast(0f) else bb = (t + wantH).coerceAtMost(1f)
                                            }
                                            Rect(l, t, rr, bb)
                                        }
                                        else -> r
                                    }
                                },
                            )
                        }
                    ) {
                        val r = crop
                        val rect = Rect(left + r.left * imgW, top + r.top * imgH, left + r.right * imgW, top + r.bottom * imgH)
                        val dim = Color.Black.copy(alpha = 0.55f)
                        drawRect(dim, Offset(left, top), Size(imgW, rect.top - top))
                        drawRect(dim, Offset(left, rect.bottom), Size(imgW, top + imgH - rect.bottom))
                        drawRect(dim, Offset(left, rect.top), Size(rect.left - left, rect.height))
                        drawRect(dim, Offset(rect.right, rect.top), Size(left + imgW - rect.right, rect.height))
                        drawRect(Color.White, rect.topLeft, rect.size, style = Stroke(2.5f))
                        for (k in 1..2) {
                            val gx = rect.left + rect.width * k / 3
                            val gy = rect.top + rect.height * k / 3
                            drawLine(Color.White.copy(alpha = 0.5f), Offset(gx, rect.top), Offset(gx, rect.bottom), 1.2f)
                            drawLine(Color.White.copy(alpha = 0.5f), Offset(rect.left, gy), Offset(rect.right, gy), 1.2f)
                        }
                        listOf(rect.topLeft, rect.topRight, rect.bottomLeft, rect.bottomRight).forEach { c ->
                            drawRoundRect(Palette.Accent, c - Offset(9f, 9f), Size(18f, 18f), CornerRadius(4f))
                        }
                    }
                }
            }
        }

        // ----- Outils -----
        Column(Modifier.fillMaxWidth().background(Palette.Bg).padding(16.dp)) {
            SegmentedTabs(listOf("recadrer", "filtres", "réglages"), tab) { tab = it }
            Spacer(Modifier.height(14.dp))
            when (tab) {
                0 -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf<Pair<String, Float?>>("libre" to null, "1:1" to 1f, "4:3" to 4f / 3f, "3:4" to 3f / 4f, "16:9" to 16f / 9f).forEach { (l, r) ->
                        ToolChip(l, aspect == r) { setAspect(r) }
                    }
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(50)).background(Palette.Card).border(1.dp, Palette.Line, RoundedCornerShape(50))
                            .clickable {
                                val b = bitmap ?: return@clickable
                                bitmap = Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(90f) }, true)
                                setAspect(null)
                            },
                        contentAlignment = Alignment.Center,
                    ) { LineIconView(Icons.rotate, Palette.Accent, 22.dp) }
                }
                1 -> LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 8.dp)) {
                    items(Filter.entries) { f -> ToolChip(f.label, filter == f) { filter = f } }
                }
                else -> Column {
                    AdjustSlider("luminosité", adjust.brightness) { adjust = adjust.copy(brightness = it) }
                    AdjustSlider("contraste", adjust.contrast) { adjust = adjust.copy(contrast = it) }
                    AdjustSlider("saturation", adjust.saturation) { adjust = adjust.copy(saturation = it) }
                    AdjustSlider("chaleur", adjust.warmth) { adjust = adjust.copy(warmth = it) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ToolChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text, style = bodyStyle(14, if (selected) Color.White else Palette.Ink, bold = true),
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) Palette.Accent else Palette.Card)
            .border(1.dp, if (selected) Palette.Accent else Palette.Line, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    )
}

@Composable
private fun AdjustSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = bodyStyle(14), modifier = Modifier.width(92.dp))
        Slider(
            value = value, onValueChange = onChange, valueRange = -1f..1f,
            colors = SliderDefaults.colors(
                thumbColor = Palette.Accent, activeTrackColor = Palette.Accent, inactiveTrackColor = Palette.Line
            ),
            modifier = Modifier.weight(1f)
        )
        Text(
            (value * 100).toInt().let { if (it > 0) "+$it" else "$it" }, style = bodyStyle(12, Palette.Muted),
            modifier = Modifier.width(36.dp)
        )
    }
}

@Suppress("unused")
private fun near(a: Float, b: Float) = abs(a - b) < 0.001f

@Suppress("unused")
private val unusedIcon: LineIcon = Icons.crop
