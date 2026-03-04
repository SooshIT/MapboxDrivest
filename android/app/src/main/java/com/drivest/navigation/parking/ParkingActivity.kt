package com.drivest.navigation.parking

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.drivest.navigation.AppFlow
import com.drivest.navigation.DestinationSuggestionAdapter
import com.drivest.navigation.LocationPrePromptDialog
import com.drivest.navigation.R
import com.drivest.navigation.data.DestinationSuggestion
import com.drivest.navigation.data.MapboxSearchRepository
import com.drivest.navigation.databinding.ActivityParkingBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.gestures.addOnMapLongClickListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.navigation.ui.maps.NavigationStyles
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.roundToInt

class ParkingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityParkingBinding
    private lateinit var spotAdapter: ParkingSpotAdapter
    private lateinit var suggestionAdapter: DestinationSuggestionAdapter
    private lateinit var searchRepository: MapboxSearchRepository
    private val parkingService by lazy { ParkingService(applicationContext) }

    private var destinationPoint: Point? = null
    private var destinationName: String? = null
    private var allSpots: List<ParkingSpot> = emptyList()
    private var filteredSpots: List<ParkingSpot> = emptyList()
    private var browseSpots: List<ParkingSpot> = emptyList()
    private var selectedSpot: ParkingSpot? = null
    private var maxDistanceMeters: Int = DEFAULT_MAX_DISTANCE
    private var freeOnly: Boolean = false
    private var accessibleOnly: Boolean = false
    private var lastRequestKey: String? = null
    private var lastBrowseRequestKey: String? = null
    private var lastBrowseCenter: Point? = null
    private var lastBrowseZoom: Double? = null
    private var lastRenderCenter: Point? = null
    private var lastRenderSpotIds: Set<String> = emptySet()
    private var showListView: Boolean = true
    private var searchJob: Job? = null
    private var browseJob: Job? = null
    private var suppressSearchInput: Boolean = false

    private var parkingAnnotationManager: PointAnnotationManager? = null
    private var destinationAnnotationManager: PointAnnotationManager? = null
    private var destinationMarkerBitmap: Bitmap? = null
    private var freeMarkerBitmap: Bitmap? = null
    private var paidMarkerBitmap: Bitmap? = null
    private var unknownMarkerBitmap: Bitmap? = null
    private var mapReady: Boolean = false
    private var bottomSheetBehavior: BottomSheetBehavior<MaterialCardView>? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            openNavigationWithParking()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityParkingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSearch()
        setupList()
        setupFilters()
        setupBottomSheet()
        setupMap()
        setupViewToggle()
        updateDestinationLabel()
        renderEmptyState()
    }

    override fun onStart() {
        super.onStart()
        binding.parkingMapView.onStart()
    }

    override fun onStop() {
        binding.parkingMapView.onStop()
        super.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.parkingMapView.onLowMemory()
    }

    override fun onDestroy() {
        parkingAnnotationManager?.deleteAll()
        destinationAnnotationManager?.deleteAll()
        binding.parkingMapView.onDestroy()
        super.onDestroy()
    }

    private fun setupList() {
        spotAdapter = ParkingSpotAdapter { spot ->
            selectedSpot = spot
            spotAdapter.selectedSpotId = spot.id
            binding.parkingUseButton.isEnabled = true
        }
        binding.parkingList.apply {
            layoutManager = LinearLayoutManager(this@ParkingActivity)
            adapter = spotAdapter
        }
        binding.parkingUseButton.isEnabled = false
        binding.parkingUseButton.setOnClickListener {
            if (LocationPrePromptDialog.hasLocationPermission(this)) {
                openNavigationWithParking()
                return@setOnClickListener
            }
            LocationPrePromptDialog.show(
                activity = this,
                onAllow = { requestLocationPermission() }
            )
        }
    }

    private fun setupFilters() {
        binding.parkingFreeToggle.isCheckable = true
        binding.parkingAccessibleToggle.isCheckable = true
        binding.parkingFreeToggle.setOnClickListener {
            freeOnly = binding.parkingFreeToggle.isChecked
            applyFilters()
        }
        binding.parkingAccessibleToggle.setOnClickListener {
            accessibleOnly = binding.parkingAccessibleToggle.isChecked
            applyFilters()
        }

        val distances = listOf(200, 400, 800, 1200)
        val labels = distances.map { getString(R.string.parking_distance_option, it) }
        val defaultIndex = distances.indexOf(DEFAULT_MAX_DISTANCE).coerceAtLeast(0)
        updateDistanceButton(labels, defaultIndex)
        binding.parkingDistanceButton.setOnClickListener {
            val currentIndex = distances.indexOf(maxDistanceMeters).coerceAtLeast(0)
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.parking_filter_walk)
                .setSingleChoiceItems(labels.toTypedArray(), currentIndex) { dialog, which ->
                    maxDistanceMeters = distances.getOrElse(which) { DEFAULT_MAX_DISTANCE }
                    updateDistanceButton(labels, distances.indexOf(maxDistanceMeters).coerceAtLeast(0))
                    applyFilters()
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun setupBottomSheet() {
        val behavior = BottomSheetBehavior.from(binding.parkingBottomSheet)
        behavior.isFitToContents = false
        behavior.skipCollapsed = false
        behavior.halfExpandedRatio = 0.58f
        behavior.state = BottomSheetBehavior.STATE_COLLAPSED
        bottomSheetBehavior = behavior
    }

    private fun setupMap() {
        binding.parkingMapView.getMapboxMap().loadStyleUri(NavigationStyles.NAVIGATION_DAY_STYLE) {
            mapReady = true
            ensureAnnotationManagers()
            if (destinationPoint == null) {
                centerMap(DEFAULT_UK_CENTER, DEFAULT_IDLE_ZOOM)
            }
            renderMarkers()
        }
        binding.parkingMapView.gestures.addOnMapLongClickListener { point ->
            setDestination(point, getString(R.string.parking_dropped_pin))
            true
        }
        binding.parkingMapView.getMapboxMap().addOnCameraChangeListener {
            if (!showListView) {
                scheduleBrowseFetch()
            }
        }
        binding.parkingRecenterButton.setOnClickListener {
            val destination = destinationPoint
            if (destination != null) {
                centerMap(destination)
            } else {
                centerMap(DEFAULT_UK_CENTER, DEFAULT_IDLE_ZOOM)
            }
        }
    }

    private fun setupSearch() {
        searchRepository = MapboxSearchRepository(this)
        suggestionAdapter = DestinationSuggestionAdapter(::onSuggestionSelected)
        binding.parkingSearchResultsList.apply {
            layoutManager = LinearLayoutManager(this@ParkingActivity)
            adapter = suggestionAdapter
        }
        binding.parkingSearchInput.doAfterTextChanged { editable ->
            if (suppressSearchInput) return@doAfterTextChanged
            runSearch(editable?.toString().orEmpty())
        }
        binding.parkingSearchButton.setOnClickListener {
            binding.parkingSearchInput.requestFocus()
            runSearch(binding.parkingSearchInput.text?.toString().orEmpty())
        }
    }

    private fun setupViewToggle() {
        binding.parkingViewToggle.check(R.id.parkingViewListButton)
        binding.parkingViewToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            updateViewMode(checkedId == R.id.parkingViewListButton)
        }
        updateViewMode(true)
    }

    private fun setDestination(point: Point, name: String?) {
        destinationPoint = point
        destinationName = name
        browseSpots = emptyList()
        selectedSpot = null
        spotAdapter.selectedSpotId = null
        binding.parkingUseButton.isEnabled = false
        updateDestinationLabel()
        centerMap(point)
        fetchParkingSpots()
    }

    private fun fetchParkingSpots() {
        val destination = destinationPoint ?: return
        val requestKey = buildRequestKey(destination, DEFAULT_RADIUS_METERS, DEFAULT_MAX_RESULTS)
        if (requestKey == lastRequestKey && allSpots.isNotEmpty()) {
            applyFilters()
            return
        }
        lastRequestKey = requestKey
        binding.parkingLoading.isVisible = true
        lifecycleScope.launch {
            val spots = parkingService.fetchParkingSpots(
                destinationLat = destination.latitude(),
                destinationLng = destination.longitude(),
                radiusMeters = DEFAULT_RADIUS_METERS,
                maxResults = DEFAULT_MAX_RESULTS
            )
            allSpots = spots
            binding.parkingLoading.isVisible = false
            applyFilters()
        }
    }

    private fun applyFilters() {
        val filtered = allSpots.asSequence()
            .filter { if (freeOnly) it.feeFlag == ParkingFeeFlag.LIKELY_FREE else true }
            .filter { it.distanceMetersToDestination <= maxDistanceMeters }
            .filter { if (accessibleOnly) it.isAccessible else true }
            .sortedWith(ParkingSort.defaultComparator())
            .toList()

        if (selectedSpot != null && filtered.none { it.id == selectedSpot?.id }) {
            selectedSpot = null
            spotAdapter.selectedSpotId = null
            binding.parkingUseButton.isEnabled = false
        }

        spotAdapter.submitList(filtered)
        filteredSpots = filtered
        val markers = if (showListView) {
            filtered
        } else {
            val center = binding.parkingMapView.getMapboxMap().cameraState.center
            val base = if (browseSpots.isNotEmpty()) browseSpots else filtered
            filterSpotsWithinRadius(base, center, BROWSE_RADIUS_METERS)
        }
        renderMarkersIfChanged(markers, if (showListView) null else binding.parkingMapView.getMapboxMap().cameraState.center, force = showListView)
        renderEmptyState(filtered.isEmpty())
    }

    private fun renderEmptyState(isEmpty: Boolean = allSpots.isEmpty()) {
        val showEmpty = showListView && (destinationPoint == null || isEmpty)
        binding.parkingEmptyView.isVisible = showEmpty
        if (destinationPoint == null) {
            binding.parkingEmptyView.text = getString(R.string.parking_empty_select_destination)
        } else {
            binding.parkingEmptyView.text = getString(R.string.parking_empty_state)
        }
    }

    private fun updateViewMode(showList: Boolean) {
        showListView = showList
        binding.parkingListContainer.isVisible = showList
        binding.parkingUseButton.isVisible = showList
        binding.parkingMapHint.isVisible = !showList
        bottomSheetBehavior?.state = if (showList) {
            BottomSheetBehavior.STATE_HALF_EXPANDED
        } else {
            BottomSheetBehavior.STATE_COLLAPSED
        }
        if (!showList) {
            binding.parkingLoading.isVisible = false
            val center = binding.parkingMapView.getMapboxMap().cameraState.center
            val base = if (browseSpots.isNotEmpty()) browseSpots else filteredSpots
            renderMarkersIfChanged(
                filterSpotsWithinRadius(base, center, BROWSE_RADIUS_METERS),
                center,
                force = true
            )
            scheduleBrowseFetch()
        }
        renderEmptyState()
    }

    private fun updateDistanceButton(labels: List<String>, index: Int) {
        val label = labels.getOrNull(index) ?: getString(R.string.parking_filter_walk)
        binding.parkingDistanceButton.text = label
    }

    private fun runSearch(query: String) {
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            suggestionAdapter.submitList(emptyList())
            binding.parkingSearchEmptyView.text = getString(R.string.destination_search_min_chars)
            binding.parkingSearchEmptyView.isVisible = trimmed.isNotEmpty()
            binding.parkingSearchResultsCard.isVisible = trimmed.isNotEmpty()
            binding.parkingSearchProgress.isVisible = false
            return
        }
        binding.parkingSearchResultsCard.isVisible = true
        binding.parkingSearchProgress.isVisible = true
        binding.parkingSearchEmptyView.isVisible = false
        searchJob = lifecycleScope.launch {
            delay(250L)
            val suggestions = searchRepository.searchDestinations(trimmed)
            binding.parkingSearchProgress.isVisible = false
            suggestionAdapter.submitList(suggestions)
            binding.parkingSearchEmptyView.isVisible = suggestions.isEmpty()
            binding.parkingSearchEmptyView.text = getString(R.string.destination_search_empty)
        }
    }

    private fun onSuggestionSelected(suggestion: DestinationSuggestion) {
        setDestination(Point.fromLngLat(suggestion.lon, suggestion.lat), suggestion.placeName)
        suggestionAdapter.submitList(emptyList())
        binding.parkingSearchResultsCard.isVisible = false
        binding.parkingSearchEmptyView.isVisible = false
        binding.parkingSearchProgress.isVisible = false
    }

    private fun scheduleBrowseFetch() {
        browseJob?.cancel()
        if (!showListView && browseSpots.isNotEmpty()) {
            val center = binding.parkingMapView.getMapboxMap().cameraState.center
            renderMarkersIfChanged(
                filterSpotsWithinRadius(browseSpots, center, BROWSE_RADIUS_METERS),
                center,
                force = false
            )
        }
        browseJob = lifecycleScope.launch {
            delay(200L)
            if (showListView) return@launch
            val mapboxMap = binding.parkingMapView.getMapboxMap()
            val center = mapboxMap.cameraState.center
            val zoom = mapboxMap.cameraState.zoom
            val radius = BROWSE_RADIUS_METERS
            val requestKey = buildBrowseKey(center, radius, BROWSE_MAX_RESULTS)
            val distanceDelta = lastBrowseCenter?.let { distanceMeters(it, center) } ?: Double.MAX_VALUE
            val zoomDelta = lastBrowseZoom?.let { abs(it - zoom) } ?: Double.MAX_VALUE
            val shouldFetch = requestKey != lastBrowseRequestKey ||
                browseSpots.isEmpty() ||
                distanceDelta >= BROWSE_FETCH_MIN_DISTANCE_METERS ||
                zoomDelta >= BROWSE_FETCH_MIN_ZOOM_DELTA
            if (!shouldFetch) {
                renderMarkersIfChanged(filterSpotsWithinRadius(browseSpots, center, radius), center, force = false)
                return@launch
            }
            lastBrowseRequestKey = requestKey
            lastBrowseCenter = center
            lastBrowseZoom = zoom
            val spots = parkingService.fetchParkingSpots(
                destinationLat = center.latitude(),
                destinationLng = center.longitude(),
                radiusMeters = radius,
                maxResults = BROWSE_MAX_RESULTS
            )
            browseSpots = spots
            renderMarkersIfChanged(filterSpotsWithinRadius(spots, center, radius), center, force = true)
        }
    }

    private fun buildBrowseKey(point: Point, radius: Int, maxResults: Int): String {
        val latBucket = "%.3f".format(Locale.US, point.latitude())
        val lonBucket = "%.3f".format(Locale.US, point.longitude())
        return "parking:browse:$latBucket,$lonBucket:r=$radius:max=$maxResults"
    }

    private fun filterSpotsWithinRadius(
        spots: List<ParkingSpot>,
        center: Point,
        radiusMeters: Int
    ): List<ParkingSpot> {
        if (spots.isEmpty()) return spots
        val limit = radiusMeters + BROWSE_RADIUS_BUFFER_METERS
        return spots.filter { spot ->
            val distance = distanceMeters(center, Point.fromLngLat(spot.lng, spot.lat))
            distance <= limit
        }
    }

    private fun distanceMeters(a: Point, b: Point): Double {
        val lat1 = Math.toRadians(a.latitude())
        val lat2 = Math.toRadians(b.latitude())
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude() - a.longitude())
        val sinLat = sin(dLat / 2)
        val sinLon = sin(dLon / 2)
        val h = sinLat * sinLat + cos(lat1) * cos(lat2) * sinLon * sinLon
        val c = 2 * asin(min(1.0, sqrt(h)))
        return EARTH_RADIUS_METERS * c
    }

    private fun renderMarkers(spots: List<ParkingSpot> = allSpots) {
        if (!mapReady) return
        ensureAnnotationManagers()
        val parkingManager = parkingAnnotationManager ?: return
        val destinationManager = destinationAnnotationManager ?: return
        parkingManager.deleteAll()
        destinationManager.deleteAll()

        val destinationBitmap = destinationMarkerBitmap ?: buildDestinationMarkerBitmap().also {
            destinationMarkerBitmap = it
        }
        val freeBitmap = freeMarkerBitmap ?: buildParkingMarkerBitmap(ParkingFeeFlag.LIKELY_FREE).also {
            freeMarkerBitmap = it
        }
        val paidBitmap = paidMarkerBitmap ?: buildParkingMarkerBitmap(ParkingFeeFlag.LIKELY_PAID).also {
            paidMarkerBitmap = it
        }
        val unknownBitmap = unknownMarkerBitmap ?: buildParkingMarkerBitmap(ParkingFeeFlag.UNKNOWN).also {
            unknownMarkerBitmap = it
        }

        destinationPoint?.let { point ->
            destinationManager.create(
                PointAnnotationOptions()
                    .withPoint(point)
                    .withIconImage(destinationBitmap)
            )
        }

        spots.forEach { spot ->
            val icon = when (spot.feeFlag) {
                ParkingFeeFlag.LIKELY_FREE -> freeBitmap
                ParkingFeeFlag.LIKELY_PAID -> paidBitmap
                ParkingFeeFlag.UNKNOWN -> unknownBitmap
            }
            parkingManager.create(
                PointAnnotationOptions()
                    .withPoint(Point.fromLngLat(spot.lng, spot.lat))
                    .withIconImage(icon)
            )
        }
    }

    private fun renderMarkersIfChanged(
        spots: List<ParkingSpot>,
        center: Point? = null,
        force: Boolean = false
    ) {
        val ids = spots.map { it.id }.toSet()
        val movedEnough = center?.let { current ->
            val last = lastRenderCenter
            last == null || distanceMeters(last, current) >= RENDER_MIN_DISTANCE_METERS
        } ?: true
        if (!force && ids == lastRenderSpotIds && !movedEnough) return
        lastRenderSpotIds = ids
        if (center != null) lastRenderCenter = center
        renderMarkers(spots)
    }

    private fun ensureAnnotationManagers() {
        if (parkingAnnotationManager == null) {
            parkingAnnotationManager = binding.parkingMapView.annotations.createPointAnnotationManager()
        }
        if (destinationAnnotationManager == null) {
            destinationAnnotationManager = binding.parkingMapView.annotations.createPointAnnotationManager()
        }
    }

    private fun centerMap(point: Point, zoom: Double = DEFAULT_MAP_ZOOM) {
        binding.parkingMapView.getMapboxMap().setCamera(
            CameraOptions.Builder()
                .center(point)
                .zoom(zoom)
                .build()
        )
    }

    private fun updateDestinationLabel() {
        suppressSearchInput = true
        if (destinationName.isNullOrBlank()) {
            binding.parkingSearchInput.setText("")
        } else {
            binding.parkingSearchInput.setText(destinationName)
            binding.parkingSearchInput.setSelection(binding.parkingSearchInput.text?.length ?: 0)
        }
        suppressSearchInput = false
    }

    private fun buildRequestKey(point: Point, radius: Int, maxResults: Int): String {
        val latBucket = "%.3f".format(Locale.US, point.latitude())
        val lonBucket = "%.3f".format(Locale.US, point.longitude())
        return "parking:$latBucket,$lonBucket:r=$radius:max=$maxResults"
    }

    private fun buildDestinationMarkerBitmap(): Bitmap {
        val sizePx = (resources.displayMetrics.density * 22).roundToInt()
        val radius = sizePx / 2f
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.parseColor("#111827")
        canvas.drawCircle(radius, radius, radius, paint)

        paint.color = Color.WHITE
        canvas.drawCircle(radius, radius, radius * 0.55f, paint)

        paint.color = Color.parseColor("#111827")
        canvas.drawCircle(radius, radius, radius * 0.28f, paint)
        return bitmap
    }

    private fun buildParkingMarkerBitmap(feeFlag: ParkingFeeFlag): Bitmap {
        val sizePx = (resources.displayMetrics.density * 22).roundToInt()
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val stroke = (resources.displayMetrics.density * 2f)
        val radius = sizePx / 2f

        val fillColor = when (feeFlag) {
            ParkingFeeFlag.LIKELY_FREE -> Color.parseColor("#16A34A")
            ParkingFeeFlag.LIKELY_PAID -> Color.parseColor("#2563EB")
            ParkingFeeFlag.UNKNOWN -> Color.parseColor("#9CA3AF")
        }

        paint.style = Paint.Style.FILL
        paint.color = fillColor
        canvas.drawCircle(radius, radius, radius, paint)

        paint.style = Paint.Style.STROKE
        paint.color = Color.WHITE
        paint.strokeWidth = stroke
        canvas.drawCircle(radius, radius, radius - stroke / 2f, paint)

        when (feeFlag) {
            ParkingFeeFlag.LIKELY_FREE -> {
                paint.style = Paint.Style.STROKE
                paint.color = Color.WHITE
                paint.strokeWidth = stroke * 1.2f
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
                val startX = sizePx * 0.3f
                val startY = sizePx * 0.55f
                val midX = sizePx * 0.45f
                val midY = sizePx * 0.7f
                val endX = sizePx * 0.74f
                val endY = sizePx * 0.38f
                canvas.drawLine(startX, startY, midX, midY, paint)
                canvas.drawLine(midX, midY, endX, endY, paint)
            }
            ParkingFeeFlag.LIKELY_PAID -> {
                paint.style = Paint.Style.FILL
                paint.color = Color.WHITE
                paint.textSize = sizePx * 0.62f
                paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
                val text = "P"
                val textWidth = paint.measureText(text)
                val textMetrics = paint.fontMetrics
                val textHeight = textMetrics.descent - textMetrics.ascent
                val textX = radius - textWidth / 2f
                val textY = radius + textHeight / 2f - textMetrics.descent
                canvas.drawText(text, textX, textY, paint)
            }
            ParkingFeeFlag.UNKNOWN -> {
                paint.style = Paint.Style.FILL
                paint.color = Color.WHITE
                paint.textSize = sizePx * 0.62f
                paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
                val text = "?"
                val textWidth = paint.measureText(text)
                val textMetrics = paint.fontMetrics
                val textHeight = textMetrics.descent - textMetrics.ascent
                val textX = radius - textWidth / 2f
                val textY = radius + textHeight / 2f - textMetrics.descent
                canvas.drawText(text, textX, textY, paint)
            }
        }
        return bitmap
    }

    private fun requestLocationPermission() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun openNavigationWithParking() {
        val spot = selectedSpot ?: return
        ParkingSelectionStore.setSelection(
            spot = spot,
            finalDestination = destinationPoint,
            finalDestinationName = destinationName
        )
        startActivity(
            Intent(this, com.drivest.navigation.MainActivity::class.java)
                .putExtra(AppFlow.EXTRA_APP_MODE, AppFlow.MODE_NAV)
                .putExtra(AppFlow.EXTRA_DESTINATION_LAT, destinationPoint?.latitude() ?: spot.lat)
                .putExtra(AppFlow.EXTRA_DESTINATION_LON, destinationPoint?.longitude() ?: spot.lng)
                .putExtra(AppFlow.EXTRA_DESTINATION_NAME, destinationName ?: getString(R.string.parking_destination_fallback))
        )
    }

    private companion object {
        const val DEFAULT_RADIUS_METERS = 600
        const val DEFAULT_MAX_RESULTS = 30
        const val DEFAULT_MAX_DISTANCE = 800
        const val DEFAULT_MAP_ZOOM = 15.2
        const val DEFAULT_IDLE_ZOOM = 12.0
        const val BROWSE_RADIUS_METERS = 805 // ~0.5 miles
        const val BROWSE_RADIUS_BUFFER_METERS = 60
        const val BROWSE_FETCH_MIN_DISTANCE_METERS = 120
        const val BROWSE_FETCH_MIN_ZOOM_DELTA = 0.35
        const val BROWSE_MAX_RESULTS = 24
        const val RENDER_MIN_DISTANCE_METERS = 40
        const val EARTH_RADIUS_METERS = 6371000.0
        val DEFAULT_UK_CENTER = Point.fromLngLat(0.928174, 51.872116)
    }
}
