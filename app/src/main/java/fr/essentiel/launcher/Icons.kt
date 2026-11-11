package fr.essentiel.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp

/** Icône au trait dessinée sur une grille de 24×24 (style des icônes de la maquette). */
class LineIcon(
    val paths: List<String>,
    val circles: List<Triple<Float, Float, Float>> = emptyList(),
    val dots: List<Pair<Float, Float>> = emptyList(),
)

object Icons {
    val note = LineIcon(
        listOf("M7 3h10a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z", "M9 9h6", "M9 13h6")
    )
    val calendar = LineIcon(
        listOf("M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z", "M16 2v4", "M8 2v4", "M3 10h18")
    )
    val timer = LineIcon(listOf("M10 2h4", "M12 10v4"), circles = listOf(Triple(12f, 14f, 8f)))
    val list = LineIcon(
        listOf("M9 6h12", "M9 12h12", "M9 18h12"),
        dots = listOf(4f to 6f, 4f to 12f, 4f to 18f)
    )
    val phone = LineIcon(
        listOf(
            "M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 " +
                "19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 " +
                "2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 " +
                "12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"
        )
    )
    val message = LineIcon(listOf("M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"))
    val camera = LineIcon(
        listOf("M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3l-2.5-3z"),
        circles = listOf(Triple(12f, 13f, 3f))
    )
    val mic = LineIcon(
        listOf("M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3z", "M19 10v2a7 7 0 0 1-14 0v-2", "M12 19v3")
    )

    // Météo
    val sun = LineIcon(
        listOf(
            "M12 2v2", "M12 20v2", "M4.93 4.93l1.41 1.41", "M17.66 17.66l1.41 1.41",
            "M2 12h2", "M20 12h2", "M6.34 17.66l-1.41 1.41", "M19.07 4.93l-1.41 1.41"
        ),
        circles = listOf(Triple(12f, 12f, 4f))
    )
    val moon = LineIcon(listOf("M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9z"))
    val cloud = LineIcon(listOf("M17.5 19H9a7 7 0 1 1 6.71-9h1.79a4.5 4.5 0 1 1 0 9z"))
    private const val CLOUD_TOP = "M4 14.9A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 2.5 8.24"
    val rain = LineIcon(listOf(CLOUD_TOP, "M16 14v6", "M8 14v6", "M12 16v6"))
    val snow = LineIcon(
        listOf(CLOUD_TOP),
        dots = listOf(8f to 15f, 8f to 19f, 12f to 17f, 12f to 21f, 16f to 15f, 16f to 19f)
    )
    val storm = LineIcon(listOf(CLOUD_TOP, "M13 11l-4 6h6l-4 6"))
    val fog = LineIcon(listOf("M4 8h16", "M3 12h18", "M5 16h14"))

    // Apps intégrées
    val back = LineIcon(listOf("M15 18l-6-6 6-6"))
    val chevronRight = LineIcon(listOf("M9 18l6-6-6-6"))
    val plus = LineIcon(listOf("M12 5v14", "M5 12h14"))
    val close = LineIcon(listOf("M18 6L6 18", "M6 6l12 12"))
    val check = LineIcon(listOf("M20 6L9 17l-5-5"))
    val search = LineIcon(listOf("M21 21l-4.35-4.35"), circles = listOf(Triple(11f, 11f, 7f)))
    val trash = LineIcon(
        listOf("M3 6h18", "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6", "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2")
    )
    val star = LineIcon(
        listOf("M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z")
    )
    val send = LineIcon(listOf("M22 2L11 13", "M22 2l-7 20-4-9-9-4 20-7z"))
    val edit = LineIcon(listOf("M17 3a2.85 2.85 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5z"))
    val share = LineIcon(
        listOf("M8.59 13.51l6.83 3.98", "M15.41 6.51l-6.82 3.98"),
        circles = listOf(Triple(18f, 5f, 3f), Triple(6f, 12f, 3f), Triple(18f, 19f, 3f))
    )
    val flip = LineIcon(
        listOf(
            "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8", "M21 3v5h-5",
            "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16", "M8 16H3v5"
        )
    )
    val flash = LineIcon(listOf("M13 2L3 14h9l-1 8 10-12h-9l1-8z"))
    val flashOff = LineIcon(listOf("M13 2L3 14h9l-1 8 10-12h-9l1-8z", "M2 2l20 20"))
    val video = LineIcon(
        listOf("M23 7l-7 5 7 5V7z", "M3 5h11a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z")
    )
    val image = LineIcon(
        listOf("M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z", "M21 15l-5-5L5 21"),
        circles = listOf(Triple(9f, 9f, 2f))
    )
    val keypad = LineIcon(
        emptyList(),
        dots = listOf(6f to 5f, 12f to 5f, 18f to 5f, 6f to 11f, 12f to 11f, 18f to 11f, 6f to 17f, 12f to 17f, 18f to 17f, 12f to 22f)
    )
    val speaker = LineIcon(
        listOf("M11 5L6 9H2v6h4l5 4V5z", "M15.54 8.46a5 5 0 0 1 0 7.07", "M19.07 4.93a10 10 0 0 1 0 14.14")
    )
    val micOff = LineIcon(
        listOf("M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3z", "M19 10v2a7 7 0 0 1-14 0v-2", "M12 19v3", "M2 2l20 20")
    )
    val pause = LineIcon(listOf("M9 5v14", "M15 5v14"))
    val users = LineIcon(
        listOf("M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2", "M22 21v-2a4 4 0 0 0-3-3.87", "M16 3.13a4 4 0 0 1 0 7.75"),
        circles = listOf(Triple(9f, 7f, 4f))
    )
    val user = LineIcon(listOf("M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"), circles = listOf(Triple(12f, 7f, 4f)))
    val bell = LineIcon(listOf("M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9", "M10.3 21a1.94 1.94 0 0 0 3.4 0"))
    val backspace = LineIcon(listOf("M21 4H8l-7 8 7 8h13a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2z", "M18 9l-6 6", "M12 9l6 6"))
    val clock = LineIcon(listOf("M12 6v6l4 2"), circles = listOf(Triple(12f, 12f, 10f)))
    val play = LineIcon(listOf("M6 3l14 9-14 9V3z"))
    val missed = LineIcon(listOf("M22 2l-6 6", "M16 2l6 6"))
    val incoming = LineIcon(listOf("M16 2v6h6", "M22 2l-6 6"))
    val outgoing = LineIcon(listOf("M16 2h6v6", "M16 8l6-6"))
    val locate = LineIcon(
        listOf("M2 12h3", "M19 12h3", "M12 2v3", "M12 19v3"),
        circles = listOf(Triple(12f, 12f, 7f)),
        dots = listOf(12f to 12f)
    )
    val gear = LineIcon(
        listOf(
            "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z"
        ),
        circles = listOf(Triple(12f, 12f, 3f))
    )
    val speakerOff = LineIcon(listOf("M11 5L6 9H2v6h4l5 4V5z", "M22 9l-6 6", "M16 9l6 6"))
    val crop = LineIcon(listOf("M6 2v14a2 2 0 0 0 2 2h14", "M18 22V8a2 2 0 0 0-2-2H2"))
    val rotate = LineIcon(listOf("M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8", "M21 3v5h-5"))
    val sliders = LineIcon(
        listOf("M4 21v-7", "M4 10V3", "M12 21v-9", "M12 8V3", "M20 21v-5", "M20 12V3", "M1 14h6", "M9 8h6", "M17 16h6")
    )
    val sparkles = LineIcon(
        listOf("M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9z", "M5 3v4", "M3 5h4", "M19 17v4", "M17 19h4")
    )
    val info = LineIcon(listOf("M12 16v-4", "M12 8h.01"), circles = listOf(Triple(12f, 12f, 10f)))
    val grid = LineIcon(listOf("M3 9h18", "M3 15h18", "M9 3v18", "M15 3v18"))
    val home = LineIcon(listOf("M3 10l9-7 9 7v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z", "M9 22V12h6v10"))
    val heart = LineIcon(listOf("M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7z"))
    val music = LineIcon(listOf("M9 18V5l12-2v13"), circles = listOf(Triple(6f, 18f, 3f), Triple(18f, 16f, 3f)))
    val globe = LineIcon(listOf("M2 12h20", "M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"), circles = listOf(Triple(12f, 12f, 10f)))
    val cart = LineIcon(listOf("M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6"), circles = listOf(Triple(9f, 21f, 1f), Triple(20f, 21f, 1f)))
    val card = LineIcon(listOf("M3 5h18a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z", "M1 10h22"))
    val book = LineIcon(listOf("M4 19.5A2.5 2.5 0 0 1 6.5 17H20", "M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"))
    val train = LineIcon(listOf("M6 3h12a2 2 0 0 1 2 2v10a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3V5a2 2 0 0 1 2-2z", "M4 11h16", "M8 22l2-4", "M16 22l-2-4"), dots = listOf(8f to 15f, 16f to 15f))
    val mail = LineIcon(listOf("M4 4h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z", "M22 6l-10 7L2 6"))
    val traffic = LineIcon(
        listOf("M9 2h6a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2z", "M7 6H4", "M7 12H4", "M7 18H4", "M17 6h3", "M17 12h3", "M17 18h3"),
        circles = listOf(Triple(12f, 6.5f, 1.6f), Triple(12f, 12f, 1.6f), Triple(12f, 17.5f, 1.6f))
    )
    val cloudSun = LineIcon(
        listOf("M12 2v2", "M4.93 4.93l1.41 1.41", "M20 12h2", "M19.07 4.93l-1.41 1.41", "M15.95 12.65a4 4 0 0 0-5.93-4.24",
            "M13 22H7a5 5 0 1 1 4.9-6H13a3 3 0 0 1 0 6z")
    )
    val drop = LineIcon(listOf("M12 22a7 7 0 0 0 7-7c0-2-1-3.9-3-5.5s-3.5-4-4-6.5c-.5 2.5-2 4.9-4 6.5C6 11.1 5 13 5 15a7 7 0 0 0 7 7z"))
    val wind = LineIcon(listOf("M17.7 7.7a2.5 2.5 0 1 1 1.8 4.3H2", "M9.6 4.6A2 2 0 1 1 11 8H2", "M12.6 19.4A2 2 0 1 0 14 16H2"))
    val thermometer = LineIcon(listOf("M14 4v10.54a4 4 0 1 1-4 0V4a2 2 0 0 1 4 0z"))
    val gauge = LineIcon(listOf("M12 14l4-4", "M3.34 19a10 10 0 1 1 17.32 0"))
    val eye = LineIcon(listOf("M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7z"), circles = listOf(Triple(12f, 12f, 3f)))
    val sunrise = LineIcon(listOf("M12 2v8", "M4.93 10.93l1.41 1.41", "M2 18h2", "M20 18h2", "M19.07 10.93l-1.41 1.41", "M22 22H2", "M8 6l4-4 4 4", "M16 18a4 4 0 0 0-8 0"))
    val leaf = LineIcon(listOf("M11 20A7 7 0 0 1 9.8 6.1C15.5 5 17 4.48 19 2c1 2 2 4.18 2 8 0 5.5-4.78 10-10 10z", "M2 21c0-3 1.85-5.36 5.08-6C9.5 14.52 12 13 13 12"))
    val lock = LineIcon(listOf("M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2z", "M7 11V7a5 5 0 0 1 10 0v4"))
    val tabs = LineIcon(listOf("M8 8h12a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V10a2 2 0 0 1 2-2z", "M4 16a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2"))
    val more = LineIcon(emptyList(), dots = listOf(5f to 12f, 12f to 12f, 19f to 12f))
    val calc = LineIcon(listOf("M6 2h12a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2z", "M8 6h8v4H8z"), dots = listOf(8f to 14f, 12f to 14f, 16f to 14f, 8f to 18f, 12f to 18f, 16f to 18f))
    val map = LineIcon(
        listOf("M9 4L3 6v14l6-2 6 2 6-2V4l-6 2-6-2z", "M9 4v14", "M15 6v14")
    )
}

@Composable
fun LineIconView(
    icon: LineIcon,
    color: Color,
    size: Dp,
    strokeWidth: Float = 2f,
    modifier: Modifier = Modifier,
) {
    val parsed = remember(icon) { icon.paths.map { PathParser().parsePathString(it).toPath() } }
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 24f
        scale(s, s, pivot = Offset.Zero) {
            val st = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            parsed.forEach { drawPath(it, color, style = st) }
            icon.circles.forEach { (x, y, r) -> drawCircle(color, r, Offset(x, y), style = st) }
            icon.dots.forEach { (x, y) -> drawCircle(color, 1.5f, Offset(x, y)) }
        }
    }
}
