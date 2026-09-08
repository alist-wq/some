package com.example.navigation.app

import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.maps.Style
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

/** Renders navigation primitives as separate layers so route, estimate, direction, and destination remain distinguishable. */
object MapLibreNavigationRenderer {
    fun render(style: Style, state: NavigationMapState) {
        val presentation = NavigationMapPresentationFactory.create(state)
        route(style, presentation)
        uncertainty(style, presentation.uncertaintyCenter, presentation.uncertaintyRadiusMeters)
        point(style, POSITION_SOURCE, POSITION_LAYER, presentation.estimatedPosition.takeIf { presentation.showPrecisePositionMarker }, "#1565C0", 7f)
        direction(style, presentation)
        point(style, DESTINATION_SOURCE, DESTINATION_LAYER, presentation.destination, "#D32F2F", 8f)
    }

    fun renderManualOrigin(style: Style, origin: com.example.navigation.routing.GeoPoint?) {
        point(style, MANUAL_ORIGIN_SOURCE, MANUAL_ORIGIN_LAYER, origin, "#8E24AA", 9f)
    }

    /** Candidate routes are visually distinct until explicit navigation activation. */
    fun renderCandidateRoute(style: Style, geometry: List<com.example.navigation.routing.GeoPoint>) {
        val feature = geometry.takeIf { it.size >= 2 }?.let { points ->
            Feature.fromGeometry(LineString.fromLngLats(points.map { Point.fromLngLat(it.longitude, it.latitude) }))
        }
        line(style, CANDIDATE_ROUTE_SOURCE, CANDIDATE_ROUTE_LAYER, feature, "#7B1FA2", 4f)
    }

    private fun route(style: Style, presentation: NavigationMapPresentation) {
        val feature = presentation.routeGeometry.takeIf { it.size >= 2 }?.let { points ->
            Feature.fromGeometry(LineString.fromLngLats(points.map { Point.fromLngLat(it.longitude, it.latitude) }))
        }
        line(style, ROUTE_SOURCE, ROUTE_LAYER, feature, "#1565C0", 5f)
    }

    private fun direction(style: Style, presentation: NavigationMapPresentation) {
        val start = presentation.estimatedPosition
        val end = presentation.directionEnd
        val feature = if (start != null && end != null) Feature.fromGeometry(LineString.fromLngLats(listOf(
            Point.fromLngLat(start.longitude, start.latitude), Point.fromLngLat(end.longitude, end.latitude)
        ))) else null
        line(style, DIRECTION_SOURCE, DIRECTION_LAYER, feature, "#1B5E20", 4f)
    }

    private fun uncertainty(style: Style, center: com.example.navigation.routing.GeoPoint?, radiusMeters: Double?) {
        val feature = if (center == null || radiusMeters == null || radiusMeters <= 0.0) null else {
            Feature.fromGeometry(Polygon.fromLngLats(listOf(circle(center, radiusMeters))))
        }
        val source = style.getSource(UNCERTAINTY_SOURCE) as? GeoJsonSource
        if (source == null) {
            if (feature == null) {
                style.addSource(GeoJsonSource(UNCERTAINTY_SOURCE, emptyFeatureCollection()))
            } else {
                style.addSource(GeoJsonSource(UNCERTAINTY_SOURCE, feature))
            }
            style.addLayer(FillLayer(UNCERTAINTY_LAYER, UNCERTAINTY_SOURCE).withProperties(
                fillColor("#1565C0"), fillOpacity(0.18f)
            ))
        } else if (feature == null) source.setGeoJson(emptyFeatureCollection()) else source.setGeoJson(feature)
    }

    private fun circle(center: com.example.navigation.routing.GeoPoint, radiusMeters: Double): List<Point> =
        (0..CIRCLE_SEGMENTS).map { index ->
            val angle = 2.0 * Math.PI * index / CIRCLE_SEGMENTS
            val latitudeOffset = Math.toDegrees(radiusMeters * kotlin.math.cos(angle) / EARTH_RADIUS_METERS)
            val longitudeOffset = Math.toDegrees(radiusMeters * kotlin.math.sin(angle) /
                (EARTH_RADIUS_METERS * kotlin.math.cos(Math.toRadians(center.latitude))))
            Point.fromLngLat(center.longitude + longitudeOffset, center.latitude + latitudeOffset)
        }

    private fun line(style: Style, sourceId: String, layerId: String, feature: Feature?, color: String, width: Float) {
        val source = (style.getSource(sourceId) as? GeoJsonSource)
        if (source == null) {
            if (feature == null) {
                style.addSource(GeoJsonSource(sourceId, emptyFeatureCollection()))
            } else {
                style.addSource(GeoJsonSource(sourceId, feature))
            }
            style.addLayer(LineLayer(layerId, sourceId).withProperties(lineColor(color), lineWidth(width)))
        } else if (feature == null) source.setGeoJson(emptyFeatureCollection()) else source.setGeoJson(feature)
    }

    private fun point(style: Style, sourceId: String, layerId: String, point: com.example.navigation.routing.GeoPoint?, color: String, radius: Float) {
        val feature = point?.let { Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude)) }
        val source = (style.getSource(sourceId) as? GeoJsonSource)
        if (source == null) {
            if (feature == null) {
                style.addSource(GeoJsonSource(sourceId, emptyFeatureCollection()))
            } else {
                style.addSource(GeoJsonSource(sourceId, feature))
            }
            style.addLayer(CircleLayer(layerId, sourceId).withProperties(circleColor(color), circleRadius(radius)))
        } else if (feature == null) source.setGeoJson(emptyFeatureCollection()) else source.setGeoJson(feature)
    }

    private fun emptyFeatureCollection(): FeatureCollection = FeatureCollection.fromFeatures(emptyArray())

    private const val ROUTE_SOURCE = "navigation-route-source"
    private const val ROUTE_LAYER = "navigation-route-layer"
    private const val CANDIDATE_ROUTE_SOURCE = "navigation-candidate-route-source"
    private const val CANDIDATE_ROUTE_LAYER = "navigation-candidate-route-layer"
    private const val POSITION_SOURCE = "navigation-position-source"
    private const val POSITION_LAYER = "navigation-position-layer"
    private const val UNCERTAINTY_SOURCE = "navigation-uncertainty-source"
    private const val UNCERTAINTY_LAYER = "navigation-uncertainty-layer"
    private const val DIRECTION_SOURCE = "navigation-direction-source"
    private const val DIRECTION_LAYER = "navigation-direction-layer"
    private const val DESTINATION_SOURCE = "navigation-destination-source"
    private const val DESTINATION_LAYER = "navigation-destination-layer"
    private const val MANUAL_ORIGIN_SOURCE = "navigation-manual-origin-source"
    private const val MANUAL_ORIGIN_LAYER = "navigation-manual-origin-layer"
    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private const val CIRCLE_SEGMENTS = 32
}
