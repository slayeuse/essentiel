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
}

@Composable
fun LineIconView(icon: LineIcon, color: Color, size: Dp, strokeWidth: Float = 2f) {
    val parsed = remember(icon) { icon.paths.map { PathParser().parsePathString(it).toPath() } }
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension / 24f
        scale(s, s, pivot = Offset.Zero) {
            val st = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            parsed.forEach { drawPath(it, color, style = st) }
            icon.circles.forEach { (x, y, r) -> drawCircle(color, r, Offset(x, y), style = st) }
            icon.dots.forEach { (x, y) -> drawCircle(color, 1.5f, Offset(x, y)) }
        }
    }
}
