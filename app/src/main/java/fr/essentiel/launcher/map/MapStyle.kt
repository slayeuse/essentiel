package fr.essentiel.launcher.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Color as AndroidColor
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * La carte vient d'OpenFreeMap (style « positron », données OpenStreetMap).
 * On repeint toutes ses couches aux couleurs d'Essentiel : beige, orange, encre.
 */
object MapStyle {
    const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

    private const val BG = "#E6E3DE"
    private const val LAND_DARK = "#DEDAD3"
    private const val GREEN = "#D5D9C4"
    private const val WATER = "#C3D0CF"
    private const val WATER_TEXT = "#6F8A89"
    private const val BUILDING = "#D8D3CB"
    private const val ROAD = "#F8F6F2"
    private const val ROAD_CASING = "#D2CCC3"
    private const val ROAD_MAJOR = "#F6C9AE"
    private const val RAIL = "#BDB7AE"
    private const val BORDER = "#B4AEA5"
    private const val INK = "#1B1B1B"
    private const val MUTED = "#6E6C69"
    const val ACCENT = "#F2390C"

    /** Couches des points d'intérêt (restaurants, magasins…), pour pouvoir les toucher. */
    var poiLayers: Array<String> = emptyArray()
        private set

    fun apply(style: Style) {
        poiLayers = style.layers.filter { it is SymbolLayer && "poi" in it.id.lowercase() }.map { it.id }.toTypedArray()
        for (layer in style.layers) {
            val id = layer.id.lowercase()
            try {
                when (layer) {
                    is BackgroundLayer -> layer.setProperties(PropertyFactory.backgroundColor(BG))
                    is FillLayer -> when {
                        "water" in id || "ocean" in id -> layer.setProperties(PropertyFactory.fillColor(WATER))
                        listOf("park", "wood", "forest", "grass", "green", "landcover", "nature", "cemetery").any { it in id } ->
                            layer.setProperties(PropertyFactory.fillColor(GREEN))
                        "building" in id -> layer.setProperties(
                            PropertyFactory.fillColor(BUILDING), PropertyFactory.fillOutlineColor(ROAD_CASING)
                        )
                        listOf("landuse", "residential", "aeroway", "industrial").any { it in id } ->
                            layer.setProperties(PropertyFactory.fillColor(LAND_DARK))
                        else -> {}
                    }
                    is FillExtrusionLayer -> layer.setProperties(PropertyFactory.fillExtrusionColor(BUILDING))
                    is RasterLayer -> layer.setProperties(PropertyFactory.rasterOpacity(0.15f), PropertyFactory.rasterSaturation(-1f))
                    is LineLayer -> when {
                        "water" in id || "river" in id -> layer.setProperties(PropertyFactory.lineColor(WATER))
                        "boundary" in id || "admin" in id -> layer.setProperties(PropertyFactory.lineColor(BORDER))
                        "rail" in id || "transit" in id -> layer.setProperties(PropertyFactory.lineColor(RAIL))
                        "casing" in id -> layer.setProperties(PropertyFactory.lineColor(ROAD_CASING))
                        "motorway" in id || "trunk" in id || "major" in id -> layer.setProperties(PropertyFactory.lineColor(ROAD_MAJOR))
                        listOf("road", "highway", "street", "minor", "path", "bridge", "tunnel", "primary", "secondary", "tertiary", "service")
                            .any { it in id } -> layer.setProperties(PropertyFactory.lineColor(ROAD))
                        else -> {}
                    }
                    is SymbolLayer -> when {
                        "water" in id -> layer.setProperties(
                            PropertyFactory.textColor(WATER_TEXT), PropertyFactory.textHaloColor(BG)
                        )
                        "road" in id || "highway" in id || "street" in id -> layer.setProperties(
                            PropertyFactory.textColor(MUTED), PropertyFactory.textHaloColor(ROAD)
                        )
                        "poi" in id -> layer.setProperties(
                            PropertyFactory.textColor("#5E4A3F"), PropertyFactory.textHaloColor(BG), PropertyFactory.iconOpacity(0.9f)
                        )
                        else -> layer.setProperties(PropertyFactory.textColor(INK), PropertyFactory.textHaloColor(BG))
                    }
                    else -> {}
                }
            } catch (e: Exception) { }
        }
        addOverlays(style)
    }

    const val SRC_ROUTE = "essentiel-route"
    const val SRC_PIN = "essentiel-pin"
    const val SRC_ME = "essentiel-me"
    const val SRC_RESULTS = "essentiel-results"
    const val LAYER_RESULTS = "essentiel-results-dot"

    /** Couches ajoutées par Essentiel : itinéraire, épingle, position. */
    private fun addOverlays(style: Style) {
        val firstLabel = style.layers.firstOrNull { it is SymbolLayer }?.id

        val route = GeoJsonSource(SRC_ROUTE).also { it.setGeoJson(MapApi.EMPTY) }
        style.addSource(route)
        val selected = Expression.eq(Expression.get("sel"), Expression.literal(1))
        val other = Expression.eq(Expression.get("sel"), Expression.literal(0))
        val altLine = LineLayer(LAYER_ROUTE_ALT, SRC_ROUTE).withProperties(
            PropertyFactory.lineColor("#B3ABA0"), PropertyFactory.lineWidth(6f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
        ).withFilter(other)
        val routeCasing = LineLayer("essentiel-route-casing", SRC_ROUTE).withProperties(
            PropertyFactory.lineColor("#FFFFFF"), PropertyFactory.lineWidth(10f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
        ).withFilter(selected)
        val routeLine = LineLayer("essentiel-route-line", SRC_ROUTE).withProperties(
            PropertyFactory.lineColor(ACCENT), PropertyFactory.lineWidth(6f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
        ).withFilter(selected)
        if (firstLabel != null) {
            style.addLayerBelow(altLine, firstLabel)
            style.addLayerBelow(routeCasing, firstLabel)
            style.addLayerBelow(routeLine, firstLabel)
        } else {
            style.addLayer(altLine); style.addLayer(routeCasing); style.addLayer(routeLine)
        }

        // Résultats de recherche (plusieurs points avec leur nom)
        val font: Array<String>? = try {
            (style.layers.firstOrNull { it is SymbolLayer } as? SymbolLayer)?.textFont?.value
        } catch (e: Exception) { null }
        val results = GeoJsonSource(SRC_RESULTS).also { it.setGeoJson(MapApi.EMPTY) }
        style.addSource(results)
        style.addLayer(
            CircleLayer(LAYER_RESULTS, SRC_RESULTS).withProperties(
                PropertyFactory.circleColor(ACCENT), PropertyFactory.circleRadius(7f),
                PropertyFactory.circleStrokeColor("#FFFFFF"), PropertyFactory.circleStrokeWidth(2.5f)
            )
        )
        val label = SymbolLayer("essentiel-results-label", SRC_RESULTS).withProperties(
            PropertyFactory.textField(Expression.get("name")),
            PropertyFactory.textSize(12f),
            PropertyFactory.textOffset(arrayOf(0f, 1.1f)),
            PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
            PropertyFactory.textColor(INK),
            PropertyFactory.textHaloColor(BG),
            PropertyFactory.textHaloWidth(1.6f),
            PropertyFactory.textOptional(true),
        )
        if (font != null) label.setProperties(PropertyFactory.textFont(font))
        style.addLayer(label)

        val pin = GeoJsonSource(SRC_PIN).also { it.setGeoJson(MapApi.EMPTY) }
        style.addSource(pin)
        style.addLayer(
            CircleLayer("essentiel-pin-layer", SRC_PIN).withProperties(
                PropertyFactory.circleColor(ACCENT), PropertyFactory.circleRadius(10f),
                PropertyFactory.circleStrokeColor("#FFFFFF"), PropertyFactory.circleStrokeWidth(3.5f)
            )
        )

        // Ma position : une flèche orientée dans le sens du téléphone
        style.addImage(IMG_ARROW, arrowBitmap())
        val me = GeoJsonSource(SRC_ME).also { it.setGeoJson(MapApi.EMPTY) }
        style.addSource(me)
        style.addLayer(
            CircleLayer("essentiel-me-halo", SRC_ME).withProperties(
                PropertyFactory.circleColor(ACCENT), PropertyFactory.circleRadius(22f), PropertyFactory.circleOpacity(0.14f)
            )
        )
        style.addLayer(
            SymbolLayer("essentiel-me-arrow", SRC_ME).withProperties(
                PropertyFactory.iconImage(IMG_ARROW),
                PropertyFactory.iconRotate(Expression.get("bearing")),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_MAP),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconSize(0.5f),
            )
        )
    }

    const val LAYER_ROUTE_ALT = "essentiel-route-alt"
    private const val IMG_ARROW = "essentiel-arrow"
    private const val SRC_TRAFFIC = "essentiel-traffic"
    private const val LAYER_TRAFFIC = "essentiel-traffic-layer"

    /** Flèche de position : rond blanc, flèche orange. */
    private fun arrowBitmap(): Bitmap {
        val size = 120
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = AndroidColor.argb(60, 0, 0, 0)
        c.drawCircle(60f, 63f, 46f, p)
        p.color = AndroidColor.WHITE
        c.drawCircle(60f, 60f, 46f, p)
        p.color = AndroidColor.parseColor(ACCENT)
        val path = Path().apply {
            moveTo(60f, 22f); lineTo(88f, 90f); lineTo(60f, 76f); lineTo(32f, 90f); close()
        }
        c.drawPath(path, p)
        return b
    }

    /** Couche trafic TomTom (avec une clé). */
    fun setTraffic(style: Style?, key: String?) {
        val s = style ?: return
        try {
            s.getLayer(LAYER_TRAFFIC)?.let { s.removeLayer(it) }
            s.getSource(SRC_TRAFFIC)?.let { s.removeSource(it) }
            if (key.isNullOrBlank()) return
            val url = "https://api.tomtom.com/traffic/map/4/tile/flow/relative0/{z}/{x}/{y}.png?key=" + key.trim() + "&tileSize=256"
            s.addSource(RasterSource(SRC_TRAFFIC, TileSet("2.2.0", url), 256))
            val layer = RasterLayer(LAYER_TRAFFIC, SRC_TRAFFIC).withProperties(PropertyFactory.rasterOpacity(0.8f))
            val below = s.layers.firstOrNull { it is SymbolLayer }?.id
            if (below != null) s.addLayerBelow(layer, below) else s.addLayer(layer)
        } catch (e: Exception) { }
    }

    fun setGeoJson(style: Style?, sourceId: String, json: String) {
        try { (style?.getSource(sourceId) as? GeoJsonSource)?.setGeoJson(json) } catch (e: Exception) { }
    }
}
