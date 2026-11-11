package fr.essentiel.launcher.memos

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import fr.essentiel.launcher.Divider
import fr.essentiel.launcher.EmptyText
import fr.essentiel.launcher.Field
import fr.essentiel.launcher.IconAction
import fr.essentiel.launcher.Icons
import fr.essentiel.launcher.LineIconView
import fr.essentiel.launcher.Nav
import fr.essentiel.launcher.Palette
import fr.essentiel.launcher.PermissionGate
import fr.essentiel.launcher.ToolScaffold
import fr.essentiel.launcher.bodyStyle
import fr.essentiel.launcher.clock.formatDuration
import fr.essentiel.launcher.serifStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Memo(val file: File, val name: String, val date: Long, val durationMs: Long)

object MemoStore {
    val version = mutableIntStateOf(0)

    fun dir(ctx: Context): File = File(ctx.filesDir, "memos").apply { mkdirs() }

    fun all(ctx: Context): List<Memo> =
        (dir(ctx).listFiles { f -> f.extension == "m4a" } ?: emptyArray())
            .map { f -> Memo(f, f.nameWithoutExtension, f.lastModified(), duration(f)) }
            .sortedByDescending { it.date }

    private fun duration(f: File): Long = try {
        val r = MediaMetadataRetriever()
        r.setDataSource(f.absolutePath)
        val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        r.release()
        d
    } catch (e: Exception) { 0L }

    fun newFile(ctx: Context): File {
        val name = "Mémo " + SimpleDateFormat("d MMM HH'h'mm", Locale.FRANCE).format(Date())
        var f = File(dir(ctx), "$name.m4a")
        var i = 2
        while (f.exists()) { f = File(dir(ctx), "$name ($i).m4a"); i++ }
        return f
    }

    fun rename(ctx: Context, memo: Memo, newName: String): Boolean {
        val clean = newName.replace(Regex("[\\\\/:*?\"<>|]"), "").trim()
        if (clean.isEmpty()) return false
        val target = File(dir(ctx), "$clean.m4a")
        if (target.exists()) return false
        val ok = memo.file.renameTo(target)
        version.intValue = version.intValue + 1
        return ok
    }

    fun delete(memo: Memo) {
        memo.file.delete()
        version.intValue = version.intValue + 1
    }

    fun share(ctx: Context, memo: Memo) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", memo.file)
        val send = Intent(Intent.ACTION_SEND).setType("audio/mp4")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, "Partager le mémo").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
fun MemosScreen(nav: Nav) {
    ToolScaffold("mémos", subtitle = "enregistrements vocaux") {
        PermissionGate(arrayOf(Manifest.permission.RECORD_AUDIO), "Pour enregistrer des mémos, Essentiel a besoin du micro.") {
            MemosContent()
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun MemosContent() {
    val ctx = LocalContext.current
    val version = MemoStore.version.intValue
    var memos by remember { mutableStateOf<List<Memo>>(emptyList()) }
    LaunchedEffect(version) { memos = withContext(Dispatchers.IO) { MemoStore.all(ctx) } }

    // ----- Enregistrement -----
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recFile by remember { mutableStateOf<File?>(null) }
    var recStart by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(0L) }
    val levels = remember { mutableStateListOf<Float>() }

    fun stopRecording() {
        val r = recorder ?: return
        try { r.stop() } catch (e: Exception) { recFile?.delete() }
        r.release()
        recorder = null
        levels.clear()
        MemoStore.version.intValue = MemoStore.version.intValue + 1
    }

    fun startRecording() {
        val f = MemoStore.newFile(ctx)
        try {
            val r = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44_100)
                setOutputFile(f.absolutePath)
                prepare()
                start()
            }
            recorder = r; recFile = f; recStart = System.currentTimeMillis()
        } catch (e: Exception) {
            f.delete()
            Toast.makeText(ctx, "Impossible d'enregistrer", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(recorder) {
        while (recorder != null) {
            now = System.currentTimeMillis()
            val amp = try { recorder?.maxAmplitude ?: 0 } catch (e: Exception) { 0 }
            levels.add((amp / 32767f).coerceIn(0.03f, 1f))
            if (levels.size > 40) levels.removeAt(0)
            delay(80)
        }
    }

    // ----- Lecture -----
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf<File?>(null) }
    var position by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }

    fun stopPlayback() {
        player?.release(); player = null; playing = null; position = 0; paused = false
    }

    fun play(memo: Memo) {
        if (playing == memo.file) {
            val p = player ?: return
            if (p.isPlaying) { p.pause(); paused = true } else { p.start(); paused = false }
            position = p.currentPosition
            return
        }
        stopPlayback()
        try {
            player = MediaPlayer().apply {
                setDataSource(memo.file.absolutePath)
                setOnCompletionListener { stopPlayback() }
                prepare()
                start()
            }
            playing = memo.file
        } catch (e: Exception) {
            Toast.makeText(ctx, "Lecture impossible", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(playing) {
        while (playing != null) { position = try { player?.currentPosition ?: 0 } catch (e: Exception) { 0 }; delay(200) }
    }

    DisposableEffect(Unit) { onDispose { stopRecording(); stopPlayback() } }

    var renaming by remember { mutableStateOf<Memo?>(null) }
    var deleting by remember { mutableStateOf<Memo?>(null) }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f)) {
            if (memos.isEmpty()) item { EmptyText("Aucun mémo. Touche le bouton pour enregistrer.") }
            items(memos, key = { it.file.absolutePath }) { m ->
                val isThis = playing == m.file
                Column(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(22.dp))
                        .background(if (isThis) Palette.Accent.copy(alpha = 0.08f) else Color(0xFFF2EFEA))
                        .border(1.dp, if (isThis) Palette.Accent.copy(alpha = 0.5f) else Palette.Line, RoundedCornerShape(22.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape)
                                .background(if (isThis) Palette.Accent else Palette.Card)
                                .border(1.dp, if (isThis) Palette.Accent else Palette.Line, CircleShape)
                                .clickable { play(m) },
                            contentAlignment = Alignment.Center,
                        ) {
                            val pausedHere = isThis && paused
                            LineIconView(
                                if (isThis && !pausedHere) Icons.pause else Icons.play,
                                if (isThis) Color.White else Palette.Accent, 20.dp
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f).clickable { play(m) }) {
                            Text(m.name, style = bodyStyle(17), maxLines = 1)
                            Text(
                                SimpleDateFormat("EEE d MMM · HH:mm", Locale.FRANCE).format(Date(m.date)) +
                                    " · " + formatDuration(m.durationMs),
                                style = bodyStyle(13, Palette.Muted)
                            )
                        }
                        IconAction(Icons.edit, Palette.Muted, 20.dp) { renaming = m }
                        IconAction(Icons.share, Palette.Muted, 20.dp) { MemoStore.share(ctx, m) }
                        IconAction(Icons.trash, Palette.Muted, 20.dp) { deleting = m }
                    }
                    if (isThis && m.durationMs > 0) {
                        Spacer(Modifier.height(8.dp))
                        val frac = (position.toFloat() / m.durationMs).coerceIn(0f, 1f)
                        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(Palette.Card)) {
                            Box(Modifier.fillMaxWidth(frac).height(4.dp).clip(RoundedCornerShape(50)).background(Palette.Accent))
                        }
                    }
                }
            }
        }

        // ----- Zone d'enregistrement -----
        Column(
            Modifier.fillMaxWidth().padding(bottom = 16.dp).clip(RoundedCornerShape(28.dp))
                .background(Color(0xFFF2EFEA)).border(1.dp, Palette.Line, RoundedCornerShape(28.dp))
                .padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (recorder != null) {
                Text(formatDuration(now - recStart), style = serifStyle(40))
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.height(44.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    levels.forEach { l ->
                        Box(Modifier.width(4.dp).height((44 * l).dp).clip(RoundedCornerShape(50)).background(Palette.Accent))
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            Box(
                Modifier
                    .size(78.dp)
                    .clip(CircleShape)
                    .border(4.dp, Palette.Accent, CircleShape)
                    .padding(8.dp)
                    .clip(if (recorder != null) RoundedCornerShape(12.dp) else CircleShape)
                    .background(Palette.Accent)
                    .clickable {
                        if (recorder != null) stopRecording() else { stopPlayback(); startRecording() }
                    }
            )
            Spacer(Modifier.height(8.dp))
            Text(if (recorder != null) "arrêter" else "enregistrer", style = bodyStyle(14, Palette.Muted))
        }
    }

    renaming?.let { m ->
        var name by remember(m) { mutableStateOf(m.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Renommer") },
            text = { Field(name, { name = it }, "nom", Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(onClick = {
                    if (!MemoStore.rename(ctx, m, name)) Toast.makeText(ctx, "Nom invalide ou déjà utilisé", Toast.LENGTH_SHORT).show()
                    renaming = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Annuler") } },
            containerColor = Palette.Bg,
        )
    }
    deleting?.let { m ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Supprimer « ${m.name} » ?") },
            confirmButton = {
                TextButton(onClick = {
                    if (playing == m.file) stopPlayback()
                    MemoStore.delete(m); deleting = null
                }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Annuler") } },
            containerColor = Palette.Bg,
        )
    }
}
