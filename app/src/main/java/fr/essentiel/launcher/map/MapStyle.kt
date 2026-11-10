package fr.essentiel.launcher.map

import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * La carte vient d'OpenFreeMap (style « positron », données OpenStreetMap).
 * On repeint toutes ses couches aux couleurs d'Essentiel : beige, orange, encre.
 */
object MapStyle {
    const val STYLE_URL = "https://tiles.openfreemap.org/styles/positron"

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

    fun apply(style: Style) {
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
                            PropertyFactory.textColor(MUTED), PropertyFactory.textHaloColor(BG), PropertyFactory.iconOpacity(0.55f)
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

    /** Couches ajoutées par Essentiel : itinéraire, épingle, position. */
    private fun addOverlays(style: Style) {
        val firstLabel = style.layers.firstOrNull { it is SymbolLayer }?.id

        val route = GeoJsonSource(SRC_ROUTE).also { it.setGeoJson(MapApi.EMPTY) }
        style.addSource(route)
        val routeCasing = LineLayer("essentiel-route-casing", SRC_ROUTE).withProperties(
            PropertyFactory.lineColor("#FFFFFF"), PropertyFactory.lineWidth(9f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
        )
        val routeLine = LineLayer("essentiel-route-line", SRC_ROUTE).withProperties(
            PropertyFactory.lineColor(ACCENT), PropertyFactory.lineWidth(5.5f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
        )
        if (firstLabel != null) {
            style.addLayerBelow(routeCasing, firstLabel)
            style.addLayerBelow(routeLine, firstLabel)
        } else {
            style.addLayer(routeCasing); style.addLayer(routeLine)
        }

        val pin = GeoJsonSource(SRC_PIN).also { it.setGeoJson(MapApi.EMPTY) }
        style.addSource(pin)
        style.addLayer(
            CircleLayer("essentiel-pin-layer", SRC_PIN).withProperties(
                PropertyFactory.circleColor(ACCENT), PropertyFactory.circleRadius(10f),
                PropertyFactory.circleStrokeColor("#FFFFFF"), PropertyFactory.circleStrokeWidth(3.5f)
            )
        )

        val me = GeoJsonSource(SRC_ME).also { it.setGeoJson(MapApi.EMPTY) }
        style.addSource(me)
        style.addLayer(
            CircleLayer("essentiel-me-halo", SRC_ME).withProperties(
                PropertyFactory.circleColor(INK), PropertyFactory.circleRadius(18f), PropertyFactory.circleOpacity(0.12f)
            )
        )
        style.addLayer(
            CircleLayer("essentiel-me-dot", SRC_ME).withProperties(
                PropertyFactory.circleColor(INK), PropertyFactory.circleRadius(7f),
                PropertyFactory.circleStrokeColor("#FFFFFF"), PropertyFactory.circleStrokeWidth(3f)
            )
        )
    }

    fun setGeoJson(style: Style?, sourceId: String, json: String) {
        try { (style?.getSource(sourceId) as? GeoJsonSource)?.setGeoJson(json) } catch (e: Exception) { }
    }
}
