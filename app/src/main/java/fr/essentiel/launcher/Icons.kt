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
