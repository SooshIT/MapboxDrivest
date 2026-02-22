package com.drivest.navigation

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.drivest.navigation.backend.BackendCentreRepository
import com.drivest.navigation.data.CentreRepository
import com.drivest.navigation.data.TestCentre
import com.drivest.navigation.databinding.ActivityPracticeRoutesBinding
import com.drivest.navigation.hazards.PackAwareHazardRepository
import com.drivest.navigation.intelligence.RouteIntelligenceEngine
import com.drivest.navigation.osm.OsmFeatureType
import com.drivest.navigation.pack.PackStore
import com.drivest.navigation.pack.PackType
import com.drivest.navigation.practice.AssetsPracticeRouteStore
import com.drivest.navigation.practice.DataSourcePracticeRouteStore
import com.drivest.navigation.practice.PracticeRoute
import com.mapbox.geojson.Point
import com.google.android.material.snackbar.Snackbar
import com.drivest.navigation.settings.DataSourceMode
import com.drivest.navigation.settings.SettingsRepository
import com.drivest.navigation.subscription.FeatureAccessManager
import com.drivest.navigation.subscription.SubscriptionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PracticeRoutesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPracticeRoutesBinding
    private lateinit var routeAdapter: PracticeRouteAdapter

    private val settingsRepository by lazy { SettingsRepository(applicationContext) }
    private val centreRepository by lazy {
        CentreRepository(
            context = this,
            settingsRepository = settingsRepository,
            backendCentreRepository = BackendCentreRepository(context = applicationContext)
        )
    }
    private val routeStore by lazy {
        DataSourcePracticeRouteStore(
            context = applicationContext,
            settingsRepository = settingsRepository,
            assetsPracticeRouteStore = AssetsPracticeRouteStore(this)
        )
    }
    private val hazardRepository by lazy {
        PackAwareHazardRepository(
            context = applicationContext,
            settingsRepository = settingsRepository
        )
    }
    private val routeIntelligenceEngine = RouteIntelligenceEngine()
    private val packStore by lazy { PackStore(applicationContext) }
    private val subscriptionRepository by lazy { SubscriptionRepository(applicationContext) }
    private val featureAccessManager by lazy {
        FeatureAccessManager(
            subscriptionRepository = subscriptionRepository,
            settingsRepository = settingsRepository,
            debugFreePracticeBypassEnabled = BuildConfig.DEBUG
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPracticeRoutesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val centreId = intent.getStringExtra(AppFlow.EXTRA_CENTRE_ID).orEmpty()
        if (centreId.isBlank()) {
            Toast.makeText(this, getString(R.string.practice_routes_missing_centre), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        routeAdapter = PracticeRouteAdapter { route ->
            selectedCentre?.let { centre -> openPracticeMap(centre, route) }
        }

        binding.practiceRoutesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@PracticeRoutesActivity)
            adapter = routeAdapter
        }

        lifecycleScope.launch {
            loadCentreAndRoutes(centreId)
        }
    }

    private var selectedCentre: TestCentre? = null

    private suspend fun loadCentreAndRoutes(centreId: String) {
        val currentDataSourceMode = settingsRepository.dataSourceMode.first()
        val loadedCentres = runCatching { centreRepository.loadCentres() }
            .onFailure {
                Toast.makeText(this, getString(R.string.centre_picker_load_error), Toast.LENGTH_LONG).show()
            }
            .getOrElse { emptyList() }
        val centre = loadedCentres.firstOrNull { it.id == centreId }
            ?: if (currentDataSourceMode == DataSourceMode.BACKEND_ONLY) {
                null
            } else {
                centreRepository.findByIdLocal(centreId)
            }

        if (centre == null) {
            Toast.makeText(this, getString(R.string.practice_routes_unknown_centre), Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (!BuildConfig.DEBUG) {
            val hasPracticeAccess = featureAccessManager.hasPracticeAccess(centre.id).first()
            if (!hasPracticeAccess) {
                openPaywall(getString(R.string.paywall_feature_practice_access))
                finish()
                return
            }
        }
        selectedCentre = centre
        binding.practiceRoutesTitle.text = getString(R.string.practice_routes_title, centre.name)
        binding.practiceRoutesSubtitle.text = centre.address
        binding.practiceOfflineBadge.isVisible = packStore.isOfflineAvailable(PackType.ROUTES, centre.id)

        val routes = runCatching {
            routeStore.loadRoutesForCentre(centre.id)
        }.onFailure {
            Toast.makeText(
                this,
                getString(R.string.practice_routes_load_error),
                Toast.LENGTH_LONG
            ).show()
        }.getOrDefault(emptyList())
        maybeShowOfflineDataSourceBanner()

        val routesWithIntelligence = buildRoutesWithIntelligence(centre.id, routes)
        binding.emptyRoutesView.isVisible = routesWithIntelligence.isEmpty()
        routeAdapter.submitList(routesWithIntelligence)
    }

    private fun openPracticeMap(centre: TestCentre, route: PracticeRoute) {
        lifecycleScope.launch {
            settingsRepository.setLastSelectedCentreId(centre.id)
            settingsRepository.setLastMode(AppFlow.MODE_PRACTICE)
        }
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(AppFlow.EXTRA_APP_MODE, AppFlow.MODE_PRACTICE)
                .putExtra(AppFlow.EXTRA_CENTRE_ID, centre.id)
                .putExtra(AppFlow.EXTRA_ROUTE_ID, route.id)
        )
    }

    private fun openPaywall(featureLabel: String) {
        startActivity(
            Intent(this, PaywallActivity::class.java).putExtra(
                PaywallActivity.EXTRA_FEATURE_LABEL,
                featureLabel
            )
        )
    }

    private suspend fun maybeShowOfflineDataSourceBanner() {
        if (settingsRepository.consumeOfflineDataSourceBannerSlot()) {
            Snackbar.make(binding.root, getString(R.string.using_offline_data_source), Snackbar.LENGTH_LONG)
                .show()
        }
    }

    private suspend fun buildRoutesWithIntelligence(
        centreId: String,
        routes: List<PracticeRoute>
    ): List<PracticeRoute> {
        if (routes.isEmpty()) return routes
        val seedRoutePoints = routes.firstOrNull()?.geometry?.map { point ->
            Point.fromLngLat(point.lon, point.lat)
        }.orEmpty()
        val hazards = runCatching {
            hazardRepository.getFeaturesForRoute(
                routePoints = seedRoutePoints,
                radiusMeters = 120,
                types = setOf(
                    OsmFeatureType.ROUNDABOUT,
                    OsmFeatureType.MINI_ROUNDABOUT,
                    OsmFeatureType.TRAFFIC_SIGNAL,
                    OsmFeatureType.ZEBRA_CROSSING,
                    OsmFeatureType.GIVE_WAY,
                    OsmFeatureType.SPEED_CAMERA,
                    OsmFeatureType.SCHOOL_ZONE,
                    OsmFeatureType.BUS_LANE,
                    OsmFeatureType.BUS_STOP,
                    OsmFeatureType.NO_ENTRY
                ),
                centreId = centreId
            )
        }.getOrDefault(emptyList())

        return routes.map { route ->
            val routeGeometry = route.geometry.map { point ->
                Point.fromLngLat(point.lon, point.lat)
            }
            val intelligence = routeIntelligenceEngine.evaluate(
                routeGeometry = routeGeometry,
                hazards = hazards
            )
            route.copy(intelligence = intelligence)
        }
    }
}
