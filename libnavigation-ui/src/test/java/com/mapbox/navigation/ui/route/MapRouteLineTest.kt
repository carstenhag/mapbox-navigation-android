package com.mapbox.navigation.ui.route

import android.content.Context
import android.content.res.Resources
import android.content.res.TypedArray
import androidx.test.core.app.ApplicationProvider
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.core.constants.Constants
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.libnavigation.ui.R
import com.mapbox.mapboxsdk.location.LocationComponentConstants
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.style.layers.Layer
import com.mapbox.mapboxsdk.style.layers.LineLayer
import com.mapbox.mapboxsdk.style.layers.SymbolLayer
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import com.mapbox.navigation.ui.internal.ThemeSwitcher
import com.mapbox.navigation.ui.internal.route.MapRouteLayerProvider
import com.mapbox.navigation.ui.internal.route.MapRouteSourceProvider
import com.mapbox.navigation.ui.internal.route.RouteConstants
import com.mapbox.navigation.ui.internal.route.RouteConstants.ALTERNATIVE_ROUTE_CASING_LAYER_ID
import com.mapbox.navigation.ui.internal.route.RouteConstants.ALTERNATIVE_ROUTE_LAYER_ID
import com.mapbox.navigation.ui.internal.route.RouteConstants.PRIMARY_ROUTE_CASING_LAYER_ID
import com.mapbox.navigation.ui.internal.route.RouteConstants.PRIMARY_ROUTE_LAYER_ID
import com.mapbox.navigation.ui.internal.route.RouteConstants.PRIMARY_ROUTE_TRAFFIC_LAYER_ID
import com.mapbox.navigation.ui.internal.route.RouteConstants.WAYPOINT_LAYER_ID
import com.mapbox.navigation.ui.route.MapRouteLine.MapRouteLineSupport.findDistanceOfPointAlongLine
import com.mapbox.turf.TurfMeasurement
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Scanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MapRouteLineTest {

    lateinit var ctx: Context
    var styleRes: Int = 0
    lateinit var wayPointSource: GeoJsonSource
    lateinit var primaryRouteLineSource: GeoJsonSource
    lateinit var primaryRouteLineTrafficSource: GeoJsonSource
    lateinit var alternativeRouteLineSource: GeoJsonSource

    lateinit var mapRouteSourceProvider: MapRouteSourceProvider
    lateinit var layerProvider: MapRouteLayerProvider
    lateinit var alternativeRouteCasingLayer: LineLayer
    lateinit var alternativeRouteLayer: LineLayer
    lateinit var primaryRouteCasingLayer: LineLayer
    lateinit var primaryRouteLayer: LineLayer
    lateinit var primaryRouteTrafficLayer: LineLayer
    lateinit var waypointLayer: SymbolLayer

    lateinit var style: Style

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        styleRes = ThemeSwitcher.retrieveAttrResourceId(
            ctx,
            R.attr.navigationViewRouteStyle, R.style.NavigationMapRoute
        )
        alternativeRouteCasingLayer = mockk {
            every { id } returns ALTERNATIVE_ROUTE_CASING_LAYER_ID
        }

        alternativeRouteLayer = mockk {
            every { id } returns ALTERNATIVE_ROUTE_LAYER_ID
        }

        primaryRouteCasingLayer = mockk(relaxUnitFun = true) {
            every { id } returns PRIMARY_ROUTE_CASING_LAYER_ID
        }

        primaryRouteLayer = mockk(relaxUnitFun = true) {
            every { id } returns PRIMARY_ROUTE_LAYER_ID
        }

        waypointLayer = mockk {
            every { id } returns WAYPOINT_LAYER_ID
        }

        primaryRouteTrafficLayer = mockk(relaxUnitFun = true) {
            every { id } returns PRIMARY_ROUTE_TRAFFIC_LAYER_ID
        }

        style = mockk(relaxUnitFun = true) {
            every { getLayer(ALTERNATIVE_ROUTE_LAYER_ID) } returns alternativeRouteLayer
            every { getLayer(ALTERNATIVE_ROUTE_CASING_LAYER_ID) } returns alternativeRouteCasingLayer
            every { getLayer(PRIMARY_ROUTE_LAYER_ID) } returns primaryRouteLayer
            every { getLayer(PRIMARY_ROUTE_TRAFFIC_LAYER_ID) } returns primaryRouteTrafficLayer
            every { getLayer(PRIMARY_ROUTE_CASING_LAYER_ID) } returns primaryRouteCasingLayer
            every { getLayer(WAYPOINT_LAYER_ID) } returns waypointLayer
            every { isFullyLoaded } returns false
        }

        wayPointSource = mockk(relaxUnitFun = true)
        primaryRouteLineSource = mockk(relaxUnitFun = true)
        primaryRouteLineTrafficSource = mockk(relaxUnitFun = true)
        alternativeRouteLineSource = mockk(relaxUnitFun = true)

        mapRouteSourceProvider = mockk {
            every { build(RouteConstants.WAYPOINT_SOURCE_ID, any(), any()) } returns wayPointSource
            every { build(RouteConstants.PRIMARY_ROUTE_SOURCE_ID, any(), any()) } returns primaryRouteLineSource
            every { build(RouteConstants.PRIMARY_ROUTE_TRAFFIC_SOURCE_ID, any(), any()) } returns primaryRouteLineTrafficSource
            every { build(RouteConstants.ALTERNATIVE_ROUTE_SOURCE_ID, any(), any()) } returns alternativeRouteLineSource
        }
        layerProvider = mockk {
            every {
                initializeAlternativeRouteCasingLayer(
                    style,
                    1.0f,
                    -9273715
                )
            } returns alternativeRouteCasingLayer
            every {
                initializeAlternativeRouteLayer(
                    style,
                    true,
                    1.0f,
                    -7957339
                )
            } returns alternativeRouteLayer
            every {
                initializePrimaryRouteCasingLayer(
                    style,
                    1.0f,
                    -13665594
                )
            } returns primaryRouteCasingLayer
            every {
                initializePrimaryRouteLayer(
                    style,
                    true,
                    1.0f,
                    -11097861
                )
            } returns primaryRouteLayer
            every { initializeWayPointLayer(style, any(), any()) } returns waypointLayer
            every { initializePrimaryRouteTrafficLayer(style, true, 1.0f, -11097861) } returns primaryRouteTrafficLayer
        }
    }

    @Test
    fun getStyledColor() {
        val result = MapRouteLine.MapRouteLineSupport.getStyledColor(
            R.styleable.NavigationMapRoute_routeColor,
            R.color.mapbox_navigation_route_layer_blue,
            ctx,
            styleRes
        )

        assertEquals(-11097861, result)
    }

    @Test
    fun getPrimaryRoute() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val directionsRoute: DirectionsRoute = getDirectionsRoute(true)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        ).also { it.draw(listOf(directionsRoute)) }

        val result = mapRouteLine.getPrimaryRoute()

        assertEquals(result, directionsRoute)
    }

    @Test
    fun getLineStringForRoute() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val directionsRoute: DirectionsRoute = getDirectionsRoute(true)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        ).also { it.draw(listOf(directionsRoute)) }

        val result = mapRouteLine.getLineStringForRoute(directionsRoute)

        assertEquals(result.coordinates().size, 4)
    }

    @Test
    fun getLineStringForRouteWhenCalledWithUnknownRoute() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val directionsRoute: DirectionsRoute = getDirectionsRoute(true)
        val directionsRoute2: DirectionsRoute = getDirectionsRoute(true)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        ).also { it.draw(listOf(directionsRoute)) }

        val result = mapRouteLine.getLineStringForRoute(directionsRoute2)

        assertNotNull(result)
    }

    @Test
    fun retrieveRouteFeatureData() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val directionsRoute: DirectionsRoute = getDirectionsRoute(true)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        ).also { it.draw(listOf(directionsRoute)) }

        val result = mapRouteLine.retrieveRouteFeatureData()

        assertEquals(result.size, 1)
        assertEquals(result[0].route, directionsRoute)
    }

    @Test
    fun retrieveRouteLineStrings() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val directionsRoute: DirectionsRoute = getDirectionsRoute(true)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        ).also { it.draw(listOf(directionsRoute)) }

        val result = mapRouteLine.retrieveRouteLineStrings()

        assertEquals(result.size, 1)
    }

    @Test
    fun retrieveDirectionsRoutes() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val directionsRoute: DirectionsRoute = getDirectionsRoute(true)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        ).also { it.draw(listOf(directionsRoute)) }

        val result = mapRouteLine.retrieveDirectionsRoutes()

        assertEquals(result[0], directionsRoute)
    }

    @Test
    fun retrieveDirectionsRoutesPrimaryRouteIsFirstInList() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val primaryRoute: DirectionsRoute = getDirectionsRoute(true)
        val alternativeRoute: DirectionsRoute = getDirectionsRoute(false)
        val directionsRoutes = mutableListOf(primaryRoute, alternativeRoute)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        ).also { it.draw(directionsRoutes) }
        directionsRoutes.reverse()

        val result = mapRouteLine.retrieveDirectionsRoutes()

        assertEquals(result[0], primaryRoute)
        assertEquals(2, result.size)
    }

    @Test
    fun retrieveDirectionsRoutesWhenPrimaryRouteIsNull() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val firstRoute: DirectionsRoute = getDirectionsRoute(true)
        val secondRoute: DirectionsRoute = getDirectionsRoute(false)
        val firstRouteFeatureCollection = mockk<FeatureCollection> {
            every { features() } returns listOf()
        }
        val secondRouteFeatureCollection = mockk<FeatureCollection> {
            every { features() } returns listOf()
        }
        val directionsRoutes = listOf(
            RouteFeatureData(firstRoute, firstRouteFeatureCollection, mockk<LineString>()),
            RouteFeatureData(secondRoute, secondRouteFeatureCollection, mockk<LineString>())
        )
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            directionsRoutes,
            listOf(),
            false,
            false,
            mapRouteSourceProvider,
            0f,
            null
        )

        val result = mapRouteLine.retrieveDirectionsRoutes()

        assertEquals(2, result.size)
    }

    @Test
    fun getTopLayerId() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        )

        val result = mapRouteLine.getTopLayerId()

        assertEquals(result, "mapbox-navigation-waypoint-layer")
    }

    @Test
    fun updatePrimaryRouteIndex() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val directionsRoute: DirectionsRoute = getDirectionsRoute(true)
        val directionsRoute2: DirectionsRoute = getDirectionsRoute(true)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        ).also { it.draw(listOf(directionsRoute, directionsRoute2)) }

        assertEquals(mapRouteLine.getPrimaryRoute(), directionsRoute)

        mapRouteLine.updatePrimaryRouteIndex(directionsRoute2)
        val result = mapRouteLine.getPrimaryRoute()

        assertEquals(result, directionsRoute2)
    }

    @Test
    fun getStyledColorRecyclesAttributes() {
        val context = mockk<Context>()
        val resources = mockk<Resources>()
        val typedArray = mockk<TypedArray>(relaxUnitFun = true)
        every {
            context.obtainStyledAttributes(
                styleRes,
                R.styleable.NavigationMapRoute
            )
        } returns typedArray
        every { context.resources } returns resources
        every { context.getColor(R.color.mapbox_navigation_route_layer_blue) } returns 0
        every { resources.getColor(R.color.mapbox_navigation_route_layer_blue) } returns 0
        every { typedArray.getColor(R.styleable.NavigationMapRoute_routeColor, anyInt()) } returns 0

        MapRouteLine.MapRouteLineSupport.getStyledColor(
            R.styleable.NavigationMapRoute_routeColor,
            R.color.mapbox_navigation_route_layer_blue,
            context,
            styleRes
        )

        verify(exactly = 1) { typedArray.recycle() }
    }

    @Test
    fun getFloatStyledValue() {
        val result: Float = MapRouteLine.MapRouteLineSupport.getFloatStyledValue(
            R.styleable.NavigationMapRoute_alternativeRouteScale,
            1.0f,
            ctx,
            styleRes
        )

        assertEquals(1.0f, result)
    }

    @Test
    fun getFloatStyledValueRecyclesAttributes() {
        val context = mockk<Context>()
        val typedArray = mockk<TypedArray>(relaxUnitFun = true)
        every {
            context.obtainStyledAttributes(
                styleRes,
                R.styleable.NavigationMapRoute
            )
        } returns typedArray
        every {
            typedArray.getFloat(
                R.styleable.NavigationMapRoute_alternativeRouteScale,
                1.0f
            )
        } returns 1.0f

        MapRouteLine.MapRouteLineSupport.getFloatStyledValue(
            R.styleable.NavigationMapRoute_alternativeRouteScale,
            1.0f,
            context,
            styleRes
        )

        verify(exactly = 1) { typedArray.recycle() }
    }

    @Test
    fun getBooleanStyledValue() {
        val result = MapRouteLine.MapRouteLineSupport.getBooleanStyledValue(
            R.styleable.NavigationMapRoute_roundedLineCap,
            true,
            ctx,
            styleRes
        )

        assertEquals(true, result)
    }

    @Test
    fun getBooleanStyledValueRecyclesAttributes() {
        val context = mockk<Context>()
        val typedArray = mockk<TypedArray>(relaxUnitFun = true)
        every {
            context.obtainStyledAttributes(
                styleRes,
                R.styleable.NavigationMapRoute
            )
        } returns typedArray
        every {
            typedArray.getBoolean(
                R.styleable.NavigationMapRoute_roundedLineCap,
                true
            )
        } returns true

        MapRouteLine.MapRouteLineSupport.getBooleanStyledValue(
            R.styleable.NavigationMapRoute_roundedLineCap,
            true,
            context,
            styleRes
        )

        verify(exactly = 1) { typedArray.recycle() }
    }

    @Test
    fun getResourceStyledValue() {
        val result = MapRouteLine.MapRouteLineSupport.getResourceStyledValue(
            R.styleable.NavigationMapRoute_originWaypointIcon,
            R.drawable.ic_route_origin,
            ctx,
            styleRes
        )

        assertEquals(R.drawable.ic_route_origin, result)
    }

    @Test
    fun getResourceStyledValueRecyclesAttributes() {
        val context = mockk<Context>()
        val typedArray = mockk<TypedArray>(relaxUnitFun = true)
        every {
            context.obtainStyledAttributes(
                styleRes,
                R.styleable.NavigationMapRoute
            )
        } returns typedArray
        every {
            typedArray.getResourceId(
                R.styleable.NavigationMapRoute_originWaypointIcon,
                R.drawable.ic_route_origin
            )
        } returns R.drawable.ic_route_origin

        MapRouteLine.MapRouteLineSupport.getResourceStyledValue(
            R.styleable.NavigationMapRoute_originWaypointIcon,
            R.drawable.ic_route_origin,
            context,
            styleRes
        )

        verify(exactly = 1) { typedArray.recycle() }
    }

    @Test
    fun getBelowLayerWithNullLayerId() {
        val style = mockk<Style>()
        val layerApple = mockk<Layer>()
        val layerBanana = mockk<Layer>()
        val layerCantaloupe = mockk<Layer>()
        val layerDragonfruit = mockk<SymbolLayer>()
        val layers = listOf(layerApple, layerBanana, layerCantaloupe, layerDragonfruit)
        every { style.layers } returns layers
        every { layerApple.id } returns "layerApple"
        every { layerBanana.id } returns RouteConstants.MAPBOX_LOCATION_ID
        every { layerCantaloupe.id } returns "layerCantaloupe"
        every { layerDragonfruit.id } returns "layerDragonfruit"

        val result = MapRouteLine.MapRouteLineSupport.getBelowLayer(null, style)

        assertEquals("layerCantaloupe", result)
    }

    @Test
    fun getBelowLayerWithEmptyLayerId() {
        val style = mockk<Style>()
        val layerApple = mockk<Layer>()
        val layerBanana = mockk<Layer>()
        val layerCantaloupe = mockk<Layer>()
        val layerDragonfruit = mockk<SymbolLayer>()
        val layers = listOf(layerApple, layerBanana, layerCantaloupe, layerDragonfruit)
        every { style.layers } returns layers
        every { layerApple.id } returns "layerApple"
        every { layerBanana.id } returns RouteConstants.MAPBOX_LOCATION_ID
        every { layerCantaloupe.id } returns "layerCantaloupe"
        every { layerDragonfruit.id } returns "layerDragonfruit"

        val result = MapRouteLine.MapRouteLineSupport.getBelowLayer("", style)

        assertEquals("layerCantaloupe", result)
    }

    @Test
    fun getBelowLayerReturnsShadowLayerIdAsDefault() {
        val style = mockk<Style>()
        val layerApple = mockk<Layer>()
        val layerBanana = mockk<SymbolLayer>()
        val layers = listOf(layerApple, layerBanana)
        every { style.layers } returns layers
        every { layerApple.id } returns RouteConstants.MAPBOX_LOCATION_ID
        every { layerBanana.id } returns "layerBanana"

        val result = MapRouteLine.MapRouteLineSupport.getBelowLayer(null, style)

        assertEquals(LocationComponentConstants.SHADOW_LAYER, result)
    }

    @Test
    fun getBelowLayerReturnsInputIdIfFound() {
        val style = mockk<Style>()
        val layerApple = mockk<Layer>()
        val layerBanana = mockk<Layer>()
        val layerCantaloupe = mockk<Layer>()
        val layerDragonfruit = mockk<Layer>()
        val layers = listOf(layerApple, layerBanana, layerCantaloupe, layerDragonfruit)
        every { style.layers } returns layers
        every { layerApple.id } returns "layerApple"
        every { layerBanana.id } returns "layerBanana"
        every { layerCantaloupe.id } returns "layerCantaloupe"
        every { layerDragonfruit.id } returns "layerDragonfruit"

        val result = MapRouteLine.MapRouteLineSupport.getBelowLayer("layerBanana", style)

        assertEquals("layerBanana", result)
    }

    @Test
    fun getBelowLayerReturnsShadowLayerIfInputNotNullOrEmptyAndNotFound() {
        val style = mockk<Style>()
        val layerApple = mockk<Layer>()
        val layerBanana = mockk<Layer>()
        val layerCantaloupe = mockk<Layer>()
        val layerDragonfruit = mockk<Layer>()
        val layers = listOf(layerApple, layerBanana, layerCantaloupe, layerDragonfruit)
        every { style.layers } returns layers
        every { layerApple.id } returns "layerApple"
        every { layerBanana.id } returns "layerBanana"
        every { layerCantaloupe.id } returns "layerCantaloupe"
        every { layerDragonfruit.id } returns "layerDragonfruit"

        val result = MapRouteLine.MapRouteLineSupport.getBelowLayer("foobar", style)

        assertEquals(LocationComponentConstants.SHADOW_LAYER, result)
    }

    @Test
    fun generateFeatureCollectionContainsRoute() {
        val route = getDirectionsRoute(true)

        val result = MapRouteLine.MapRouteLineSupport.generateFeatureCollection(route)

        assertEquals(route, result.route)
    }

    @Test
    fun generateFeatureLineStringContainsCorrectCoordinates() {
        val route = getDirectionsRoute(true)

        val result = MapRouteLine.MapRouteLineSupport.generateFeatureCollection(route)

        assertEquals(4, result.lineString.coordinates().size)
    }

    @Test
    fun generateFeatureFeatureCollectionContainsCorrectFeatures() {
        val route = getDirectionsRoute(true)

        val result = MapRouteLine.MapRouteLineSupport.generateFeatureCollection(route)

        assertEquals(1, result.featureCollection.features()!!.size)
    }

    @Test
    fun buildRouteLineExpression() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val expectedExpression =
            "[\"step\", [\"line-progress\"], [\"rgba\", 0.0, 0.0, 0.0, 0.0], 0.2, [\"rgba\", 86.0, 168.0, 251.0, 1.0], 0.31436133, [\"rgba\", 86.0, 168.0, 251.0, 1.0], 0.92972755, [\"rgba\", 233.0, 51.0, 64.0, 1.0], 1.0003215, [\"rgba\", 86.0, 168.0, 251.0, 1.0]]"
        val route = getDirectionsRoute(true)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        ).also { it.draw(listOf(route)) }

        val expression = mapRouteLine.getExpressionAtOffset(.2f)

        assertEquals(expectedExpression, expression.toString())
    }

    @Test
    fun buildRouteLineExpressionMultileg() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val expectedExpression =
            "[\"step\", [\"line-progress\"], [\"rgba\", 0.0, 0.0, 0.0, 0.0], 0.0, [\"rgba\", 86.0, 168.0, 251.0, 1.0], 0.021346012, [\"rgba\", 86.0, 168.0, 251.0, 1.0], 0.06847635, [\"rgba\", 86.0, 168.0, 251.0, 1.0], 0.09496192, [\"rgba\", 243.0, 166.0, 79.0, 1.0], 0.1054035, [\"rgba\", 86.0, 168.0, 251.0, 1.0], 0.31133384, [\"rgba\", 86.0, 168.0, 251.0, 1.0], 0.31479248, [\"rgba\", 86.0, 168.0, 251.0, 1.0], 0.38133165, [\"rgba\", 86.0, 168.0, 251.0, 1.0], 0.38438845, [\"rgba\", 86.0, 168.0, 251.0, 1.0], 0.41593167, [\"rgba\", 86.0, 168.0, 251.0, 1.0], 0.45903113, [\"rgba\", 86.0, 168.0, 251.0, 1.0], 0.7533547, [\"rgba\", 243.0, 166.0, 79.0, 1.0], 0.7613792, [\"rgba\", 86.0, 168.0, 251.0, 1.0], 0.7993399, [\"rgba\", 243.0, 166.0, 79.0, 1.0], 0.8467529, [\"rgba\", 86.0, 168.0, 251.0, 1.0], 0.86620295, [\"rgba\", 243.0, 166.0, 79.0, 1.0], 0.8693383, [\"rgba\", 86.0, 168.0, 251.0, 1.0], 0.9069854, [\"rgba\", 233.0, 51.0, 64.0, 1.0], 0.9224731, [\"rgba\", 233.0, 51.0, 64.0, 1.0], 0.9338598, [\"rgba\", 86.0, 168.0, 251.0, 1.0], 0.9950478, [\"rgba\", 86.0, 168.0, 251.0, 1.0]]"
        val route = getMultilegRoute()
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        ).also { it.draw(listOf(route)) }

        val expression = mapRouteLine.getExpressionAtOffset(0f)

        assertEquals(expectedExpression, expression.toString())
    }

    @Test
    fun buildRouteLineExpressionWhenNoTraffic() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val expectedExpression =
            "[\"step\", [\"line-progress\"], [\"rgba\", 0.0, 0.0, 0.0, 0.0], 0.2, [\"rgba\", 86.0, 168.0, 251.0, 1.0]]"
        val route = getDirectionsRoute(false)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        ).also { it.draw(listOf(route)) }

        val expression = mapRouteLine.getExpressionAtOffset(.2f)

        assertEquals(expectedExpression, expression.toString())
    }

    @Test
    fun buildRouteLineExpressionOffsetAfterLastLeg() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val expectedExpression =
            "[\"step\", [\"line-progress\"], [\"rgba\", 0.0, 0.0, 0.0, 0.0], 0.9, [\"rgba\", 86.0, 168.0, 251.0, 1.0]]"
        val route = getDirectionsRoute(false)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        ).also { it.draw(listOf(route)) }

        val expression = mapRouteLine.getExpressionAtOffset(.9f)

        assertEquals(expectedExpression, expression.toString())
    }

    @Test
    fun calculateRouteLineSegmentsMultilegRoute() {
        val route = getMultilegRoute()
        val lineString = LineString.fromPolyline(route.geometry()!!, Constants.PRECISION_6)

        val result = MapRouteLine.MapRouteLineSupport.calculateRouteLineSegments(
            route,
            lineString,
            true
        ) { _, _ -> 1 }

        assertEquals(21, result.size)
    }

    @Test
    fun calculateRouteLineSegmentsMultilegRouteFirstDistanceValueAboveMinimumOffset() {
        val route = getMultilegRoute()
        val lineString = LineString.fromPolyline(route.geometry()!!, Constants.PRECISION_6)

        val result = MapRouteLine.MapRouteLineSupport.calculateRouteLineSegments(
            route,
            lineString,
            true
        ) { _, _ -> 1 }

        assertTrue(result[1].offset > .001f)
    }

    @Test
    fun calculateRouteLineSegmentFromCongestion() {
        val route = getMultilegRoute()
        val lineString = LineString.fromPolyline(route.geometry()!!, Constants.PRECISION_6)

        val result = MapRouteLine.MapRouteLineSupport.calculateRouteLineSegmentsFromCongestion(
            route.legs()!![0].annotation()!!.congestion()!!.toList(),
            lineString,
            route.distance()!!,
            true
        ) { _, _ -> 1 }

        assertEquals(9, result.size)
    }

    @Test
    fun buildWayPointFeatureCollection() {
        val route = getDirectionsRoute(true)

        val result = MapRouteLine.MapRouteLineSupport.buildWayPointFeatureCollection(route)

        assertEquals(2, result.features()!!.size)
    }

    @Test
    fun buildWayPointFeatureCollectionFirstFeatureOrigin() {
        val route = getDirectionsRoute(true)

        val result = MapRouteLine.MapRouteLineSupport.buildWayPointFeatureCollection(route)

        assertEquals("{\"wayPoint\":\"origin\"}", result.features()!![0].properties().toString())
    }

    @Test
    fun buildWayPointFeatureCollectionSecondFeatureOrigin() {
        val route = getDirectionsRoute(true)

        val result = MapRouteLine.MapRouteLineSupport.buildWayPointFeatureCollection(route)

        assertEquals(
            "{\"wayPoint\":\"destination\"}",
            result.features()!![1].properties().toString()
        )
    }

    @Test
    fun buildWayPointFeatureFromLeg() {
        val route = getDirectionsRoute(true)

        val result =
            MapRouteLine.MapRouteLineSupport.buildWayPointFeatureFromLeg(route.legs()!![0], 0)

        assertEquals(-122.523514, (result!!.geometry() as Point).coordinates()[0], 0.0)
        assertEquals(37.975355, (result.geometry() as Point).coordinates()[1], 0.0)
    }

    @Test
    fun buildWayPointFeatureFromLegContainsOriginWaypoint() {
        val route = getDirectionsRoute(true)

        val result =
            MapRouteLine.MapRouteLineSupport.buildWayPointFeatureFromLeg(route.legs()!![0], 0)

        assertEquals("\"origin\"", result!!.properties()!!["wayPoint"].toString())
    }

    @Test
    fun getRouteColorForCongestionPrimaryRouteCongestionModerate() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        )

        val result =
            mapRouteLine.getRouteColorForCongestion(RouteConstants.MODERATE_CONGESTION_VALUE, true)

        assertEquals(-809393, result)
    }

    @Test
    fun getRouteColorForCongestionPrimaryRouteCongestionHeavy() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        )

        val result =
            mapRouteLine.getRouteColorForCongestion(RouteConstants.HEAVY_CONGESTION_VALUE, true)

        assertEquals(-1494208, result)
    }

    @Test
    fun getRouteColorForCongestionPrimaryRouteCongestionSevere() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        )

        val result =
            mapRouteLine.getRouteColorForCongestion(RouteConstants.SEVERE_CONGESTION_VALUE, true)

        assertEquals(-1494208, result)
    }

    @Test
    fun getRouteColorForCongestionPrimaryRouteCongestionUnknown() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        )

        val result =
            mapRouteLine.getRouteColorForCongestion(RouteConstants.UNKNOWN_CONGESTION_VALUE, true)

        assertEquals(-11097861, result)
    }

    @Test
    fun getRouteColorForCongestionPrimaryRouteCongestionDefault() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        )

        val result = mapRouteLine.getRouteColorForCongestion("foobar", true)

        assertEquals(-11097861, result)
    }

    @Test
    fun getRouteColorForCongestionNonPrimaryRouteCongestionModerate() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        )

        val result =
            mapRouteLine.getRouteColorForCongestion(RouteConstants.MODERATE_CONGESTION_VALUE, false)

        assertEquals(-4881791, result)
    }

    @Test
    fun getRouteColorForCongestionNonPrimaryRouteCongestionHeavy() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        )

        val result =
            mapRouteLine.getRouteColorForCongestion(RouteConstants.HEAVY_CONGESTION_VALUE, false)

        assertEquals(-4881791, result)
    }

    @Test
    fun getRouteColorForCongestionNonPrimaryRouteCongestionSevere() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        )

        val result =
            mapRouteLine.getRouteColorForCongestion(RouteConstants.SEVERE_CONGESTION_VALUE, false)

        assertEquals(-4881791, result)
    }

    @Test
    fun getRouteColorForCongestionNonPrimaryRouteCongestionUnknown() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        )

        val result =
            mapRouteLine.getRouteColorForCongestion(RouteConstants.UNKNOWN_CONGESTION_VALUE, false)

        assertEquals(-7957339, result)
    }

    @Test
    fun getRouteColorForCongestionNonPrimaryRouteCongestionDefault() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        )

        val result = mapRouteLine.getRouteColorForCongestion("foobar", false)

        assertEquals(-7957339, result)
    }

    @Test
    fun reinitializeWithRoutes() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val route = getDirectionsRoute(true)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        )

        mapRouteLine.reinitializeWithRoutes(listOf(route))

        assertEquals(route, mapRouteLine.getPrimaryRoute())
    }

    @Test
    fun reinitializePrimaryRoute() {
        every { style.layers } returns listOf(primaryRouteLayer)
        every { style.isFullyLoaded } returns true
        every { style.getLayer(PRIMARY_ROUTE_TRAFFIC_LAYER_ID) } returns primaryRouteLayer
        every { primaryRouteLayer.setFilter(any()) } returns Unit
        every { primaryRouteCasingLayer.setFilter(any()) } returns Unit
        every { alternativeRouteLayer.setFilter(any()) } returns Unit
        every { alternativeRouteCasingLayer.setFilter(any()) } returns Unit
        every { primaryRouteTrafficLayer.setFilter(any()) } returns Unit
        every { waypointLayer.setFilter(any()) } returns Unit
        every { primaryRouteLayer.setProperties(any()) } returns Unit
        every { primaryRouteCasingLayer.setProperties(any()) } returns Unit
        every { alternativeRouteLayer.setProperties(any()) } returns Unit
        every { alternativeRouteCasingLayer.setProperties(any()) } returns Unit
        every { primaryRouteTrafficLayer.setProperties(any()) } returns Unit
        every { waypointLayer.setProperties(any()) } returns Unit
        every { style.getLayerAs<LineLayer>("mapbox-navigation-route-casing-layer") } returns primaryRouteCasingLayer

        val route = getDirectionsRoute(true)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        )

        mapRouteLine.reinitializeWithRoutes(listOf(route))
        mapRouteLine.reinitializePrimaryRoute()

        verify { primaryRouteLayer.setProperties(any()) }
    }

    @Test
    fun getExpressionAtOffsetWhenExpressionDataEmpty() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val expectedExpression = "[\"step\", [\"line-progress\"], [\"rgba\", 0.0, 0.0, 0.0, 0.0], 0.2, [\"rgba\", 86.0, 168.0, 251.0, 1.0]]"
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            listOf<RouteFeatureData>(),
            listOf<RouteLineExpressionData>(),
            true,
            false,
            mapRouteSourceProvider,
            0f,
            null
        )

        val expression = mapRouteLine.getExpressionAtOffset(.2f)

        assertEquals(expectedExpression, expression.toString())
    }

    @Test
    fun findDistanceOfPointAlongLine() {
        val lineString = LineString.fromPolyline(getDirectionsRoute().geometry()!!, Constants.PRECISION_6)
        val points = lineString.coordinates()
        val midPoint = TurfMeasurement.midpoint(points[4], points[5])
        val expectedDist = 150.09777136396087

        val result = findDistanceOfPointAlongLine(lineString, midPoint)

        assertEquals(expectedDist, result, 0.00000001)
    }

    @Test
    fun updateVanishingPoint() {
        every { style.layers } returns listOf(primaryRouteLayer)
        every { style.isFullyLoaded } returnsMany listOf(false, false, false, false, false, false, false, false, true, true, true)
        every { style.getLayerAs<LineLayer>("mapbox-navigation-route-casing-layer") } returns primaryRouteCasingLayer
        every { style.getLayer("mapbox-navigation-route-layer") } returns primaryRouteLayer
        every { style.getLayer("mapbox-navigation-route-traffic-layer") } returns primaryRouteTrafficLayer
        val route = getDirectionsRoute()
        val coordinates = LineString.fromPolyline(route.geometry()!!, Constants.PRECISION_6).coordinates()
        val inputPoint = TurfMeasurement.midpoint(coordinates[4], coordinates[5])
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        ).also { it.draw(listOf(route)) }

        mapRouteLine.updateTraveledRouteLine(inputPoint)

        verify { primaryRouteCasingLayer.setProperties(any()) }
        verify { primaryRouteLayer.setProperties(any()) }
        verify { primaryRouteTrafficLayer.setProperties(any()) }
    }

    @Test
    fun updateVanishingPointWhenPointDistanceBeyondThreshold() {
        every { style.layers } returns listOf(primaryRouteLayer)
        every { style.isFullyLoaded } returnsMany listOf(false, false, false, false, false, false, false, false, true, true, true)
        every { style.getLayerAs<LineLayer>("mapbox-navigation-route-casing-layer") } returns primaryRouteCasingLayer
        every { style.getLayer("mapbox-navigation-route-layer") } returns primaryRouteLayer
        every { style.getLayer("mapbox-navigation-route-traffic-layer") } returns primaryRouteTrafficLayer
        val route = getDirectionsRoute()
        val inputPoint = Point.fromLngLat(-122.508527, 37.974846)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        ).also { it.draw(listOf(route)) }

        mapRouteLine.updateTraveledRouteLine(inputPoint)

        verify(exactly = 0) { primaryRouteCasingLayer.setProperties(any()) }
        verify(exactly = 0) { primaryRouteLayer.setProperties(any()) }
        verify(exactly = 0) { primaryRouteTrafficLayer.setProperties(any()) }
    }

    private fun getDirectionsRoute(includeCongestion: Boolean): DirectionsRoute {
        val congestion = when (includeCongestion) {
            true -> "\"unknown\",\"heavy\",\"low\""
            false -> ""
        }
        val tokenHere = "someToken"
        val directionsRouteAsJson =
            "{\"routeIndex\":\"0\",\"distance\":66.9,\"duration\":45.0,\"geometry\":\"urylgArvfuhFjJ`CbC{[pAZ\",\"weight\":96.6,\"weight_name\":\"routability\",\"legs\":[{\"distance\":66.9,\"duration\":45.0,\"summary\":\"Laurel Place, Lincoln Avenue\",\"steps\":[{\"distance\":21.0,\"duration\":16.7,\"geometry\":\"urylgArvfuhFjJ`C\",\"name\":\"\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.523514,37.975355],\"bearing_before\":0.0,\"bearing_after\":196.0,\"instruction\":\"Head south\",\"type\":\"depart\",\"modifier\":\"right\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":21.0,\"announcement\":\"Head south, then turn left onto Laurel Place\",\"ssmlAnnouncement\":\"\\u003cspeak\\u003e\\u003camazon:effect name\\u003d\\\"drc\\\"\\u003e\\u003cprosody rate\\u003d\\\"1.08\\\"\\u003eHead south, then turn left onto Laurel Place\\u003c/prosody\\u003e\\u003c/amazon:effect\\u003e\\u003c/speak\\u003e\"},{\"distanceAlongGeometry\":18.9,\"announcement\":\"Turn left onto Laurel Place, then turn right onto Lincoln Avenue\",\"ssmlAnnouncement\":\"\\u003cspeak\\u003e\\u003camazon:effect name\\u003d\\\"drc\\\"\\u003e\\u003cprosody rate\\u003d\\\"1.08\\\"\\u003eTurn left onto Laurel Place, then turn right onto Lincoln Avenue\\u003c/prosody\\u003e\\u003c/amazon:effect\\u003e\\u003c/speak\\u003e\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":21.0,\"primary\":{\"text\":\"Laurel Place\",\"components\":[{\"text\":\"Laurel Place\",\"type\":\"text\",\"abbr\":\"Laurel Pl\",\"abbr_priority\":0}],\"type\":\"turn\",\"modifier\":\"left\"}},{\"distanceAlongGeometry\":18.9,\"primary\":{\"text\":\"Laurel Place\",\"components\":[{\"text\":\"Laurel Place\",\"type\":\"text\",\"abbr\":\"Laurel Pl\",\"abbr_priority\":0}],\"type\":\"turn\",\"modifier\":\"left\"},\"sub\":{\"text\":\"Lincoln Avenue\",\"components\":[{\"text\":\"Lincoln Avenue\",\"type\":\"text\",\"abbr\":\"Lincoln Ave\",\"abbr_priority\":0}],\"type\":\"turn\",\"modifier\":\"right\"}}],\"driving_side\":\"right\",\"weight\":52.6,\"intersections\":[{\"location\":[-122.523514,37.975355],\"bearings\":[196],\"entry\":[true],\"out\":0}]},{\"distance\":41.2,\"duration\":27.3,\"geometry\":\"igylgAtzfuhFbC{[\",\"name\":\"Laurel Place\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.523579,37.975173],\"bearing_before\":195.0,\"bearing_after\":99.0,\"instruction\":\"Turn left onto Laurel Place\",\"type\":\"turn\",\"modifier\":\"left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":22.6,\"announcement\":\"Turn right onto Lincoln Avenue, then you will arrive at your destination\",\"ssmlAnnouncement\":\"\\u003cspeak\\u003e\\u003camazon:effect name\\u003d\\\"drc\\\"\\u003e\\u003cprosody rate\\u003d\\\"1.08\\\"\\u003eTurn right onto Lincoln Avenue, then you will arrive at your destination\\u003c/prosody\\u003e\\u003c/amazon:effect\\u003e\\u003c/speak\\u003e\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":41.2,\"primary\":{\"text\":\"Lincoln Avenue\",\"components\":[{\"text\":\"Lincoln Avenue\",\"type\":\"text\",\"abbr\":\"Lincoln Ave\",\"abbr_priority\":0}],\"type\":\"turn\",\"modifier\":\"right\"}}],\"driving_side\":\"right\",\"weight\":43.0,\"intersections\":[{\"location\":[-122.523579,37.975173],\"bearings\":[15,105,285],\"entry\":[false,true,true],\"in\":0,\"out\":1}]},{\"distance\":4.7,\"duration\":1.0,\"geometry\":\"ecylgAx}euhFpAZ\",\"name\":\"Lincoln Avenue\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.523117,37.975107],\"bearing_before\":99.0,\"bearing_after\":194.0,\"instruction\":\"Turn right onto Lincoln Avenue\",\"type\":\"turn\",\"modifier\":\"right\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":4.7,\"announcement\":\"You have arrived at your destination\",\"ssmlAnnouncement\":\"\\u003cspeak\\u003e\\u003camazon:effect name\\u003d\\\"drc\\\"\\u003e\\u003cprosody rate\\u003d\\\"1.08\\\"\\u003eYou have arrived at your destination\\u003c/prosody\\u003e\\u003c/amazon:effect\\u003e\\u003c/speak\\u003e\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":4.7,\"primary\":{\"text\":\"You have arrived\",\"components\":[{\"text\":\"You have arrived\",\"type\":\"text\"}],\"type\":\"arrive\",\"modifier\":\"straight\"}}],\"driving_side\":\"right\",\"weight\":1.0,\"intersections\":[{\"location\":[-122.523117,37.975107],\"bearings\":[15,105,195,285],\"entry\":[true,true,true,false],\"in\":3,\"out\":2}]},{\"distance\":0.0,\"duration\":0.0,\"geometry\":\"s`ylgAt~euhF\",\"name\":\"Lincoln Avenue\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.523131,37.975066],\"bearing_before\":195.0,\"bearing_after\":0.0,\"instruction\":\"You have arrived at your destination\",\"type\":\"arrive\"},\"voiceInstructions\":[],\"bannerInstructions\":[],\"driving_side\":\"right\",\"weight\":0.0,\"intersections\":[{\"location\":[-122.523131,37.975066],\"bearings\":[15],\"entry\":[true],\"in\":0}]}],\"annotation\":{\"distance\":[21.030105037432428,41.16669115760234,4.722589365163041],\"congestion\":[$congestion]}}],\"routeOptions\":{\"baseUrl\":\"https://api.mapbox.com\",\"user\":\"mapbox\",\"profile\":\"driving-traffic\",\"coordinates\":[[-122.5237559,37.9754094],[-122.5231475,37.9750697]],\"alternatives\":true,\"language\":\"en\",\"continue_straight\":false,\"roundabout_exits\":false,\"geometries\":\"polyline6\",\"overview\":\"full\",\"steps\":true,\"annotations\":\"congestion,distance\",\"voice_instructions\":true,\"banner_instructions\":true,\"voice_units\":\"imperial\",\"access_token\":\"$tokenHere\",\"uuid\":\"ck9g2sbdk6pod7ynuece0r2yo\"},\"voiceLocale\":\"en-US\"}"
        return DirectionsRoute.fromJson(directionsRouteAsJson)
    }

    private fun getDirectionsRoute(): DirectionsRoute {
        val tokenHere = "someToken"
        val route = "{\"routeIndex\":\"0\",\"distance\":879.1,\"duration\":228.6,\"geometry\":\"miylgAniguhF{Cra@iBdVa@nFtThE`RpDpFfBr]xEvCd@nU~DUbCoBnd@vn@lC~EVzRj@jOfA~Rr@iAbQiBh^o@|N[fSlUjBbPpAnTfB|FeiA\",\"weight\":396.4,\"weight_name\":\"routability\",\"legs\":[{\"distance\":879.1,\"duration\":228.6,\"summary\":\"Nye Street, Lootens Place\",\"steps\":[{\"distance\":93.1,\"duration\":24.4,\"geometry\":\"miylgAniguhF{Cra@iBdVa@nF\",\"name\":\"Laurel Place\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.523816,37.975207],\"bearing_before\":0.0,\"bearing_after\":280.0,\"instruction\":\"Head west on Laurel Place\",\"type\":\"depart\",\"modifier\":\"right\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":93.1,\"announcement\":\"Head west on Laurel Place, then turn left onto Nye Street\",\"ssmlAnnouncement\":\"\\u003cspeak\\u003e\\u003camazon:effect name\\u003d\\\"drc\\\"\\u003e\\u003cprosody rate\\u003d\\\"1.08\\\"\\u003eHead west on Laurel Place, then turn left onto Nye Street\\u003c/prosody\\u003e\\u003c/amazon:effect\\u003e\\u003c/speak\\u003e\"},{\"distanceAlongGeometry\":57.2,\"announcement\":\"Turn left onto Nye Street\",\"ssmlAnnouncement\":\"\\u003cspeak\\u003e\\u003camazon:effect name\\u003d\\\"drc\\\"\\u003e\\u003cprosody rate\\u003d\\\"1.08\\\"\\u003eTurn left onto Nye Street\\u003c/prosody\\u003e\\u003c/amazon:effect\\u003e\\u003c/speak\\u003e\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":93.1,\"primary\":{\"text\":\"Nye Street\",\"components\":[{\"text\":\"Nye Street\",\"type\":\"text\",\"abbr\":\"Nye St\",\"abbr_priority\":0}],\"type\":\"turn\",\"modifier\":\"left\"}}],\"driving_side\":\"right\",\"weight\":54.9,\"intersections\":[{\"location\":[-122.523816,37.975207],\"bearings\":[280],\"entry\":[true],\"out\":0}]},{\"distance\":193.5,\"duration\":57.7,\"geometry\":\"urylgAxjiuhFtThE`RpDpFfBr]xEvCd@nU~D\",\"name\":\"Nye Street\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.524861,37.975355],\"bearing_before\":279.0,\"bearing_after\":192.0,\"instruction\":\"Turn left onto Nye Street\",\"type\":\"turn\",\"modifier\":\"left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":173.5,\"announcement\":\"In 600 feet, turn right onto 5th Avenue\",\"ssmlAnnouncement\":\"\\u003cspeak\\u003e\\u003camazon:effect name\\u003d\\\"drc\\\"\\u003e\\u003cprosody rate\\u003d\\\"1.08\\\"\\u003eIn 600 feet, turn right onto \\u003csay-as interpret-as\\u003d\\\"address\\\"\\u003e5th\\u003c/say-as\\u003e Avenue\\u003c/prosody\\u003e\\u003c/amazon:effect\\u003e\\u003c/speak\\u003e\"},{\"distanceAlongGeometry\":50.3,\"announcement\":\"Turn right onto 5th Avenue, then turn left onto Lootens Place\",\"ssmlAnnouncement\":\"\\u003cspeak\\u003e\\u003camazon:effect name\\u003d\\\"drc\\\"\\u003e\\u003cprosody rate\\u003d\\\"1.08\\\"\\u003eTurn right onto \\u003csay-as interpret-as\\u003d\\\"address\\\"\\u003e5th\\u003c/say-as\\u003e Avenue, then turn left onto Lootens Place\\u003c/prosody\\u003e\\u003c/amazon:effect\\u003e\\u003c/speak\\u003e\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":193.5,\"primary\":{\"text\":\"5th Avenue\",\"components\":[{\"text\":\"5th Avenue\",\"type\":\"text\",\"abbr\":\"5th Ave\",\"abbr_priority\":0}],\"type\":\"turn\",\"modifier\":\"right\"}},{\"distanceAlongGeometry\":50.3,\"primary\":{\"text\":\"5th Avenue\",\"components\":[{\"text\":\"5th Avenue\",\"type\":\"text\",\"abbr\":\"5th Ave\",\"abbr_priority\":0}],\"type\":\"turn\",\"modifier\":\"right\"},\"sub\":{\"text\":\"Lootens Place\",\"components\":[{\"text\":\"Lootens Place\",\"type\":\"text\",\"abbr\":\"Lootens Pl\",\"abbr_priority\":0}],\"type\":\"turn\",\"modifier\":\"left\"}}],\"driving_side\":\"right\",\"weight\":70.5,\"intersections\":[{\"location\":[-122.524861,37.975355],\"bearings\":[15,105,195,285],\"entry\":[true,false,true,true],\"in\":1,\"out\":2},{\"location\":[-122.525103,37.974582],\"bearings\":[15,105,195,285],\"entry\":[false,true,true,true],\"in\":0,\"out\":2}]},{\"distance\":58.9,\"duration\":13.1,\"geometry\":\"ohvlgA|gjuhFUbCoBnd@\",\"name\":\"5th Avenue\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.525327,37.973656],\"bearing_before\":191.0,\"bearing_after\":281.0,\"instruction\":\"Turn right onto 5th Avenue\",\"type\":\"turn\",\"modifier\":\"right\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":58.9,\"announcement\":\"Turn left onto Lootens Place\",\"ssmlAnnouncement\":\"\\u003cspeak\\u003e\\u003camazon:effect name\\u003d\\\"drc\\\"\\u003e\\u003cprosody rate\\u003d\\\"1.08\\\"\\u003eTurn left onto Lootens Place\\u003c/prosody\\u003e\\u003c/amazon:effect\\u003e\\u003c/speak\\u003e\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":58.9,\"primary\":{\"text\":\"Lootens Place\",\"components\":[{\"text\":\"Lootens Place\",\"type\":\"text\",\"abbr\":\"Lootens Pl\",\"abbr_priority\":0}],\"type\":\"turn\",\"modifier\":\"left\"}}],\"driving_side\":\"right\",\"weight\":47.3,\"intersections\":[{\"location\":[-122.525327,37.973656],\"bearings\":[15,105,180,285],\"entry\":[false,true,true,true],\"in\":0,\"out\":3}]},{\"distance\":198.1,\"duration\":60.8,\"geometry\":\"ulvlgApqkuhFvn@lC~EVzRj@jOfA~Rr@\",\"name\":\"Lootens Place\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.525993,37.973723],\"bearing_before\":275.0,\"bearing_after\":182.0,\"instruction\":\"Turn left onto Lootens Place\",\"type\":\"turn\",\"modifier\":\"left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":178.1,\"announcement\":\"In 600 feet, turn right onto 3rd Street\",\"ssmlAnnouncement\":\"\\u003cspeak\\u003e\\u003camazon:effect name\\u003d\\\"drc\\\"\\u003e\\u003cprosody rate\\u003d\\\"1.08\\\"\\u003eIn 600 feet, turn right onto \\u003csay-as interpret-as\\u003d\\\"address\\\"\\u003e3rd\\u003c/say-as\\u003e Street\\u003c/prosody\\u003e\\u003c/amazon:effect\\u003e\\u003c/speak\\u003e\"},{\"distanceAlongGeometry\":48.9,\"announcement\":\"Turn right onto 3rd Street, then turn left onto Brooks Street\",\"ssmlAnnouncement\":\"\\u003cspeak\\u003e\\u003camazon:effect name\\u003d\\\"drc\\\"\\u003e\\u003cprosody rate\\u003d\\\"1.08\\\"\\u003eTurn right onto \\u003csay-as interpret-as\\u003d\\\"address\\\"\\u003e3rd\\u003c/say-as\\u003e Street, then turn left onto Brooks Street\\u003c/prosody\\u003e\\u003c/amazon:effect\\u003e\\u003c/speak\\u003e\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":198.1,\"primary\":{\"text\":\"3rd Street\",\"components\":[{\"text\":\"3rd Street\",\"type\":\"text\",\"abbr\":\"3rd St\",\"abbr_priority\":0}],\"type\":\"turn\",\"modifier\":\"right\"}},{\"distanceAlongGeometry\":48.9,\"primary\":{\"text\":\"3rd Street\",\"components\":[{\"text\":\"3rd Street\",\"type\":\"text\",\"abbr\":\"3rd St\",\"abbr_priority\":0}],\"type\":\"turn\",\"modifier\":\"right\"},\"sub\":{\"text\":\"Brooks Street\",\"components\":[{\"text\":\"Brooks Street\",\"type\":\"text\",\"abbr\":\"Brooks St\",\"abbr_priority\":0}],\"type\":\"turn\",\"modifier\":\"left\"}}],\"driving_side\":\"right\",\"weight\":89.2,\"intersections\":[{\"location\":[-122.525993,37.973723],\"bearings\":[15,90,180,300],\"entry\":[true,false,true,true],\"in\":1,\"out\":2},{\"location\":[-122.526064,37.972959],\"bearings\":[0,105,180,270],\"entry\":[false,true,true,true],\"in\":0,\"out\":2},{\"location\":[-122.526098,37.972529],\"bearings\":[0,105,180],\"entry\":[false,true,true],\"in\":0,\"out\":2}]},{\"distance\":121.0,\"duration\":15.2,\"geometry\":\"u}rlgA~{kuhFiAbQiBh^o@|N[fS\",\"name\":\"3rd Street\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.52616,37.971947],\"bearing_before\":182.0,\"bearing_after\":278.0,\"instruction\":\"Turn right onto 3rd Street\",\"type\":\"end of road\",\"modifier\":\"right\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":119.4,\"announcement\":\"Turn left onto Brooks Street, then turn left onto 2nd Street\",\"ssmlAnnouncement\":\"\\u003cspeak\\u003e\\u003camazon:effect name\\u003d\\\"drc\\\"\\u003e\\u003cprosody rate\\u003d\\\"1.08\\\"\\u003eTurn left onto Brooks Street, then turn left onto \\u003csay-as interpret-as\\u003d\\\"address\\\"\\u003e2nd\\u003c/say-as\\u003e Street\\u003c/prosody\\u003e\\u003c/amazon:effect\\u003e\\u003c/speak\\u003e\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":121.0,\"primary\":{\"text\":\"Brooks Street\",\"components\":[{\"text\":\"Brooks Street\",\"type\":\"text\",\"abbr\":\"Brooks St\",\"abbr_priority\":0}],\"type\":\"turn\",\"modifier\":\"left\"}},{\"distanceAlongGeometry\":119.4,\"primary\":{\"text\":\"Brooks Street\",\"components\":[{\"text\":\"Brooks Street\",\"type\":\"text\",\"abbr\":\"Brooks St\",\"abbr_priority\":0}],\"type\":\"turn\",\"modifier\":\"left\"},\"sub\":{\"text\":\"2nd Street\",\"components\":[{\"text\":\"2nd Street\",\"type\":\"text\",\"abbr\":\"2nd St\",\"abbr_priority\":0}],\"type\":\"turn\",\"modifier\":\"left\"}}],\"driving_side\":\"right\",\"weight\":45.2,\"intersections\":[{\"location\":[-122.52616,37.971947],\"bearings\":[0,105,285],\"entry\":[false,false,true],\"in\":0,\"out\":2}]},{\"distance\":109.4,\"duration\":49.9,\"geometry\":\"ueslgArqnuhFlUjBbPpAnTfB\",\"name\":\"Brooks Street\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.52753,37.972075],\"bearing_before\":272.0,\"bearing_after\":185.0,\"instruction\":\"Turn left onto Brooks Street\",\"type\":\"turn\",\"modifier\":\"left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":32.9,\"announcement\":\"Turn left onto 2nd Street, then you will arrive at your destination\",\"ssmlAnnouncement\":\"\\u003cspeak\\u003e\\u003camazon:effect name\\u003d\\\"drc\\\"\\u003e\\u003cprosody rate\\u003d\\\"1.08\\\"\\u003eTurn left onto \\u003csay-as interpret-as\\u003d\\\"address\\\"\\u003e2nd\\u003c/say-as\\u003e Street, then you will arrive at your destination\\u003c/prosody\\u003e\\u003c/amazon:effect\\u003e\\u003c/speak\\u003e\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":109.4,\"primary\":{\"text\":\"2nd Street\",\"components\":[{\"text\":\"2nd Street\",\"type\":\"text\",\"abbr\":\"2nd St\",\"abbr_priority\":0}],\"type\":\"turn\",\"modifier\":\"left\"}}],\"driving_side\":\"right\",\"weight\":81.8,\"intersections\":[{\"location\":[-122.52753,37.972075],\"bearings\":[90,180,270],\"entry\":[false,true,true],\"in\":0,\"out\":1}]},{\"distance\":105.0,\"duration\":7.5,\"geometry\":\"shqlgAxznuhF|FeiA\",\"name\":\"2nd Street\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.527677,37.971098],\"bearing_before\":185.0,\"bearing_after\":97.0,\"instruction\":\"Turn left onto 2nd Street\",\"type\":\"turn\",\"modifier\":\"left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":70.0,\"announcement\":\"You have arrived at your destination, on the right\",\"ssmlAnnouncement\":\"\\u003cspeak\\u003e\\u003camazon:effect name\\u003d\\\"drc\\\"\\u003e\\u003cprosody rate\\u003d\\\"1.08\\\"\\u003eYou have arrived at your destination, on the right\\u003c/prosody\\u003e\\u003c/amazon:effect\\u003e\\u003c/speak\\u003e\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":105.0,\"primary\":{\"text\":\"You will arrive\",\"components\":[{\"text\":\"You will arrive\",\"type\":\"text\"}],\"type\":\"arrive\",\"modifier\":\"right\"}},{\"distanceAlongGeometry\":70.0,\"primary\":{\"text\":\"You have arrived\",\"components\":[{\"text\":\"You have arrived\",\"type\":\"text\"}],\"type\":\"arrive\",\"modifier\":\"right\"}}],\"driving_side\":\"right\",\"weight\":7.5,\"intersections\":[{\"location\":[-122.527677,37.971098],\"bearings\":[0,105,270],\"entry\":[false,true,false],\"in\":0,\"out\":1}]},{\"distance\":0.0,\"duration\":0.0,\"geometry\":\"u`qlgArpluhF\",\"name\":\"2nd Street\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.52649,37.970971],\"bearing_before\":98.0,\"bearing_after\":0.0,\"instruction\":\"You have arrived at your destination, on the right\",\"type\":\"arrive\",\"modifier\":\"right\"},\"voiceInstructions\":[],\"bannerInstructions\":[],\"driving_side\":\"right\",\"weight\":0.0,\"intersections\":[{\"location\":[-122.52649,37.970971],\"bearings\":[278],\"entry\":[true],\"in\":0}]}],\"annotation\":{\"distance\":[49.34180914849393,33.05802569090612,10.689795908138624,39.59839199650681,34.80992351675273,14.209672171473654,55.332462814681335,8.615785848371143,40.91659557025385,5.914738766868038,52.97482177741399,85.20461216655733,12.501699782507817,35.42252405667138,29.311743333674485,35.66534898263901,25.758372926868166,44.32194001964289,22.517423451854935,28.451254156899267,40.20997808786729,30.687302775025532,38.532552346674045,105.03289645548367],\"congestion\":[\"low\",\"unknown\",\"unknown\",\"low\",\"unknown\",\"unknown\",\"heavy\",\"low\",\"low\",\"unknown\",\"low\",\"heavy\",\"low\",\"low\",\"low\",\"low\",\"low\",\"low\",\"low\",\"low\",\"unknown\",\"unknown\",\"unknown\",\"low\"]}}],\"routeOptions\":{\"baseUrl\":\"https://api.mapbox.com\",\"user\":\"mapbox\",\"profile\":\"driving-traffic\",\"coordinates\":[[-122.5237734,37.9753973],[-122.5264995,37.9709171]],\"alternatives\":true,\"language\":\"en\",\"continue_straight\":false,\"roundabout_exits\":false,\"geometries\":\"polyline6\",\"overview\":\"full\",\"steps\":true,\"annotations\":\"congestion,distance\",\"voice_instructions\":true,\"banner_instructions\":true,\"voice_units\":\"imperial\",\"access_token\":\"$tokenHere\",\"uuid\":\"ckd9ao6hl13a97ars2byaymo7\"},\"voiceLocale\":\"en-US\"}"
        return DirectionsRoute.fromJson(route)
    }

    @Test
    fun onInitializedCallback() {
        val callback = mockk<MapRouteLineInitializedCallback>(relaxUnitFun = true)

        every { style.layers } returns listOf(primaryRouteLayer)
        MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            callback
        )

        verify { callback.onInitialized(RouteLineLayerIds(
            PRIMARY_ROUTE_TRAFFIC_LAYER_ID,
            PRIMARY_ROUTE_LAYER_ID,
            ALTERNATIVE_ROUTE_LAYER_ID
        )) }
    }

    private fun getMultilegRoute(): DirectionsRoute {
        val routeAsJson = loadJsonFixture("multileg_route.json")
        return DirectionsRoute.fromJson(routeAsJson)
    }

    private fun loadJsonFixture(filename: String): String? {
        val classLoader = javaClass.classLoader
        val inputStream = classLoader?.getResourceAsStream(filename)
        val scanner = Scanner(inputStream, "UTF-8").useDelimiter("\\A")
        return if (scanner.hasNext()) scanner.next() else ""
    }
}
