package com.drivest.navigation

import android.Manifest
import android.annotation.SuppressLint
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.api.directions.v5.models.VoiceInstructions
import com.mapbox.common.location.Location
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.ImageHolder
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.ScreenCoordinate
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.gestures.OnMapClickListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.navigation.base.ExperimentalPreviewMapboxNavigationAPI
import com.mapbox.navigation.base.TimeFormat
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.formatter.DistanceFormatterOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouteAlternativesOptions
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.base.route.RouterOrigin
import com.mapbox.navigation.base.speed.model.SpeedUnit
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.formatter.MapboxDistanceFormatter
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver
import com.mapbox.navigation.core.lifecycle.requireMapboxNavigation
import com.mapbox.navigation.core.replay.route.ReplayProgressObserver
import com.mapbox.navigation.core.replay.route.ReplayRouteMapper
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.core.trip.session.VoiceInstructionsObserver
import com.drivest.navigation.camera.NavigationCameraMode
import com.drivest.navigation.camera.NavigationCameraModePolicy
import com.drivest.navigation.camera.PuckStabilityDecider
import com.drivest.navigation.data.CentreRepository
import com.drivest.navigation.data.TestCentre
import com.drivest.navigation.compliance.DisclaimerManager
import com.drivest.navigation.debug.PracticeOffRouteDebugStore
import com.drivest.navigation.databinding.ActivityMainBinding
import com.drivest.navigation.backend.BackendCentreRepository
import com.drivest.navigation.geo.RouteProjection
import com.drivest.navigation.hazards.PackAwareHazardRepository
import com.drivest.navigation.intelligence.RouteDifficultyLabel
import com.drivest.navigation.intelligence.RouteIntelligenceEngine
import com.drivest.navigation.intelligence.RouteIntelligenceSummary
import com.drivest.navigation.intelligence.StressAdjustmentPolicy
import com.drivest.navigation.legal.ConsentRepository
import com.drivest.navigation.legal.SessionSafetyGatekeeper
import com.drivest.navigation.osm.OsmFeature
import com.drivest.navigation.osm.OsmFeatureType
import com.drivest.navigation.pack.PackStore
import com.drivest.navigation.pack.PackType
import com.drivest.navigation.profile.DriverMode
import com.drivest.navigation.profile.DriverProfileRepository
import com.drivest.navigation.profile.ModeSuggestionApplier
import com.drivest.navigation.profile.PracticalPassPromptEligibility
import com.drivest.navigation.prompts.HazardVoiceController
import com.drivest.navigation.prompts.PromptEvent
import com.drivest.navigation.prompts.PromptEngine
import com.drivest.navigation.prompts.PromptSpeechTemplates
import com.drivest.navigation.prompts.PromptType
import com.drivest.navigation.prompts.VoiceOutput
import com.drivest.navigation.restrictions.NoEntryRestrictionGuard
import com.drivest.navigation.report.SessionSummaryExporter
import com.drivest.navigation.report.SessionSummaryPayload
import com.drivest.navigation.practice.AssetsPracticeRouteStore
import com.drivest.navigation.practice.DataSourcePracticeRouteStore
import com.drivest.navigation.practice.PolylineDistance
import com.drivest.navigation.practice.PracticeRoute
import com.drivest.navigation.practice.PracticeRouteStore
import com.drivest.navigation.practice.PracticeFlowDecisions
import com.drivest.navigation.practice.RollingMedian
import com.drivest.navigation.speed.SpeedLimitNormalizer
import com.drivest.navigation.settings.AppearanceModeManager
import com.drivest.navigation.settings.AppearanceModeSetting
import com.drivest.navigation.settings.AppLanguageSetting
import com.drivest.navigation.settings.PreferredUnitsSetting
import com.drivest.navigation.settings.SettingsRepository
import com.drivest.navigation.settings.SpeedLimitDisplaySetting
import com.drivest.navigation.settings.SpeedingThresholdSetting
import com.drivest.navigation.settings.VoiceModeSetting
import com.drivest.navigation.subscription.FeatureAccessManager
import com.drivest.navigation.subscription.SubscriptionRepository
import com.drivest.navigation.theory.TheoryFeatureFlags
import com.drivest.navigation.theory.services.MapRouteTagsToTheoryTopics
import com.drivest.navigation.theory.storage.TheoryProgressStore
import com.drivest.navigation.session.NavigationSessionManager
import com.drivest.navigation.telemetry.SessionSummaryTelemetry
import com.drivest.navigation.telemetry.TelemetryEvent
import com.drivest.navigation.telemetry.TelemetryRepository
import com.mapbox.navigation.tripdata.maneuver.api.MapboxManeuverApi
import com.mapbox.navigation.tripdata.progress.api.MapboxTripProgressApi
import com.mapbox.navigation.tripdata.progress.model.DistanceRemainingFormatter
import com.mapbox.navigation.tripdata.progress.model.EstimatedTimeToArrivalFormatter
import com.mapbox.navigation.tripdata.progress.model.PercentDistanceTraveledFormatter
import com.mapbox.navigation.tripdata.progress.model.TimeRemainingFormatter
import com.mapbox.navigation.tripdata.progress.model.TripProgressUpdateFormatter
import com.mapbox.navigation.tripdata.shield.model.RouteShieldCallback
import com.mapbox.navigation.tripdata.speedlimit.api.MapboxSpeedInfoApi
import com.mapbox.navigation.tripdata.speedlimit.model.SpeedInfoValue
import com.mapbox.navigation.ui.maps.NavigationStyles
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.maps.route.line.MapboxRouteLineApiExtensions.setNavigationRoutes
import com.mapbox.navigation.ui.maps.route.line.MapboxRouteLineApiExtensions.updateWithRouteProgress
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineApiOptions
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewOptions
import com.mapbox.navigation.voice.api.MapboxSpeechApi
import com.mapbox.navigation.voice.api.MapboxVoiceInstructionsPlayer
import com.mapbox.navigation.voice.model.SpeechAnnouncement
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.asin
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
class MainActivity : AppCompatActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    private enum class VoiceGuidanceMode {
        FULL,
        ALERTS_ONLY,
        MUTE
    }

    private enum class UiMode {
        PRACTICE,
        NAVIGATION
    }

    private enum class NavSessionState {
        BROWSE,
        PREVIEW,
        ACTIVE
    }

    private enum class PracticeRunStage {
        IDLE,
        TO_CENTRE,
        AT_CENTRE_TRANSITION,
        PRACTICE_ACTIVE,
        PRACTICE_COMPLETED
    }

    private enum class PracticeOffRouteState {
        ON_ROUTE,
        OFF_ROUTE
    }

    private val routeClickPadding = 30 * Resources.getSystem().displayMetrics.density
    private val alertsOnlyDistanceMeters = 120.0
    private val duplicateVoiceWindowMs = 5_000L
    private val practiceArrivalImmediateRadiusMeters = 40.0
    private val practiceArrivalDwellRadiusMeters = 60.0
    private val practiceArrivalDwellWindowMs = 10_000L
    private val practiceArrivalMaxSpeedMps = 3.57632 // 8 mph
    private val practiceStartMissedDeltaMeters = 30.0
    private val practiceStartRerouteCooldownMs = 8_000L
    private val practiceApproachArrivalMeters = 120.0
    private val practiceRouteFinishMeters = 45.0
    private val practiceRouteFinishPercent = 98
    private val practiceRouteActivationGraceMs = 10_000L
    private val practiceRouteMinDistanceArmMeters = 120.0
    private val practiceRouteMinValidDistanceMeters = 900.0
    private val practiceRouteDistanceValidityRatio = 0.45
    private val practiceOffRouteEnterDistanceMeters = 50.0
    private val practiceOffRouteExitDistanceMeters = 30.0
    private val practiceOffRouteEnterDurationMs = 5_000L
    private val practiceOffRouteExitDurationMs = 3_000L
    private val practiceOffRouteIgnoreInitialMs = 15_000L
    private val practiceOffRouteMaxGpsAccuracyMeters = 25f
    private val schoolZoneOverrideDistanceMeters = 260.0
    private val schoolZoneDefaultLimitMph = 20
    private val speedThresholdAlertCooldownMs = 3_000L
    private val cameraUpdateMinGapMs = 180L
    private val lowStressToggleDebounceMs = 220L
    private val noEntryWarningDistanceMeters = 60.0
    private val noEntryRouteMatchDistanceMeters = 30.0
    private val noEntryRerouteCooldownMs = 8_000L
    private val noEntryRerouteRadiusMeters = 140
    private val puckMinTransitionMs = 250L
    private val puckMaxTransitionMs = 900L
    private val promptBannerAutoHideMs = 6_000L
    private val defaultPromptGpsAccuracyM = 10f
    private val hazardVoiceMinConfidenceHint = 0.80f
    private val lowConfidenceRouteThreshold = 40
    private val roadMarkingFeatureTypes = setOf(
        OsmFeatureType.SCHOOL_ZONE,
        OsmFeatureType.ZEBRA_CROSSING,
        OsmFeatureType.GIVE_WAY,
        OsmFeatureType.SPEED_CAMERA
    )

    private val defaultTestCentreId = "colchester"
    private val defaultTestCentreLabel = "Colchester"
    private val fallbackTestCentrePoint = Point.fromLngLat(0.928174, 51.872116)

    private lateinit var binding: ActivityMainBinding
    private var latestStatusBarInsetPx: Int = 0
    private var voiceGuidanceMode: VoiceGuidanceMode = VoiceGuidanceMode.FULL
    private var uiMode: UiMode = UiMode.PRACTICE
    private var navSessionState: NavSessionState = NavSessionState.BROWSE
    private var navigationCameraMode: NavigationCameraMode = NavigationCameraMode.FOLLOW
    private var selectedCentreId: String = defaultTestCentreId
    private var selectedRouteId: String? = null
    private var selectedCentre: TestCentre? = null
    private var selectedDestinationPoint: Point? = null
    private var selectedDestinationName: String? = null
    private var selectedPracticeRoute: PracticeRoute? = null
    private var selectedPracticeNavigationRoute: NavigationRoute? = null
    private var practiceStartPoint: Point? = null
    private var practiceRunStage: PracticeRunStage = PracticeRunStage.IDLE
    private var isStartingSelectedPracticeRoute = false
    private var isReroutingToPracticeStart = false
    private var practiceRouteActivatedAtMs: Long = 0L
    private var practiceRouteExpectedDistanceM: Double = 0.0
    private var practiceRouteCompletionArmed = false
    private var practiceToCentreWithinRadiusSinceMs: Long? = null
    private var practiceToCentreClosestDistanceM: Double = Double.MAX_VALUE
    private var lastPracticeStartRerouteAtMs: Long = 0L
    private var isPracticeRouteLoading: Boolean = false
    private var latestEnhancedLocationPoint: Point? = null
    private var latestGpsAccuracyMeters: Float = defaultPromptGpsAccuracyM
    private var latestSpeedMetersPerSecond: Double = 0.0
    private var latestDistanceToManeuverMeters: Double = Double.MAX_VALUE
    private var latestNearestMiniRoundaboutMeters: Double = Double.MAX_VALUE
    private var latestManeuverTimeSeconds: Double? = null
    private var latestRouteDistanceRemainingM: Double = 0.0
    private var latestRouteCompletionPercent: Int = 0
    private var lastLocationUpdateAtMs: Long = 0L
    private var lastPuckAppliedPoint: Point? = null
    private var lastPuckAppliedBearing: Double? = null
    private var lastPuckAppliedAtMs: Long = 0L
    private var lastCameraZoom: Double = 15.0
    private var lastCameraPitch: Double = 45.0
    private var lastCameraUpdateAtMs: Long = 0L
    private var lastCameraCenterPoint: Point? = null
    private var styleLoaded = false
    private var appearanceModeSetting: AppearanceModeSetting = AppearanceModeSetting.AUTO
    private var lastAppliedNavigationStyleUri: String? = null
    private var speedometerEnabledSetting: Boolean = true
    private var speedLimitDisplaySetting: SpeedLimitDisplaySetting = SpeedLimitDisplaySetting.ALWAYS
    private var speedingThresholdSetting: SpeedingThresholdSetting = SpeedingThresholdSetting.AT_LIMIT
    private var speedAlertAtThresholdEnabledSetting: Boolean = true
    private var latestSpeedInfoValue: SpeedInfoValue? = null
    private var lastSpeedThresholdExceeded: Boolean = false
    private var lastSpeedThresholdAlertAtMs: Long = 0L
    private var destinationAnnotationManager: PointAnnotationManager? = null
    private var destinationAnnotation: PointAnnotation? = null
    private var hazardAnnotationManager: PointAnnotationManager? = null
    private var roadMarkingAnnotationManager: PointAnnotationManager? = null
    private var hazardFeatures: List<OsmFeature> = emptyList()
    private var lastHazardMarkerRefreshAtMs: Long = 0L
    private var lastHazardMarkerRefreshBearing: Double = Double.NaN
    private val hazardMarkerBitmapCache = mutableMapOf<PromptType, Bitmap>()
    private val roadMarkingBitmapCache = mutableMapOf<PromptType, Bitmap>()
    private val promptBannerBitmapCache = mutableMapOf<PromptType, Bitmap>()
    private var unavailablePromptBannerBitmap: Bitmap? = null
    private var pendingDestinationPreview = false
    private var lastSpokenAnnouncement: String? = null
    private var lastSpokenAtMs: Long = 0L
    private var hazardsEnabled = true
    private var promptAutoHideJob: Job? = null
    private var isManeuverSpeechPlaying = false
    private var isHazardSpeechPlaying = false
    private var debugAutoStartHandled = false
    private var extraPromptsUnavailableShown = false
    private var activePracticeRoutePolyline: List<Point> = emptyList()
    private val practiceOffRouteMedian = RollingMedian(windowSize = 5)
    private var practiceOffRouteState = PracticeOffRouteState.ON_ROUTE
    private var practiceOffRouteAboveThresholdSinceMs: Long = 0L
    private var practiceOffRouteBelowThresholdSinceMs: Long = 0L
    private var activeSessionStartedAtMs: Long = 0L
    private var activeSessionInitialDistanceM: Double = 0.0
    private var activeSessionRouteId: String? = null
    private var activeSessionMode: UiMode? = null
    private var activeSessionIntelligence: RouteIntelligenceSummary? = null
    private var currentSessionOffRouteEvents: Int = 0
    private var currentConfidenceScore: Int = 0
    private var currentDriverMode: DriverMode = DriverMode.LEARNER
    private var currentInstructorModeEnabled: Boolean = false
    private var currentOrganisationCode: String = ""
    private var lowStressModeEnabled: Boolean = false
    private var currentActiveRouteStressIndex: Int? = null
    private var currentActiveRouteDifficultyLabel: RouteDifficultyLabel? = null
    private var lastStressAdjustmentAnnouncementAtMs: Long = 0L
    private var promptShownCount: Int = 0
    private var promptReplacedQuicklyCount: Int = 0
    private var lastPromptShownAtMs: Long = 0L
    private var lastPromptTelemetryType: PromptType? = null
    private var pendingLocationGrantedAction: (() -> Unit)? = null
    private var coreMapboxObserversRegistered: Boolean = false
    private var foregroundNavigationServiceRunning: Boolean = false
    private var foregroundNavigationServiceMode: NavigationForegroundService.SessionMode? = null
    private var foregroundNotificationPermissionPromptedForSession: Boolean = false
    private var noEntryRerouteInProgress: Boolean = false
    private var lastNoEntryRerouteAtMs: Long = 0L
    private val lowStressToggleCoordinator = LowStressToggleCoordinator()
    private var suppressLowStressToggleListener = false
    private var lowStressPersistJob: Job? = null
    private var lastRenderedRouteLineSignature: String? = null

    private val debugCommandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            val command = intent?.getStringExtra(AppFlow.EXTRA_DEBUG_COMMAND).orEmpty()
            if (command.isBlank()) return
            handleDebugCommand(command)
        }
    }
    private var notificationStopReceiverRegistered = false
    private val notificationStopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action != NavigationForegroundService.ACTION_STOP_GUIDANCE_REQUEST) return
            runOnUiThread { stopActiveGuidanceFromNotification() }
        }
    }

    private val navigationLocationProvider = NavigationLocationProvider()
    private val settingsRepository by lazy { SettingsRepository(applicationContext) }
    private val subscriptionRepository by lazy { SubscriptionRepository(applicationContext) }
    private val consentRepository by lazy { ConsentRepository(applicationContext) }
    private val disclaimerManager by lazy { DisclaimerManager(applicationContext) }
    private val packStore by lazy { PackStore(applicationContext) }
    private val driverProfileRepository by lazy { DriverProfileRepository(applicationContext) }
    private val modeSuggestionApplier by lazy {
        ModeSuggestionApplier(
            driverProfileRepository = driverProfileRepository,
            settingsRepository = settingsRepository
        )
    }
    private val backendCentreRepository by lazy {
        BackendCentreRepository(context = applicationContext)
    }
    private val centreRepository by lazy {
        CentreRepository(
            context = this,
            settingsRepository = settingsRepository,
            backendCentreRepository = backendCentreRepository
        )
    }
    private val practiceRouteStore: PracticeRouteStore by lazy {
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
    private val promptEngine by lazy { PromptEngine() }
    private val routeIntelligenceEngine by lazy { RouteIntelligenceEngine() }
    private val telemetryRepository by lazy {
        TelemetryRepository(
            settingsRepository = settingsRepository,
            consentRepository = consentRepository
        )
    }
    private val theoryProgressStore by lazy { TheoryProgressStore(applicationContext) }
    private val sessionSummaryExporter by lazy { SessionSummaryExporter(applicationContext) }
    private val featureAccessManager by lazy {
        FeatureAccessManager(
            subscriptionRepository = subscriptionRepository,
            settingsRepository = settingsRepository,
            driverProfileRepository = driverProfileRepository,
            debugFreePracticeBypassEnabled = BuildConfig.DEBUG
        )
    }
    private val hazardVoiceController by lazy {
        HazardVoiceController(
            voiceModeProvider = { currentVoiceModeSetting() },
            upcomingManeuverTimeSProvider = { latestManeuverTimeSeconds },
            isManeuverSpeechPlayingProvider = { isManeuverSpeechPlaying },
            voiceOutput = object : VoiceOutput {
                override fun speak(text: String) {
                    playHazardPrompt(text)
                }

                override fun stop() {
                    stopHazardSpeech()
                }
            },
            speechTextProvider = { prompt ->
                PromptSpeechTemplates.textFor(
                    context = this,
                    prompt = prompt,
                    speedMps = latestSpeedMetersPerSecond,
                    distanceM = prompt.distanceM
                )
            }
        )
    }
    private var preferredUnitsSetting: PreferredUnitsSetting = PreferredUnitsSetting.UK_MPH
    private val replayRouteMapper = ReplayRouteMapper()
    private val replayEnabled: Boolean by lazy { isLikelyEmulator() }

    private val routeLineViewOptions: MapboxRouteLineViewOptions by lazy {
        MapboxRouteLineViewOptions.Builder(this)
            .routeLineBelowLayerId("road-label-navigation")
            .build()
    }

    private val routeLineApiOptions: MapboxRouteLineApiOptions by lazy {
        MapboxRouteLineApiOptions.Builder()
            .vanishingRouteLineEnabled(true)
            .build()
    }

    private val routeLineView by lazy {
        MapboxRouteLineView(routeLineViewOptions)
    }

    private val routeLineApi: MapboxRouteLineApi by lazy {
        MapboxRouteLineApi(routeLineApiOptions)
    }

    private val formatterOptions: DistanceFormatterOptions by lazy {
        DistanceFormatterOptions.Builder(applicationContext).build()
    }

    private val maneuverApi: MapboxManeuverApi by lazy {
        MapboxManeuverApi(MapboxDistanceFormatter(formatterOptions))
    }

    private val tripProgressFormatter: TripProgressUpdateFormatter by lazy {
        TripProgressUpdateFormatter.Builder(this)
            .distanceRemainingFormatter(DistanceRemainingFormatter(formatterOptions))
            .timeRemainingFormatter(TimeRemainingFormatter(this))
            .percentRouteTraveledFormatter(PercentDistanceTraveledFormatter())
            .estimatedTimeToArrivalFormatter(EstimatedTimeToArrivalFormatter(this, TimeFormat.NONE_SPECIFIED))
            .build()
    }

    private val tripProgressApi: MapboxTripProgressApi by lazy {
        MapboxTripProgressApi(tripProgressFormatter)
    }

    private val roadShieldCallback =
        RouteShieldCallback { shields -> binding.maneuverView.renderManeuverWith(shields) }

    private val speedInfoApi: MapboxSpeedInfoApi by lazy {
        MapboxSpeedInfoApi()
    }

    private val speedThresholdToneGenerator: ToneGenerator? by lazy(LazyThreadSafetyMode.NONE) {
        runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70) }.getOrNull()
    }

    private var voiceLanguageTag: String = AppLanguageSetting.ENGLISH_UK.bcp47Tag
    private var voiceLanguageSetting: AppLanguageSetting = AppLanguageSetting.ENGLISH_UK
    private var useOnDeviceTtsForVoice: Boolean = false
    private var speechApiInstance: MapboxSpeechApi? = null
    private var voiceInstructionsPlayerInstance: MapboxVoiceInstructionsPlayer? = null

    private val voiceInstructionsObserver = VoiceInstructionsObserver { voiceInstructions ->
        hazardVoiceController.onManeuverInstructionArrived()
        if (!shouldSpeakVoiceInstruction(voiceInstructions)) {
            return@VoiceInstructionsObserver
        }
        if (isDuplicateVoiceInstruction(voiceInstructions)) {
            return@VoiceInstructionsObserver
        }
        speakVoiceInstructions(
            voiceInstructions = voiceInstructions,
            onStarted = { isManeuverSpeechPlaying = true },
            onCompleted = { isManeuverSpeechPlaying = false }
        )
    }

    private lateinit var sessionManager: NavigationSessionManager

    private val locationObserver: LocationObserver = object : LocationObserver {
        override fun onNewRawLocation(rawLocation: Location) {
            // Raw location is not used for rendering.
        }

        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            val enhancedLocation = locationMatcherResult.enhancedLocation
            val nowMs = System.currentTimeMillis()
            val locationUpdateIntervalMs = if (lastLocationUpdateAtMs == 0L) {
                1_000L
            } else {
                (nowMs - lastLocationUpdateAtMs).coerceIn(250L, 2_200L)
            }
            lastLocationUpdateAtMs = nowMs

            latestSpeedMetersPerSecond = (enhancedLocation.speed ?: 0.0).coerceAtLeast(0.0)
            latestGpsAccuracyMeters = ((enhancedLocation.horizontalAccuracy ?: defaultPromptGpsAccuracyM.toDouble())
                .coerceAtLeast(0.0)).toFloat()
            val enhancedPoint = Point.fromLngLat(enhancedLocation.longitude, enhancedLocation.latitude)
            latestEnhancedLocationPoint = enhancedPoint
            val movementMeters = lastPuckAppliedPoint?.let { previous ->
                distanceMeters(previous, enhancedPoint)
            } ?: Double.MAX_VALUE
            val enhancedBearing = (enhancedLocation.bearing ?: 0.0).toDouble()
            val bearingDeltaDegrees = lastPuckAppliedBearing?.let { previous ->
                PuckStabilityDecider.bearingDeltaDegrees(previous, enhancedBearing)
            } ?: Double.MAX_VALUE
            val elapsedSincePuckApplyMs = if (lastPuckAppliedAtMs == 0L) {
                Long.MAX_VALUE
            } else {
                (nowMs - lastPuckAppliedAtMs).coerceAtLeast(0L)
            }
            val puckDecision = PuckStabilityDecider.decide(
                input = PuckStabilityDecider.Input(
                    elapsedMsSinceLastApply = elapsedSincePuckApplyMs,
                    locationUpdateIntervalMs = locationUpdateIntervalMs,
                    movementMeters = movementMeters,
                    bearingDeltaDegrees = bearingDeltaDegrees,
                    speedMetersPerSecond = latestSpeedMetersPerSecond,
                    gpsAccuracyMeters = latestGpsAccuracyMeters
                ),
                minTransitionMs = puckMinTransitionMs,
                maxTransitionMs = puckMaxTransitionMs
            )
            if (puckDecision.shouldApply) {
                navigationLocationProvider.changePosition(
                    enhancedLocation,
                    locationMatcherResult.keyPoints,
                    { configurePuckAnimator(this, puckDecision.transitionDurationMs) },
                    {
                        configurePuckAnimator(
                            this,
                            (puckDecision.transitionDurationMs * 0.82).toLong().coerceAtLeast(puckMinTransitionMs)
                        )
                    }
                )
                lastPuckAppliedPoint = enhancedPoint
                lastPuckAppliedBearing = enhancedBearing
                lastPuckAppliedAtMs = nowMs
            }
            if (::sessionManager.isInitialized) {
                sessionManager.onLocationUpdate(
                    latitude = enhancedLocation.latitude,
                    longitude = enhancedLocation.longitude,
                    gpsAccuracyM = latestGpsAccuracyMeters,
                    speedMps = latestSpeedMetersPerSecond.toFloat()
                )
            }
            val cameraBearing = if (latestSpeedMetersPerSecond < 1.5) null else enhancedLocation.bearing
            updateCamera(
                latestEnhancedLocationPoint ?: fallbackTestCentrePoint,
                cameraBearing,
                latestSpeedMetersPerSecond,
                locationUpdateIntervalMs
            )

            val speedInfo = speedInfoApi.updatePostedAndCurrentSpeed(
                locationMatcherResult,
                formatterOptions
            )
            latestSpeedInfoValue = speedInfo
            renderSpeedometer(speedInfo)
            evaluatePracticeOffRoute(nowMs)
            maybeRefreshHazardMarkers(nowMs)
        }
    }

    private val routesObserver = RoutesObserver { result ->
        lifecycleScope.launch {
            // Skip full redraw when route geometry is unchanged to avoid route-layer flicker.
            if (shouldRenderRouteLine(result.navigationRoutes)) {
                routeLineApi.setNavigationRoutes(
                    newRoutes = result.navigationRoutes,
                    alternativeRoutesMetadata = mapboxNavigation.getAlternativeMetadataFor(result.navigationRoutes)
                ).apply {
                    binding.mapView.mapboxMap.style?.let { style ->
                        routeLineView.renderRouteDrawData(style, this)
                    }
                }
            }

            handleActiveNavigationRouteStressUpdates(result.navigationRoutes)

            if (uiMode == UiMode.NAVIGATION && navSessionState == NavSessionState.PREVIEW) {
                updatePreviewSummaryFromRoutes(result.navigationRoutes)
            }

            if (::sessionManager.isInitialized) {
                sessionManager.onNavigationRoutesUpdated(result.navigationRoutes)
            }

            val isActiveSession =
                mainViewModel.uiState.value.sessionState == NavigationSessionManager.SessionState.ACTIVE
            if (replayEnabled && isActiveSession && result.navigationRoutes.isNotEmpty()) {
                syncReplayToRoute(result.navigationRoutes.first())
            }
        }
    }

    private val routeProgressObserver = RouteProgressObserver { progress ->
        lifecycleScope.launch {
            val routeLineUpdate = routeLineApi.updateWithRouteProgress(progress)
            binding.mapView.mapboxMap.style?.let { style ->
                routeLineView.renderRouteLineUpdate(style, routeLineUpdate)
            }
        }

        val maneuvers = maneuverApi.getManeuvers(progress)
        maneuvers.fold(
            {
                // Ignore maneuver formatting errors for now.
            },
            {
                maneuvers.onValue { maneuverList ->
                    maneuverApi.getRoadShields(maneuverList, roadShieldCallback)
                }
                binding.maneuverView.isVisible = true
                binding.maneuverView.renderManeuvers(maneuvers)
                updateTopOrnamentsPosition()
            }
        )

        val tripProgress = tripProgressApi.getTripProgress(progress)
        latestDistanceToManeuverMeters =
            progress.currentLegProgress?.currentStepProgress?.distanceRemaining?.toDouble()
                ?: Double.MAX_VALUE
        latestManeuverTimeSeconds =
            progress.currentLegProgress?.currentStepProgress?.durationRemaining?.toDouble()
        val formatter = tripProgress.formatter
        val completionPercent = normalizedCompletionPercent(tripProgress.percentRouteTraveled.toDouble())
        val ignoreTripProgressUi = uiMode == UiMode.PRACTICE &&
            practiceRunStage != PracticeRunStage.TO_CENTRE &&
            practiceRunStage != PracticeRunStage.PRACTICE_ACTIVE
        if (!ignoreTripProgressUi) {
            latestRouteDistanceRemainingM = tripProgress.distanceRemaining
            latestRouteCompletionPercent = completionPercent

            binding.routeProgressBanner.isVisible = true
            binding.routeDistanceLeftValue.text = formatter.getDistanceRemaining(tripProgress.distanceRemaining)
            binding.routeTimeLeftValue.text = formatter.getTimeRemaining(tripProgress.totalTimeRemaining)
            binding.routeEtaValue.text = formatter.getEstimatedTimeToArrival(
                tripProgress.estimatedTimeToArrival,
                tripProgress.arrivalTimeZone
            )
            binding.routeCompletedValue.text = "$completionPercent%"
            binding.routeProgressBar.progress = completionPercent
            renderActiveRouteStressBanner()
        }

        handlePracticeRunProgress(
            distanceRemainingMeters = tripProgress.distanceRemaining,
            completionPercent = completionPercent
        )
    }

    private val mapClickListener = OnMapClickListener { point ->
        lifecycleScope.launch {
            routeLineApi.findClosestRoute(point, binding.mapView.mapboxMap, routeClickPadding) {
                val routeFound = it.value?.navigationRoute
                if (routeFound != null && routeFound != routeLineApi.getPrimaryNavigationRoute()) {
                    val reorderedRoutes = routeLineApi.getNavigationRoutes()
                        .filter { navigationRoute -> navigationRoute != routeFound }
                        .toMutableList()
                        .also { list -> list.add(0, routeFound) }

                    mapboxNavigation.setNavigationRoutes(reorderedRoutes)
                }
            }
        }
        false
    }

    private val mapboxNavigation: MapboxNavigation by requireMapboxNavigation(
        onResumedObserver = object : MapboxNavigationObserver {
            @SuppressLint("MissingPermission")
            override fun onAttached(mapboxNavigation: MapboxNavigation) {
                registerCoreMapboxObserversIfNeeded(mapboxNavigation)
                if (replayEnabled) {
                    mapboxNavigation.startReplayTripSession()
                    Log.d(TAG, "Replay trip session enabled (emulator mode).")
                } else {
                    mapboxNavigation.mapboxReplayer.stop()
                    mapboxNavigation.startTripSession()
                    Log.d(TAG, "Real GPS trip session enabled (device mode).")
                }
            }

            override fun onDetached(mapboxNavigation: MapboxNavigation) {
                val keepActiveGuidance = shouldKeepForegroundNavigationServiceActive()
                if (!keepActiveGuidance) {
                    if (::sessionManager.isInitialized) {
                        sessionManager.stop()
                    }
                    if (uiMode == UiMode.PRACTICE) {
                        resetPracticeRunState()
                    }
                    cancelSpeechGeneration()
                    clearVoicePlaybackQueue()
                    latestSpeedInfoValue = null
                    renderSpeedometer(null)
                    mapboxNavigation.mapboxReplayer.stop()
                    unregisterCoreMapboxObserversIfNeeded(mapboxNavigation)
                } else {
                    Log.d(
                        TAG,
                        "Lifecycle detach while guidance active; preserving route session in background."
                    )
                }
            }
        },
        onInitialize = this::initNavigation
    )

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (granted) {
            val action = pendingLocationGrantedAction
            pendingLocationGrantedAction = null
            action?.invoke()
        } else {
            pendingLocationGrantedAction = null
            Toast.makeText(
                this,
                getString(R.string.location_permission_required_for_routing),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val foregroundNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        settingsRepository.refreshNotificationsPermission()
        if (!it) {
            Toast.makeText(
                this,
                getString(R.string.navigation_service_permission_hint),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        uiMode = resolveUiModeFromIntent()
        resolveSessionSelection()
        mainViewModel.setMode(
            if (uiMode == UiMode.NAVIGATION) AppFlow.MODE_NAV else AppFlow.MODE_PRACTICE
        )
        mainViewModel.setCentre(selectedCentreId)
        mainViewModel.setRoute(selectedRouteId)
        mainViewModel.setDestination(
            selectedDestinationPoint?.let { destination ->
                DestinationUiState(
                    lat = destination.latitude(),
                    lon = destination.longitude(),
                    name = selectedDestinationName
                )
            }
        )

        observeUiState()
        observeSettings()
        observeDriverProfile()
        registerNotificationStopReceiver()
        lifecycleScope.launch {
            settingsRepository.setLastSelectedCentreId(selectedCentreId)
            settingsRepository.setLastMode(
                if (uiMode == UiMode.NAVIGATION) AppFlow.MODE_NAV else AppFlow.MODE_PRACTICE
            )
            modeSuggestionApplier.applySuggestionsIfNeeded(driverProfileRepository.driverMode.first())
        }
        binding.maneuverView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateTopOrnamentsPosition()
        }
        applySystemBarInsets()
        renderVoiceGuidanceMode()
        applyUiModeState()
        renderAppearanceModeControl()

        loadNavigationMapStyle(forceReload = true, resetCamera = true)
        binding.mapView.gestures.addOnMapClickListener(mapClickListener)
        binding.mapView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_POINTER_DOWN,
                MotionEvent.ACTION_MOVE -> {
                    val nextMode = NavigationCameraModePolicy.onUserMapGesture(
                        currentMode = navigationCameraMode,
                        hasRouteForOverview = hasRouteForOverview()
                    )
                    setNavigationCameraMode(nextMode)
                }
            }
            false
        }
        binding.compassButton.setOnClickListener {
            setNavigationCameraMode(NavigationCameraMode.FOLLOW)
            resetCameraBearingToNorth()
            recenterFollowCamera(force = true)
        }
        binding.voiceModeButton.setOnClickListener {
            cycleVoiceGuidanceMode()
        }
        binding.overviewButton.setOnClickListener {
            handleOverviewButtonPressed()
        }
        binding.overviewButton.alpha = 0.8f
        binding.appearanceModeButton.setOnClickListener {
            cycleAppearanceMode()
        }
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.navLowStressSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (suppressLowStressToggleListener) {
                return@setOnCheckedChangeListener
            }
            val toggleState = lowStressToggleCoordinator.onUserToggled(isChecked)
            lowStressModeEnabled = toggleState.effectiveEnabled
            scheduleLowStressPersistence(
                toggleState.pendingPersistValue ?: isChecked
            )
            applyLowStressPreferenceToPreviewRoutesIfNeeded()
        }
        binding.stopNavigationButton.setOnClickListener {
            stopNavigationSession()
        }

        binding.startNavigation.setOnClickListener {
            lifecycleScope.launch {
                val proceedWithStart: () -> Unit = {
                    if (!hasLocationPermission()) {
                        LocationPrePromptDialog.show(
                            activity = this@MainActivity,
                            onAllow = {
                                requestLocationPermission {
                                    startPrimaryRouteAction()
                                }
                            }
                        )
                    } else {
                        startPrimaryRouteAction()
                    }
                }

                val needsSafetyAck = consentRepository.needsSafetyAcknowledgement.first()
                if (needsSafetyAck) {
                    proceedWithStart()
                } else {
                    ensureDisclaimerAccepted {
                        proceedWithStart()
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        registerDebugReceiver()
        ensureSessionManager()
        syncVoicePipelineToCurrentLanguage()
        updateKeepScreenOnState()
        if (pendingDestinationPreview && selectedDestinationPoint != null) {
            sessionManager.previewDestination(selectedDestinationPoint!!)
            pendingDestinationPreview = false
        }

        if (!debugAutoStartHandled && intent.getBooleanExtra(AppFlow.EXTRA_DEBUG_AUTOSTART, false)) {
            debugAutoStartHandled = true
            lifecycleScope.launch {
                delay(1_000)
                handleDebugCommand(
                    if (uiMode == UiMode.NAVIGATION) AppFlow.DEBUG_START_NAV else AppFlow.DEBUG_START_PRACTICE
                )
            }
        }
    }

    override fun onStop() {
        updateKeepScreenOnState(forceDisable = true)
        unregisterDebugReceiver()
        super.onStop()
    }

    private fun ensureSessionManager() {
        if (::sessionManager.isInitialized) return
        sessionManager = NavigationSessionManager(
            mapboxNavigation = mapboxNavigation,
            routeProgressObserver = routeProgressObserver,
            voiceInstructionsObserver = voiceInstructionsObserver,
            createReplayObserver = if (replayEnabled) {
                { ReplayProgressObserver(mapboxNavigation.mapboxReplayer) }
            } else {
                null
            },
            clearVoiceQueue = {
                cancelSpeechGeneration()
                clearVoicePlaybackQueue()
            },
            onStateChanged = { state ->
                mainViewModel.setSessionState(state)
                if (state != NavigationSessionManager.SessionState.ACTIVE) {
                    mainViewModel.setActivePrompt(null)
                }
                if (uiMode == UiMode.NAVIGATION) {
                    navSessionState = toNavSessionState(state)
                }
                applyUiModeState()
            },
            onPreviewPracticeRoute = { /* MainActivity drives practice preview generation. */ },
            onPreviewDestination = { destination ->
                selectedDestinationPoint = destination
                renderDestinationMarker()
                previewNavigationToDestination()
            },
            settingsRepository = settingsRepository,
            hazardRepository = hazardRepository,
            promptEngine = promptEngine,
            onPromptEvent = { prompt -> mainViewModel.setActivePrompt(prompt) },
            onFeaturesUpdated = { features ->
                hazardFeatures = features
                lastHazardMarkerRefreshAtMs = 0L
                renderHazardMarkers(features)
                if (uiMode == UiMode.NAVIGATION && navSessionState == NavSessionState.PREVIEW) {
                    val previewRoutes = mapboxNavigation.getNavigationRoutes()
                    val prioritizedRoutes = prioritizeRoutesForConfidence(previewRoutes)
                    if (prioritizedRoutes != previewRoutes) {
                        mapboxNavigation.setNavigationRoutes(prioritizedRoutes)
                    }
                    updatePreviewSummaryFromRoutes(prioritizedRoutes)
                }
            },
            onFeatureUnavailable = { showExtraPromptsUnavailableBanner() },
            onNearestMiniRoundaboutMeters = { meters ->
                latestNearestMiniRoundaboutMeters = meters
            }
        )
        sessionManager.init(this, binding.mapView)
        sessionManager.setCentreId(selectedCentreId)
        sessionManager.setMode(
            if (uiMode == UiMode.NAVIGATION) {
                NavigationSessionManager.Mode.NAVIGATION
            } else {
                NavigationSessionManager.Mode.PRACTICE
            }
        )
    }

    private fun registerCoreMapboxObserversIfNeeded(mapboxNavigation: MapboxNavigation) {
        if (coreMapboxObserversRegistered) return
        mapboxNavigation.registerLocationObserver(locationObserver)
        mapboxNavigation.registerRoutesObserver(routesObserver)
        coreMapboxObserversRegistered = true
        Log.d(TAG, "Core Mapbox observers attached")
    }

    private fun unregisterCoreMapboxObserversIfNeeded(mapboxNavigation: MapboxNavigation) {
        if (!coreMapboxObserversRegistered) return
        mapboxNavigation.unregisterLocationObserver(locationObserver)
        mapboxNavigation.unregisterRoutesObserver(routesObserver)
        coreMapboxObserversRegistered = false
        Log.d(TAG, "Core Mapbox observers detached")
    }

    private fun shouldKeepForegroundNavigationServiceActive(): Boolean {
        return when (uiMode) {
            UiMode.NAVIGATION -> navSessionState == NavSessionState.ACTIVE
            UiMode.PRACTICE -> practiceRunStage == PracticeRunStage.TO_CENTRE ||
                practiceRunStage == PracticeRunStage.AT_CENTRE_TRANSITION ||
                practiceRunStage == PracticeRunStage.PRACTICE_ACTIVE
        }
    }

    private fun activeForegroundSessionMode(): NavigationForegroundService.SessionMode {
        return if (uiMode == UiMode.NAVIGATION) {
            NavigationForegroundService.SessionMode.NAVIGATION
        } else {
            NavigationForegroundService.SessionMode.PRACTICE
        }
    }

    private fun syncNavigationForegroundServiceState() {
        val shouldRun = shouldKeepForegroundNavigationServiceActive()
        if (shouldRun) {
            val targetMode = activeForegroundSessionMode()
            when {
                !foregroundNavigationServiceRunning -> {
                    NavigationForegroundService.start(applicationContext, targetMode)
                    foregroundNavigationServiceRunning = true
                    foregroundNavigationServiceMode = targetMode
                }
                foregroundNavigationServiceMode != targetMode -> {
                    NavigationForegroundService.update(applicationContext, targetMode)
                    foregroundNavigationServiceMode = targetMode
                }
            }
            maybeRequestForegroundNotificationPermission()
        } else if (foregroundNavigationServiceRunning) {
            NavigationForegroundService.stop(applicationContext)
            foregroundNavigationServiceRunning = false
            foregroundNavigationServiceMode = null
            foregroundNotificationPermissionPromptedForSession = false
        }

        if (
            !shouldRun &&
            coreMapboxObserversRegistered &&
            !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        ) {
            unregisterCoreMapboxObserversIfNeeded(mapboxNavigation)
        }
    }

    private fun maybeRequestForegroundNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return

        val permissionGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (permissionGranted || foregroundNotificationPermissionPromptedForSession) return

        foregroundNotificationPermissionPromptedForSession = true
        foregroundNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun applySystemBarInsets() {
        val topGapPx = (8f * resources.displayMetrics.density).toInt()
        val bottomGapPx = (16f * resources.displayMetrics.density).toInt()

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            latestStatusBarInsetPx = topInset
            binding.maneuverView.updateLayoutParams<ConstraintLayout.LayoutParams> {
                topMargin = topInset + topGapPx
            }

            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            binding.startNavigation.updateLayoutParams<ConstraintLayout.LayoutParams> {
                bottomMargin = bottomInset + bottomGapPx
            }
            binding.stopNavigationButton.updateLayoutParams<ConstraintLayout.LayoutParams> {
                bottomMargin = bottomInset + (8f * resources.displayMetrics.density).toInt()
            }

            updateTopOrnamentsPosition()
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun updateTopOrnamentsPosition() {
        binding.mapView.post {
            binding.mapView.compass.apply {
                enabled = false
                visibility = false
            }
        }
    }

    private fun resolveUiModeFromIntent(): UiMode {
        return when (intent.getStringExtra(AppFlow.EXTRA_APP_MODE)) {
            AppFlow.MODE_NAV -> UiMode.NAVIGATION
            else -> UiMode.PRACTICE
        }
    }

    private fun resolveSessionSelection() {
        selectedRouteId = intent.getStringExtra(AppFlow.EXTRA_ROUTE_ID)?.takeIf { it.isNotBlank() }

        val requestedCentreId = intent.getStringExtra(AppFlow.EXTRA_CENTRE_ID)?.trim().orEmpty()
        selectedCentreId = requestedCentreId.ifBlank { defaultTestCentreId }
        selectedCentre = runCatching { centreRepository.findByIdLocal(selectedCentreId) }.getOrNull()

        if (selectedCentre == null && selectedCentreId != defaultTestCentreId) {
            selectedCentreId = defaultTestCentreId
            selectedCentre = runCatching { centreRepository.findByIdLocal(selectedCentreId) }.getOrNull()
        }

        val hasDestination =
            intent.hasExtra(AppFlow.EXTRA_DESTINATION_LAT) && intent.hasExtra(AppFlow.EXTRA_DESTINATION_LON)
        if (hasDestination) {
            val lat = intent.getDoubleExtra(AppFlow.EXTRA_DESTINATION_LAT, Double.NaN)
            val lon = intent.getDoubleExtra(AppFlow.EXTRA_DESTINATION_LON, Double.NaN)
            if (lat.isFinite() && lon.isFinite()) {
                selectedDestinationPoint = Point.fromLngLat(lon, lat)
                selectedDestinationName = intent.getStringExtra(AppFlow.EXTRA_DESTINATION_NAME)
                    ?.takeIf { it.isNotBlank() }
                navSessionState = NavSessionState.BROWSE
            }
        }
    }

    private fun applyUiModeState() {
        binding.startNavigation.text = when (uiMode) {
            UiMode.PRACTICE -> when (practiceRunStage) {
                PracticeRunStage.IDLE -> getString(
                    R.string.start_navigation_practice_centre,
                    selectedCentreLabel()
                )
                PracticeRunStage.TO_CENTRE -> getString(R.string.practice_approaching_start)
                PracticeRunStage.AT_CENTRE_TRANSITION -> getString(R.string.practice_route_transitioning)
                PracticeRunStage.PRACTICE_ACTIVE -> getString(R.string.practice_route_active)
                PracticeRunStage.PRACTICE_COMPLETED -> getString(R.string.practice_route_completed_state)
            }
            UiMode.NAVIGATION -> when (navSessionState) {
                NavSessionState.ACTIVE -> getString(R.string.start_navigation_nav_preview)
                NavSessionState.PREVIEW -> getString(R.string.start_navigation_nav_preview)
                NavSessionState.BROWSE -> {
                    if (selectedDestinationPoint == null) {
                        getString(R.string.preview_navigation_route)
                    } else {
                        getString(R.string.preview_navigation_route)
                    }
                }
            }
        }
        if (uiMode == UiMode.PRACTICE) {
            binding.startNavigation.isEnabled = !isPracticeRouteLoading &&
                (practiceRunStage == PracticeRunStage.IDLE || practiceRunStage == PracticeRunStage.PRACTICE_COMPLETED)
        }
        binding.practiceOfflineBadge.isVisible =
            uiMode == UiMode.PRACTICE && packStore.isOfflineAvailable(PackType.ROUTES, selectedCentreId)
        binding.stopNavigationButton.isVisible =
            uiMode == UiMode.NAVIGATION && navSessionState == NavSessionState.ACTIVE
        renderActiveRouteStressBanner()
        adjustFloatingControlsAvoidingBottomPanels()
        updateKeepScreenOnState()
        syncNavigationForegroundServiceState()
    }

    private fun updateKeepScreenOnState(forceDisable: Boolean = false) {
        if (!::binding.isInitialized) return
        val shouldKeepOn = !forceDisable && when (uiMode) {
            UiMode.NAVIGATION -> navSessionState == NavSessionState.ACTIVE
            UiMode.PRACTICE -> practiceRunStage == PracticeRunStage.TO_CENTRE ||
                practiceRunStage == PracticeRunStage.AT_CENTRE_TRANSITION ||
                practiceRunStage == PracticeRunStage.PRACTICE_ACTIVE
        }
        binding.root.keepScreenOn = shouldKeepOn
        if (shouldKeepOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun scheduleLowStressPersistence(targetEnabled: Boolean) {
        lowStressPersistJob?.cancel()
        lowStressPersistJob = lifecycleScope.launch {
            delay(lowStressToggleDebounceMs)
            settingsRepository.setLowStressModeEnabled(
                targetEnabled,
                markUserSet = true
            )
        }
    }

    private fun applyLowStressPreferenceToPreviewRoutesIfNeeded() {
        if (uiMode != UiMode.NAVIGATION || navSessionState != NavSessionState.PREVIEW) {
            return
        }
        val currentRoutes = mapboxNavigation.getNavigationRoutes()
        if (currentRoutes.isEmpty()) return
        val reprioritized = prioritizeRoutesForConfidence(currentRoutes)
        mapboxNavigation.setNavigationRoutes(reprioritized)
        updatePreviewSummaryFromRoutes(reprioritized)
    }

    private fun adjustFloatingControlsAvoidingBottomPanels() {
        binding.root.doOnLayout {
            val topCandidates = mutableListOf<Int>()
            if (binding.startNavigation.top > 0) {
                topCandidates += binding.startNavigation.top
            }
            if (binding.navPreviewSummaryBanner.isVisible && binding.navPreviewSummaryBanner.top > 0) {
                topCandidates += binding.navPreviewSummaryBanner.top
            }
            if (binding.routeProgressBanner.isVisible && binding.routeProgressBanner.top > 0) {
                topCandidates += binding.routeProgressBanner.top
            }
            val anchorTop = topCandidates.minOrNull() ?: return@doOnLayout
            val desiredGap = dpToPx(8f)

            val leftControls = listOf(
                binding.speedometerCard,
                binding.overviewButton,
                binding.voiceModeButton,
                binding.compassButton
            ).filter { view -> view.isVisible }
            val leftBottom = leftControls.maxOfOrNull { view -> view.bottom } ?: 0
            val leftOverlap = (leftBottom + desiredGap - anchorTop).coerceAtLeast(0)
            val leftTranslation = -leftOverlap.toFloat()
            leftControls.forEach { view ->
                view.translationY = leftTranslation
            }

            val rightControls = listOf(binding.settingsButton, binding.appearanceModeButton)
                .filter { view -> view.isVisible }
            val rightBottom = rightControls.maxOfOrNull { view -> view.bottom } ?: 0
            val rightOverlap = (rightBottom + desiredGap - anchorTop).coerceAtLeast(0)
            val rightTranslation = -rightOverlap.toFloat()
            rightControls.forEach { view ->
                view.translationY = rightTranslation
            }
        }
    }

    private fun ensureDisclaimerAccepted(onAccepted: () -> Unit) {
        if (disclaimerManager.isCurrentVersionAccepted()) {
            onAccepted()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.disclaimer_title)
            .setMessage(getString(R.string.disclaimer_message, disclaimerManager.currentVersion()))
            .setCancelable(false)
            .setNegativeButton(R.string.disclaimer_decline, null)
            .setPositiveButton(R.string.disclaimer_accept) { _, _ ->
                disclaimerManager.acceptCurrentVersion()
                onAccepted()
            }
            .show()
    }

    private suspend fun requestSafetyAcknowledgementIfNeeded(): Boolean {
        val needsSafetyAck = consentRepository.needsSafetyAcknowledgement.first()
        if (!needsSafetyAck) {
            return true
        }
        val userAccepted = suspendCancellableCoroutine<Boolean> { continuation ->
            SafetyNoticeDialog.show(this) { accepted ->
                if (continuation.isActive) {
                    continuation.resume(accepted)
                }
            }
        }
        val shouldStart = SessionSafetyGatekeeper.shouldStartSession(
            needsSafetyAck = needsSafetyAck,
            userAcceptedDialog = userAccepted
        )
        if (!shouldStart) {
            return false
        }
        consentRepository.acknowledgeSafety()
        disclaimerManager.acceptCurrentVersion()
        return true
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.uiState.collectLatest { uiState ->
                    applyUiModeState()
                    renderPromptBanner(uiState.activePrompt)
                }
            }
        }
    }

    private fun observeSettings() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val coreSettingsFlow = combine(
                    settingsRepository.voiceMode,
                    settingsRepository.preferredUnits,
                    settingsRepository.hazardsEnabled,
                    settingsRepository.lowStressRoutingEnabled,
                    settingsRepository.appearanceMode
                ) { voiceMode, units, hazardsEnabled, lowStressMode, appearanceMode ->
                    SettingsTuple(
                        voiceMode = voiceMode,
                        units = units,
                        hazardsEnabled = hazardsEnabled,
                        lowStressMode = lowStressMode,
                        appearanceMode = appearanceMode
                    )
                }

                combine(
                    coreSettingsFlow,
                    settingsRepository.speedometerEnabled,
                    settingsRepository.speedLimitDisplay,
                    settingsRepository.speedingThreshold,
                    settingsRepository.speedAlertAtThresholdEnabled
                ) { core,
                    speedometerEnabled,
                    speedLimitDisplay,
                    speedingThreshold,
                    speedAlertAtThresholdEnabled ->
                    core.copy(
                        speedometerEnabled = speedometerEnabled,
                        speedLimitDisplay = speedLimitDisplay,
                        speedingThreshold = speedingThreshold,
                        speedAlertAtThresholdEnabled = speedAlertAtThresholdEnabled
                    )
                }.collectLatest { tuple ->
                    val voiceModeSetting = tuple.voiceMode
                    val unitsSetting = tuple.units
                    val hazardsEnabled = tuple.hazardsEnabled
                    val appearanceMode = tuple.appearanceMode
                    val previousLowStressEffective = lowStressModeEnabled
                    val previousStyleUri = currentNavigationStyleUri()
                    val lowStressUiState = lowStressToggleCoordinator.onStoreValue(tuple.lowStressMode)
                    lowStressModeEnabled = lowStressUiState.effectiveEnabled
                    this@MainActivity.hazardsEnabled = hazardsEnabled
                    preferredUnitsSetting = unitsSetting
                    appearanceModeSetting = appearanceMode
                    speedometerEnabledSetting = tuple.speedometerEnabled
                    speedLimitDisplaySetting = tuple.speedLimitDisplay
                    speedingThresholdSetting = tuple.speedingThreshold
                    speedAlertAtThresholdEnabledSetting = tuple.speedAlertAtThresholdEnabled
                    val shouldReloadMapStyle =
                        styleLoaded && previousStyleUri != currentNavigationStyleUri()
                    if (::sessionManager.isInitialized) {
                        sessionManager.setPreferredUnits(unitsSetting)
                        sessionManager.setVoiceMode(voiceModeSetting)
                    }
                    val mappedVoiceMode = when (voiceModeSetting) {
                        VoiceModeSetting.ALL -> VoiceGuidanceMode.FULL
                        VoiceModeSetting.ALERTS -> VoiceGuidanceMode.ALERTS_ONLY
                        VoiceModeSetting.MUTE -> VoiceGuidanceMode.MUTE
                    }
                    val previousMode = voiceGuidanceMode
                    voiceGuidanceMode = mappedVoiceMode
                    if (voiceGuidanceMode == VoiceGuidanceMode.MUTE && previousMode != VoiceGuidanceMode.MUTE) {
                        cancelSpeechGeneration()
                        clearVoicePlaybackQueue()
                        hazardVoiceController.clear()
                        isHazardSpeechPlaying = false
                    }
                    if (!hazardsEnabled) {
                        hazardVoiceController.stopSpeaking()
                        hazardVoiceController.clear()
                    }
                    renderVoiceGuidanceMode()
                    if (binding.navLowStressSwitch.isChecked != lowStressUiState.displayedChecked) {
                        suppressLowStressToggleListener = true
                        binding.navLowStressSwitch.isChecked = lowStressUiState.displayedChecked
                        suppressLowStressToggleListener = false
                    }
                    if (previousLowStressEffective != lowStressModeEnabled) {
                        applyLowStressPreferenceToPreviewRoutesIfNeeded()
                        if (lowStressModeEnabled) {
                            handleActiveNavigationRouteStressUpdates(mapboxNavigation.getNavigationRoutes())
                        }
                    }
                    renderAppearanceModeControl()
                    renderSpeedometer(latestSpeedInfoValue)
                    if (shouldReloadMapStyle) {
                        loadNavigationMapStyle(forceReload = true, resetCamera = false)
                    }
                }
            }
        }
    }

    private fun observeDriverProfile() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                driverProfileRepository.profile.collectLatest { profile ->
                    currentConfidenceScore = profile.confidenceScore
                    currentDriverMode = profile.driverMode
                    currentInstructorModeEnabled = profile.instructorModeEnabled
                    currentOrganisationCode = profile.organisationCode
                }
            }
        }
    }

    private fun renderPromptBanner(prompt: PromptEvent?) {
        promptAutoHideJob?.cancel()
        if (prompt == null) {
            binding.promptBanner.isVisible = false
            hazardVoiceController.clear()
            lastPromptTelemetryType = null
            return
        }
        val nowMs = System.currentTimeMillis()
        if (lastPromptShownAtMs > 0L && nowMs - lastPromptShownAtMs < 2_000L) {
            promptReplacedQuicklyCount += 1
            lastPromptTelemetryType?.let { previousType ->
                emitPromptSuppressedTelemetry(
                    promptType = previousType,
                    reason = "replaced_quickly"
                )
            }
        }
        promptShownCount += 1
        lastPromptShownAtMs = nowMs
        lastPromptTelemetryType = prompt.type
        emitPromptFiredTelemetry(prompt)

        binding.promptBannerIcon.setImageBitmap(promptBannerIconBitmap(prompt.type))
        binding.promptBannerIcon.contentDescription = prompt.message
        binding.promptBannerText.text = prompt.message
        binding.promptBanner.isVisible = true
        updateTopOrnamentsPosition()
        if (shouldSpeakHazardPrompt(prompt)) {
            hazardVoiceController.enqueue(prompt)
        }
        if (prompt.type == PromptType.NO_ENTRY) {
            maybeTriggerNoEntryReroute(prompt)
        }

        promptAutoHideJob = lifecycleScope.launch {
            delay(promptBannerAutoHideMs)
            val activePrompt = mainViewModel.uiState.value.activePrompt
            if (activePrompt?.id == prompt.id) {
                mainViewModel.setActivePrompt(null)
            }
        }
    }

    private fun shouldSpeakHazardPrompt(prompt: PromptEvent): Boolean {
        if (
            prompt.type == PromptType.BUS_LANE ||
            prompt.type == PromptType.GIVE_WAY
        ) {
            return false
        }
        return prompt.confidenceHint >= hazardVoiceMinConfidenceFor(prompt.type)
    }

    private fun emitPromptFiredTelemetry(prompt: PromptEvent) {
        lifecycleScope.launch {
            telemetryRepository.sendEvent(
                TelemetryEvent.App(
                    eventType = "prompt_fired",
                    centreId = selectedCentreId,
                    routeId = activeSessionRouteId,
                    organisationCode = currentOrganisationCode.takeIf { it.isNotBlank() },
                    promptType = prompt.type.name.lowercase(Locale.US),
                    confidenceScore = (prompt.confidenceHint * 100f).roundToInt(),
                    offRouteCount = currentSessionOffRouteEvents,
                    payload = mapOf(
                        "feature_id" to prompt.featureId,
                        "distance_m" to prompt.distanceM,
                        "priority" to prompt.priority
                    )
                )
            )
        }
    }

    private fun emitPromptSuppressedTelemetry(
        promptType: PromptType,
        reason: String
    ) {
        lifecycleScope.launch {
            telemetryRepository.sendEvent(
                TelemetryEvent.App(
                    eventType = "prompt_suppressed",
                    centreId = selectedCentreId,
                    routeId = activeSessionRouteId,
                    organisationCode = currentOrganisationCode.takeIf { it.isNotBlank() },
                    promptType = promptType.name.lowercase(Locale.US),
                    suppressed = true,
                    payload = mapOf("reason" to reason)
                )
            )
        }
    }

    private fun emitOffRouteTelemetry(
        eventType: String,
        rawDistanceMeters: Double,
        smoothedDistanceMeters: Double
    ) {
        lifecycleScope.launch {
            telemetryRepository.sendEvent(
                TelemetryEvent.App(
                    eventType = eventType,
                    centreId = selectedCentreId,
                    routeId = activeSessionRouteId,
                    organisationCode = currentOrganisationCode.takeIf { it.isNotBlank() },
                    offRouteCount = currentSessionOffRouteEvents,
                    payload = mapOf(
                        "raw_distance_m" to rawDistanceMeters.roundToInt(),
                        "smoothed_distance_m" to smoothedDistanceMeters.roundToInt(),
                        "off_route_state" to practiceOffRouteState.name
                    )
                )
            )
        }
    }

    private suspend fun maybeShowPracticalPassPrompt() {
        if (isFinishing || isDestroyed) {
            return
        }
        val profile = driverProfileRepository.profile.first()
        val eligible = PracticalPassPromptEligibility.isEligible(
            mode = profile.driverMode,
            practiceSessionsCompletedCount = profile.practiceSessionsCompletedCount,
            promptAlreadyShown = profile.practicalPassPromptShown
        )
        if (!eligible) {
            return
        }

        val passedPractical = suspendCancellableCoroutine<Boolean> { continuation ->
            PracticalPassPromptDialog.show(this) { passed ->
                if (continuation.isActive) {
                    continuation.resume(passed)
                }
            }
        }

        if (passedPractical) {
            driverProfileRepository.setDriverMode(DriverMode.NEW_DRIVER)
            modeSuggestionApplier.applySuggestionsIfNeeded(DriverMode.NEW_DRIVER)
        }
        driverProfileRepository.setPracticalPassPromptShown(true)
    }

    private fun hazardVoiceMinConfidenceFor(type: PromptType): Float {
        return when (type) {
            PromptType.BUS_STOP -> 0.65f
            PromptType.SCHOOL_ZONE -> 0.70f
            PromptType.NO_ENTRY -> 0.50f
            else -> hazardVoiceMinConfidenceHint
        }
    }

    private fun maybeTriggerNoEntryReroute(prompt: PromptEvent) {
        if (noEntryRerouteInProgress) return
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastNoEntryRerouteAtMs < noEntryRerouteCooldownMs) return
        if (prompt.distanceM > noEntryWarningDistanceMeters.roundToInt()) return

        val origin = latestEnhancedLocationPoint ?: return
        val destination = resolveNoEntryRerouteTargetPoint() ?: return
        val activeRoutePoints = activeRoutePointsForMarkerPlacement()
        if (activeRoutePoints.size < 2) return

        val nearbyNoEntry = NoEntryRestrictionGuard.nearestConflictAhead(
            userPoint = origin,
            routePoints = activeRoutePoints,
            noEntryFeatures = NoEntryRestrictionGuard.noEntryFeatures(hazardFeatures),
            warningDistanceMeters = noEntryWarningDistanceMeters,
            routeMatchDistanceMeters = noEntryRouteMatchDistanceMeters
        ) ?: return

        lifecycleScope.launch {
            noEntryRerouteInProgress = true
            try {
                // Temporarily hide stale maneuver guidance while we replace the route away from restriction.
                binding.maneuverView.isVisible = false
                val reroutedRoutes = requestNoEntryAwareRoutes(
                    origin = origin,
                    destination = destination
                )
                if (reroutedRoutes.isEmpty()) {
                    Log.w(
                        TAG,
                        "No-entry reroute requested but no routes were returned. featureId=${nearbyNoEntry.featureId}"
                    )
                    return@launch
                }
                mapboxNavigation.setNavigationRoutes(reroutedRoutes)
                if (uiMode == UiMode.NAVIGATION && navSessionState == NavSessionState.PREVIEW) {
                    updatePreviewSummaryFromRoutes(reroutedRoutes)
                }
                if (replayEnabled && reroutedRoutes.isNotEmpty()) {
                    syncReplayToRoute(reroutedRoutes.first())
                }
                Log.d(
                    TAG,
                    "No-entry reroute applied. featureId=${nearbyNoEntry.featureId} distanceAheadM=${nearbyNoEntry.distanceAheadMeters.roundToInt()}"
                )
            } finally {
                noEntryRerouteInProgress = false
                lastNoEntryRerouteAtMs = System.currentTimeMillis()
            }
        }
    }

    private fun resolveNoEntryRerouteTargetPoint(): Point? {
        return when (uiMode) {
            UiMode.NAVIGATION -> {
                if (navSessionState == NavSessionState.PREVIEW || navSessionState == NavSessionState.ACTIVE) {
                    selectedDestinationPoint
                } else {
                    null
                }
            }
            UiMode.PRACTICE -> {
                if (
                    practiceRunStage == PracticeRunStage.TO_CENTRE ||
                    practiceRunStage == PracticeRunStage.AT_CENTRE_TRANSITION
                ) {
                    practiceStartPoint ?: selectedCentrePoint()
                } else {
                    null
                }
            }
        }
    }

    private suspend fun requestNoEntryAwareRoutes(
        origin: Point,
        destination: Point
    ): List<NavigationRoute> {
        val candidates = requestRoutesForCoordinates(
            coordinates = listOf(origin, destination),
            alternatives = true
        )
        if (candidates.isEmpty()) return emptyList()

        val noEntryType = setOf(OsmFeatureType.NO_ENTRY)
        val scoredRoutes = candidates.map { route ->
            val routePoints = decodeNavigationRoutePoints(route)
            val noEntryFeatures = if (routePoints.size < 2) {
                emptyList()
            } else {
                runCatching {
                    hazardRepository.getFeaturesForRoute(
                        routePoints = routePoints,
                        radiusMeters = noEntryRerouteRadiusMeters,
                        types = noEntryType,
                        centreId = selectedCentreId
                    )
                }.getOrDefault(emptyList())
            }
            val conflictCount = NoEntryRestrictionGuard.countRouteConflicts(
                routePoints = routePoints,
                noEntryFeatures = noEntryFeatures,
                routeMatchDistanceMeters = noEntryRouteMatchDistanceMeters
            )
            NoEntryScoredRoute(
                route = route,
                conflictCount = conflictCount,
                distanceMeters = route.directionsRoute.distance() ?: Double.MAX_VALUE
            )
        }

        val bestRoute = scoredRoutes.minWithOrNull(
            compareBy<NoEntryScoredRoute> { it.conflictCount }
                .thenBy { it.distanceMeters }
        )?.route ?: return candidates

        val reordered = candidates
            .filterNot { candidate -> candidate == bestRoute }
            .toMutableList()
            .also { list -> list.add(0, bestRoute) }
        val bestScore = scoredRoutes.firstOrNull { it.route == bestRoute }?.conflictCount ?: -1
        Log.d(TAG, "No-entry route scoring: ${scoredRoutes.map { it.conflictCount }} best=$bestScore")
        return reordered
    }

    private fun showExtraPromptsUnavailableBanner() {
        if (extraPromptsUnavailableShown) return
        extraPromptsUnavailableShown = true
        promptAutoHideJob?.cancel()
        binding.promptBannerIcon.setImageBitmap(unavailablePromptIconBitmap())
        binding.promptBannerText.text = getString(R.string.extra_prompts_unavailable)
        binding.promptBanner.isVisible = true
        promptAutoHideJob = lifecycleScope.launch {
            delay(4_000L)
            if (mainViewModel.uiState.value.activePrompt == null) {
                binding.promptBanner.isVisible = false
            }
        }
    }

    private fun showSystemPromptBanner(message: String) {
        promptAutoHideJob?.cancel()
        binding.promptBannerIcon.setImageBitmap(unavailablePromptIconBitmap())
        binding.promptBannerIcon.contentDescription = message
        binding.promptBannerText.text = message
        binding.promptBanner.isVisible = true
        updateTopOrnamentsPosition()
        promptAutoHideJob = lifecycleScope.launch {
            delay(4_000L)
            if (mainViewModel.uiState.value.activePrompt == null) {
                binding.promptBanner.isVisible = false
            }
        }
    }

    private suspend fun maybeShowOfflineDataSourceBanner() {
        if (settingsRepository.consumeOfflineDataSourceBannerSlot()) {
            Snackbar.make(binding.root, getString(R.string.using_offline_data_source), Snackbar.LENGTH_LONG)
                .show()
        }
    }

    private fun toNavSessionState(state: NavigationSessionManager.SessionState): NavSessionState {
        return when (state) {
            NavigationSessionManager.SessionState.BROWSE -> NavSessionState.BROWSE
            NavigationSessionManager.SessionState.PREVIEW -> NavSessionState.PREVIEW
            NavigationSessionManager.SessionState.ACTIVE -> NavSessionState.ACTIVE
        }
    }

    private fun registerDebugReceiver() {
        val filter = IntentFilter(AppFlow.ACTION_DEBUG_COMMAND)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(debugCommandReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(debugCommandReceiver, filter)
        }
    }

    private fun unregisterDebugReceiver() {
        runCatching { unregisterReceiver(debugCommandReceiver) }
    }

    private fun registerNotificationStopReceiver() {
        if (notificationStopReceiverRegistered) return
        val filter = IntentFilter(NavigationForegroundService.ACTION_STOP_GUIDANCE_REQUEST)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(notificationStopReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(notificationStopReceiver, filter)
        }
        notificationStopReceiverRegistered = true
    }

    private fun unregisterNotificationStopReceiver() {
        if (!notificationStopReceiverRegistered) return
        runCatching { unregisterReceiver(notificationStopReceiver) }
        notificationStopReceiverRegistered = false
    }

    private fun handleDebugCommand(command: String) {
        when (command) {
            AppFlow.DEBUG_START_NAV -> {
                if (uiMode != UiMode.NAVIGATION) return
                when (navSessionState) {
                    NavSessionState.BROWSE -> handleNavigationPrimaryAction()
                    NavSessionState.PREVIEW -> beginGuidanceWithSafetyGate()
                    NavSessionState.ACTIVE -> Unit
                }
            }
            AppFlow.DEBUG_STOP_NAV -> {
                if (uiMode == UiMode.NAVIGATION) {
                    stopNavigationSession()
                }
            }
            AppFlow.DEBUG_START_PRACTICE -> {
                if (uiMode == UiMode.PRACTICE) {
                    generatePracticeRoutes()
                }
            }
            AppFlow.DEBUG_STOP_PRACTICE -> {
                if (uiMode == UiMode.PRACTICE) {
                    stopPracticeSession(reason = "stopped")
                }
            }
        }
    }

    private fun stopActiveGuidanceFromNotification() {
        when (uiMode) {
            UiMode.NAVIGATION -> {
                if (navSessionState == NavSessionState.ACTIVE) {
                    stopNavigationSession()
                }
            }
            UiMode.PRACTICE -> {
                if (
                    practiceRunStage == PracticeRunStage.TO_CENTRE ||
                    practiceRunStage == PracticeRunStage.AT_CENTRE_TRANSITION ||
                    practiceRunStage == PracticeRunStage.PRACTICE_ACTIVE
                ) {
                    stopPracticeSession(reason = "notification_action")
                }
            }
        }

        if (foregroundNavigationServiceRunning) {
            NavigationForegroundService.stop(applicationContext)
            foregroundNavigationServiceRunning = false
            foregroundNavigationServiceMode = null
            foregroundNotificationPermissionPromptedForSession = false
        }
    }

    private fun stopPracticeSession(reason: String) {
        val completed = latestRouteCompletionPercent >= practiceRouteFinishPercent
        submitSessionSummary(completed = completed, reason = reason)
        ensureSessionManager()
        sessionManager.stop()
        resetPracticeRunState()
        binding.maneuverView.isVisible = false
        binding.routeProgressBanner.isVisible = false
        applyUiModeState()
    }

    private fun selectedCentreLabel(): String {
        val centreName = selectedCentre?.name ?: defaultTestCentreLabel
        return centreName.removeSuffix(" Driving Test Centre").trim()
    }

    private fun selectedCentrePoint(): Point? {
        val centre = selectedCentre ?: return null
        return Point.fromLngLat(centre.lon, centre.lat)
    }

    private fun initialCameraCenter(): Point {
        return selectedCentrePoint() ?: fallbackTestCentrePoint
    }

    private fun renderDestinationMarker() {
        if (!styleLoaded) return
        val destination = selectedDestinationPoint ?: return
        val manager = destinationAnnotationManager ?: return

        destinationAnnotation?.let { manager.delete(it) }
        destinationAnnotation = manager.create(
            PointAnnotationOptions()
                .withPoint(destination)
                .withIconImage("marker-15")
                .withIconSize(1.8)
        )
    }

    private fun renderHazardMarkers(features: List<OsmFeature>) {
        if (!styleLoaded) return
        val markerManager = hazardAnnotationManager ?: return
        val roadMarkingManager = roadMarkingAnnotationManager ?: return

        markerManager.deleteAll()
        roadMarkingManager.deleteAll()
        if (features.isEmpty()) return

        val dedupedFeatures = features
            .asSequence()
            .filter { feature -> feature.lat.isFinite() && feature.lon.isFinite() }
            .sortedByDescending { feature -> feature.confidenceHint }
            .distinctBy { feature ->
                buildString {
                    append(feature.type.name)
                    append(':')
                    append(String.format(Locale.US, "%.5f", feature.lat))
                    append(':')
                    append(String.format(Locale.US, "%.5f", feature.lon))
                }
            }
            .take(90)
            .toList()

        val visibleFeatures = selectVisibleHazardFeatures(
            features = dedupedFeatures,
            locationPoint = latestEnhancedLocationPoint
        )

        val activeRoutePoints = activeRoutePointsForMarkerPlacement()
        val mapBearing = binding.mapView.mapboxMap.cameraState.bearing
        visibleFeatures.forEach { feature ->
            val promptType = promptTypeForFeatureType(feature.type)
            val baseFeaturePoint = Point.fromLngLat(feature.lon, feature.lat)
            // Keep point advisories visually offset from the route stroke.
            val markerPoint = if (shouldOffsetFromRouteStroke(feature.type)) {
                offsetPointRightOfRoute(
                    featurePoint = baseFeaturePoint,
                    routePoints = activeRoutePoints
                )
            } else {
                baseFeaturePoint
            }
            if (roadMarkingFeatureTypes.contains(feature.type)) {
                val iconBitmap = roadMarkingBitmap(promptType)
                roadMarkingManager.create(
                    PointAnnotationOptions()
                        .withPoint(markerPoint)
                        .withIconImage(iconBitmap)
                        .withIconSize(1.12)
                        .withIconOffset(listOf(0.0, 0.0))
                )
            } else {
                val iconBitmap = hazardMarkerBitmap(promptType)
                markerManager.create(
                    PointAnnotationOptions()
                        .withPoint(markerPoint)
                        .withIconImage(iconBitmap)
                        .withIconSize(markerIconScaleFor(promptType))
                        .withIconOffset(listOf(0.0, 0.0))
                        .withIconRotate(mapBearing)
                )
            }
        }
    }

    private fun shouldOffsetFromRouteStroke(type: OsmFeatureType): Boolean {
        return type == OsmFeatureType.BUS_STOP ||
            type == OsmFeatureType.TRAFFIC_SIGNAL ||
            type == OsmFeatureType.SPEED_CAMERA ||
            type == OsmFeatureType.MINI_ROUNDABOUT ||
            type == OsmFeatureType.NO_ENTRY
    }

    private fun activeRoutePointsForMarkerPlacement(): List<Point> {
        if (activePracticeRoutePolyline.isNotEmpty()) {
            return activePracticeRoutePolyline
        }
        val primary = routeLineApi.getPrimaryNavigationRoute()
            ?: mapboxNavigation.getNavigationRoutes().firstOrNull()
            ?: return emptyList()
        return decodeNavigationRoutePoints(primary)
    }

    /**
     * Offsets marker position to the right side of route direction in screen space (14dp),
     * then converts it back into map coordinate so icons do not overlap the blue route stroke.
     */
    private fun offsetPointRightOfRoute(
        featurePoint: Point,
        routePoints: List<Point>
    ): Point {
        if (routePoints.size < 2) return featurePoint
        val projection = RouteProjection.projectPointOntoRoute(routePoints, featurePoint) ?: return featurePoint
        val segmentStart = routePoints.getOrNull(projection.segmentIndex) ?: return featurePoint
        val segmentEnd = routePoints.getOrNull(projection.segmentIndex + 1) ?: return featurePoint

        val mapboxMap = binding.mapView.mapboxMap
        val startPx = mapboxMap.pixelForCoordinate(segmentStart)
        val endPx = mapboxMap.pixelForCoordinate(segmentEnd)
        val featurePx = mapboxMap.pixelForCoordinate(featurePoint)

        val dx = endPx.x - startPx.x
        val dy = endPx.y - startPx.y
        val magnitude = sqrt((dx * dx) + (dy * dy))
        if (magnitude <= 1e-3) return featurePoint

        val rightX = dy / magnitude
        val rightY = -dx / magnitude
        val offsetPx = dpToPx(14f).toDouble()
        val offsetScreenCoordinate = ScreenCoordinate(
            featurePx.x + (rightX * offsetPx),
            featurePx.y + (rightY * offsetPx)
        )
        return mapboxMap.coordinateForPixel(offsetScreenCoordinate)
    }

    private fun selectVisibleHazardFeatures(
        features: List<OsmFeature>,
        locationPoint: Point?
    ): List<OsmFeature> {
        val applyDistanceCap = when (uiMode) {
            UiMode.NAVIGATION -> navSessionState == NavSessionState.ACTIVE
            UiMode.PRACTICE -> practiceRunStage == PracticeRunStage.TO_CENTRE ||
                practiceRunStage == PracticeRunStage.AT_CENTRE_TRANSITION ||
                practiceRunStage == PracticeRunStage.PRACTICE_ACTIVE
        }
        val sorted = if (locationPoint == null) {
            features.sortedByDescending { it.confidenceHint }
        } else {
            features.sortedWith(
                compareBy<OsmFeature> { feature ->
                    distanceMeters(locationPoint, Point.fromLngLat(feature.lon, feature.lat))
                }.thenByDescending { feature -> feature.confidenceHint }
            )
        }

        val selected = mutableListOf<OsmFeature>()
        val perTypeCount = mutableMapOf<OsmFeatureType, Int>()
        val maxVisible = 36

        sorted.forEach { feature ->
            if (selected.size >= maxVisible) return@forEach
            val markerPoint = Point.fromLngLat(feature.lon, feature.lat)
            if (locationPoint != null && applyDistanceCap) {
                val distanceToUser = distanceMeters(locationPoint, markerPoint)
                val maxDistance = if (roadMarkingFeatureTypes.contains(feature.type)) 2_800.0 else 1_700.0
                if (distanceToUser > maxDistance) {
                    return@forEach
                }
            }

            val countForType = perTypeCount[feature.type] ?: 0
            if (countForType >= maxMarkersPerType(feature.type)) {
                return@forEach
            }

            val minSpacingMeters = minimumMarkerSpacingMeters(feature.type)
            val hasNearbySameType = selected.any { existing ->
                existing.type == feature.type &&
                    distanceMeters(
                        markerPoint,
                        Point.fromLngLat(existing.lon, existing.lat)
                    ) < minSpacingMeters
            }
            if (hasNearbySameType) {
                return@forEach
            }

            selected += feature
            perTypeCount[feature.type] = countForType + 1
        }

        roadMarkingFeatureTypes.forEach { type ->
            if (selected.any { feature -> feature.type == type }) return@forEach
            val fallback = sorted.firstOrNull { feature ->
                feature.type == type && feature !in selected
            } ?: return@forEach
            if (selected.size >= maxVisible) return@forEach
            selected += fallback
        }

        return selected
    }

    private fun maxMarkersPerType(type: OsmFeatureType): Int {
        return when (type) {
            OsmFeatureType.ROUNDABOUT -> 5
            OsmFeatureType.MINI_ROUNDABOUT -> 6
            OsmFeatureType.TRAFFIC_SIGNAL -> 6
            OsmFeatureType.ZEBRA_CROSSING -> 12
            OsmFeatureType.GIVE_WAY -> 10
            OsmFeatureType.SPEED_CAMERA -> 9
            OsmFeatureType.SCHOOL_ZONE -> 4
            OsmFeatureType.BUS_LANE -> 5
            OsmFeatureType.BUS_STOP -> 7
            OsmFeatureType.NO_ENTRY -> 8
        }
    }

    private fun minimumMarkerSpacingMeters(type: OsmFeatureType): Double {
        return when (type) {
            OsmFeatureType.ROUNDABOUT -> 160.0
            OsmFeatureType.MINI_ROUNDABOUT -> 110.0
            OsmFeatureType.TRAFFIC_SIGNAL -> 110.0
            OsmFeatureType.ZEBRA_CROSSING -> 50.0
            OsmFeatureType.GIVE_WAY -> 65.0
            OsmFeatureType.SPEED_CAMERA -> 90.0
            OsmFeatureType.SCHOOL_ZONE -> 220.0
            OsmFeatureType.BUS_LANE -> 160.0
            OsmFeatureType.BUS_STOP -> 110.0
            OsmFeatureType.NO_ENTRY -> 85.0
        }
    }

    private fun maybeRefreshHazardMarkers(nowMs: Long) {
        if (hazardFeatures.isEmpty()) return
        val currentBearing = binding.mapView.mapboxMap.cameraState.bearing
        val bearingDelta = if (lastHazardMarkerRefreshBearing.isNaN()) {
            Double.MAX_VALUE
        } else {
            angularDifferenceDegrees(lastHazardMarkerRefreshBearing, currentBearing)
        }
        // Keep point-advisory marker rotation visually in sync with map bearing
        // without redrawing on every frame.
        if (nowMs - lastHazardMarkerRefreshAtMs < 1_000L && bearingDelta < 2.5) return
        lastHazardMarkerRefreshAtMs = nowMs
        lastHazardMarkerRefreshBearing = currentBearing
        renderHazardMarkers(hazardFeatures)
    }

    private fun angularDifferenceDegrees(a: Double, b: Double): Double {
        val delta = kotlin.math.abs(a - b) % 360.0
        return if (delta > 180.0) 360.0 - delta else delta
    }

    private fun promptTypeForFeatureType(type: OsmFeatureType): PromptType {
        return when (type) {
            OsmFeatureType.ROUNDABOUT -> PromptType.ROUNDABOUT
            OsmFeatureType.MINI_ROUNDABOUT -> PromptType.MINI_ROUNDABOUT
            OsmFeatureType.SCHOOL_ZONE -> PromptType.SCHOOL_ZONE
            OsmFeatureType.ZEBRA_CROSSING -> PromptType.ZEBRA_CROSSING
            OsmFeatureType.GIVE_WAY -> PromptType.GIVE_WAY
            OsmFeatureType.TRAFFIC_SIGNAL -> PromptType.TRAFFIC_SIGNAL
            OsmFeatureType.SPEED_CAMERA -> PromptType.SPEED_CAMERA
            OsmFeatureType.BUS_LANE -> PromptType.BUS_LANE
            OsmFeatureType.BUS_STOP -> PromptType.BUS_STOP
            OsmFeatureType.NO_ENTRY -> PromptType.NO_ENTRY
        }
    }

    private fun promptBannerIconBitmap(type: PromptType): Bitmap {
        return promptBannerBitmapCache.getOrPut(type) {
            createPromptSymbolBitmap(
                type = type,
                sizePx = dpToPx(22f),
                busLaneAsArrow = type == PromptType.BUS_LANE
            )
        }
    }

    private fun hazardMarkerBitmap(type: PromptType): Bitmap {
        return hazardMarkerBitmapCache.getOrPut(type) {
            createPromptSymbolBitmap(
                type = type,
                sizePx = dpToPx(26f)
            )
        }
    }

    private fun roadMarkingBitmap(type: PromptType): Bitmap {
        return roadMarkingBitmapCache.getOrPut(type) {
            createRoadMarkingBitmap(
                type = type,
                sizePx = dpToPx(30f)
            )
        }
    }

    private fun markerIconScaleFor(type: PromptType): Double {
        return when (type) {
            PromptType.ZEBRA_CROSSING -> 1.08
            PromptType.GIVE_WAY -> 1.06
            PromptType.SPEED_CAMERA -> 1.08
            else -> 1.0
        }
    }

    private fun createRoadMarkingBitmap(
        type: PromptType,
        sizePx: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        when (type) {
            PromptType.SCHOOL_ZONE -> drawRoadSchoolMarking(canvas, sizePx.toFloat())
            PromptType.ZEBRA_CROSSING -> drawRoadZebraMarking(canvas, sizePx.toFloat())
            PromptType.GIVE_WAY -> drawRoadGiveWayMarking(canvas, sizePx.toFloat())
            PromptType.SPEED_CAMERA -> drawRoadSpeedCameraMarking(canvas, sizePx.toFloat())
            PromptType.BUS_STOP -> drawRoadBusStopMarking(canvas, sizePx.toFloat())
            else -> {
                val fallback = createPromptSymbolBitmap(type = type, sizePx = sizePx)
                Canvas(bitmap).drawBitmap(fallback, 0f, 0f, null)
            }
        }
        return bitmap
    }

    private fun drawRoadSchoolMarking(canvas: Canvas, sizePx: Float) {
        val signPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#FCD34D")
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#92400E")
            strokeWidth = max(1.8f, sizePx * 0.06f)
        }
        val signRect = RectF(
            sizePx * 0.16f,
            sizePx * 0.18f,
            sizePx * 0.84f,
            sizePx * 0.82f
        )
        canvas.save()
        canvas.rotate(45f, signRect.centerX(), signRect.centerY())
        canvas.drawRoundRect(signRect, sizePx * 0.08f, sizePx * 0.08f, signPaint)
        canvas.drawRoundRect(signRect, sizePx * 0.08f, sizePx * 0.08f, borderPaint)
        canvas.restore()

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111827")
            textAlign = Paint.Align.CENTER
            textSize = sizePx * 0.28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("S", sizePx * 0.5f, sizePx * 0.61f, textPaint)
    }

    private fun createPromptSymbolBitmap(
        type: PromptType,
        sizePx: Int,
        busLaneAsArrow: Boolean = false
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val fillColor = if (type == PromptType.BUS_LANE && busLaneAsArrow) {
            Color.WHITE
        } else {
            promptBadgeFillColor(type)
        }
        val strokeColor = if (type == PromptType.BUS_LANE && busLaneAsArrow) {
            Color.parseColor("#B91C1C")
        } else {
            promptBadgeStrokeColor(type)
        }

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = fillColor
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(2f, sizePx * 0.08f)
            color = strokeColor
        }

        val cx = sizePx / 2f
        val cy = sizePx / 2f
        val radius = (sizePx / 2f) - (strokePaint.strokeWidth / 2f) - 1f
        canvas.drawCircle(cx, cy, radius, fillPaint)
        canvas.drawCircle(cx, cy, radius, strokePaint)

        val symbolRadius = radius * 0.72f
        val symbolBounds = RectF(
            cx - symbolRadius,
            cy - symbolRadius,
            cx + symbolRadius,
            cy + symbolRadius
        )

        when (type) {
            PromptType.TRAFFIC_SIGNAL -> drawTrafficSignalSymbol(canvas, symbolBounds)
            PromptType.ZEBRA_CROSSING -> drawZebraSymbol(canvas, symbolBounds)
            PromptType.GIVE_WAY -> drawGiveWaySymbol(canvas, symbolBounds)
            PromptType.SCHOOL_ZONE -> drawSchoolSymbol(canvas, symbolBounds)
            PromptType.ROUNDABOUT -> drawRoundaboutSymbol(canvas, symbolBounds)
            PromptType.MINI_ROUNDABOUT -> drawMiniRoundaboutSymbol(canvas, symbolBounds)
            PromptType.SPEED_CAMERA -> drawSpeedCameraSymbol(canvas, symbolBounds)
            PromptType.BUS_LANE -> {
                if (busLaneAsArrow) {
                    drawBusLaneArrowSymbol(canvas, symbolBounds)
                } else {
                    drawBusSymbol(canvas, symbolBounds)
                }
            }
            PromptType.BUS_STOP -> drawBusStopSymbol(canvas, symbolBounds)
            PromptType.NO_ENTRY -> drawNoEntrySymbol(canvas, symbolBounds)
        }

        return bitmap
    }

    private fun drawRoadZebraMarking(canvas: Canvas, sizePx: Float) {
        val baseRect = RectF(
            sizePx * 0.1f,
            sizePx * 0.25f,
            sizePx * 0.9f,
            sizePx * 0.75f
        )
        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#111827")
            alpha = 220
        }
        val baseStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#F9FAFB")
            strokeWidth = max(1.6f, sizePx * 0.05f)
        }
        canvas.drawRoundRect(baseRect, sizePx * 0.08f, sizePx * 0.08f, basePaint)
        canvas.drawRoundRect(baseRect, sizePx * 0.08f, sizePx * 0.08f, baseStroke)

        val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        val stripeWidth = baseRect.width() * 0.11f
        val stripeGap = baseRect.width() * 0.06f
        var left = baseRect.left + baseRect.width() * 0.08f
        repeat(5) {
            val stripe = RectF(
                left,
                baseRect.top + baseRect.height() * 0.12f,
                left + stripeWidth,
                baseRect.bottom - baseRect.height() * 0.12f
            )
            canvas.drawRoundRect(stripe, stripeWidth * 0.22f, stripeWidth * 0.22f, stripePaint)
            left += stripeWidth + stripeGap
        }
    }

    private fun drawRoadGiveWayMarking(canvas: Canvas, sizePx: Float) {
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.WHITE
            strokeWidth = max(1.8f, sizePx * 0.06f)
        }
        val lineStart = sizePx * 0.16f
        val lineEnd = sizePx * 0.84f
        val lineY = sizePx * 0.75f
        canvas.drawLine(lineStart, lineY, lineEnd, lineY, linePaint)

        val toothFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        val toothStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#EF4444")
            strokeWidth = max(1.4f, sizePx * 0.045f)
        }

        val spacing = sizePx * 0.21f
        val startX = sizePx * 0.29f
        repeat(3) { idx ->
            val cx = startX + (idx * spacing)
            val path = Path().apply {
                moveTo(cx, sizePx * 0.26f)
                lineTo(cx - sizePx * 0.08f, sizePx * 0.56f)
                lineTo(cx + sizePx * 0.08f, sizePx * 0.56f)
                close()
            }
            canvas.drawPath(path, toothFill)
            canvas.drawPath(path, toothStroke)
        }
    }

    private fun drawRoadSpeedCameraMarking(canvas: Canvas, sizePx: Float) {
        val signPath = Path().apply {
            moveTo(sizePx * 0.5f, sizePx * 0.15f)
            lineTo(sizePx * 0.84f, sizePx * 0.74f)
            lineTo(sizePx * 0.16f, sizePx * 0.74f)
            close()
        }
        val signFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        val signStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#DC2626")
            strokeWidth = max(2f, sizePx * 0.07f)
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(signPath, signFill)
        canvas.drawPath(signPath, signStroke)

        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#1F2937")
        }
        val lensPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#60A5FA")
        }
        val bodyRect = RectF(
            sizePx * 0.34f,
            sizePx * 0.42f,
            sizePx * 0.66f,
            sizePx * 0.58f
        )
        canvas.drawRoundRect(bodyRect, sizePx * 0.04f, sizePx * 0.04f, bodyPaint)
        canvas.drawCircle(bodyRect.centerX(), bodyRect.centerY(), sizePx * 0.065f, lensPaint)
    }

    private fun drawRoadBusStopMarking(canvas: Canvas, sizePx: Float) {
        val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#1E3A8A")
            alpha = 235
        }
        val panelStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.WHITE
            strokeWidth = max(1.6f, sizePx * 0.05f)
        }
        val panelRect = RectF(
            sizePx * 0.14f,
            sizePx * 0.24f,
            sizePx * 0.86f,
            sizePx * 0.76f
        )
        canvas.drawRoundRect(panelRect, sizePx * 0.1f, sizePx * 0.1f, panelPaint)
        canvas.drawRoundRect(panelRect, sizePx * 0.1f, sizePx * 0.1f, panelStroke)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = sizePx * 0.2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("BUS", sizePx * 0.5f, sizePx * 0.58f, textPaint)
    }

    private fun drawTrafficSignalSymbol(canvas: Canvas, bounds: RectF) {
        val housingWidth = bounds.width() * 0.42f
        val housingHeight = bounds.height() * 0.82f
        val left = bounds.centerX() - housingWidth / 2f
        val top = bounds.centerY() - housingHeight / 2f
        val housingRect = RectF(left, top, left + housingWidth, top + housingHeight)

        val housingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#111827")
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(1.5f, bounds.width() * 0.06f)
            color = Color.WHITE
        }
        canvas.drawRoundRect(housingRect, housingWidth * 0.22f, housingWidth * 0.22f, housingPaint)
        canvas.drawRoundRect(housingRect, housingWidth * 0.22f, housingWidth * 0.22f, borderPaint)

        val lightRadius = housingWidth * 0.18f
        val gap = housingHeight * 0.24f
        val startY = housingRect.top + housingHeight * 0.2f
        val signalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        signalPaint.color = Color.parseColor("#EF4444")
        canvas.drawCircle(housingRect.centerX(), startY, lightRadius, signalPaint)
        signalPaint.color = Color.parseColor("#F59E0B")
        canvas.drawCircle(housingRect.centerX(), startY + gap, lightRadius, signalPaint)
        signalPaint.color = Color.parseColor("#22C55E")
        canvas.drawCircle(housingRect.centerX(), startY + (gap * 2f), lightRadius, signalPaint)
    }

    private fun drawZebraSymbol(canvas: Canvas, bounds: RectF) {
        val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#111827")
            strokeWidth = max(1.5f, bounds.width() * 0.05f)
        }

        canvas.save()
        canvas.rotate(-22f, bounds.centerX(), bounds.centerY())
        val stripeCount = 4
        val stripeWidth = bounds.width() * 0.18f
        val gap = bounds.width() * 0.07f
        var x = bounds.left + bounds.width() * 0.12f
        repeat(stripeCount) {
            val rect = RectF(x, bounds.top + bounds.height() * 0.18f, x + stripeWidth, bounds.bottom - bounds.height() * 0.18f)
            canvas.drawRoundRect(rect, stripeWidth * 0.2f, stripeWidth * 0.2f, stripePaint)
            canvas.drawRoundRect(rect, stripeWidth * 0.2f, stripeWidth * 0.2f, outlinePaint)
            x += stripeWidth + gap
        }
        canvas.restore()
    }

    private fun drawGiveWaySymbol(canvas: Canvas, bounds: RectF) {
        val outerPath = Path().apply {
            moveTo(bounds.centerX(), bounds.top + bounds.height() * 0.1f)
            lineTo(bounds.right - bounds.width() * 0.12f, bounds.bottom - bounds.height() * 0.12f)
            lineTo(bounds.left + bounds.width() * 0.12f, bounds.bottom - bounds.height() * 0.12f)
            close()
        }
        val innerPath = Path().apply {
            moveTo(bounds.centerX(), bounds.top + bounds.height() * 0.27f)
            lineTo(bounds.right - bounds.width() * 0.27f, bounds.bottom - bounds.height() * 0.24f)
            lineTo(bounds.left + bounds.width() * 0.27f, bounds.bottom - bounds.height() * 0.24f)
            close()
        }
        val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#B91C1C")
            strokeWidth = max(1.8f, bounds.width() * 0.07f)
        }
        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#EF4444")
        }
        canvas.drawPath(outerPath, outerPaint)
        canvas.drawPath(outerPath, borderPaint)
        canvas.drawPath(innerPath, innerPaint)
    }

    private fun drawSchoolSymbol(canvas: Canvas, bounds: RectF) {
        val roofPath = Path().apply {
            moveTo(bounds.left + bounds.width() * 0.12f, bounds.top + bounds.height() * 0.5f)
            lineTo(bounds.centerX(), bounds.top + bounds.height() * 0.18f)
            lineTo(bounds.right - bounds.width() * 0.12f, bounds.top + bounds.height() * 0.5f)
            close()
        }
        val bodyRect = RectF(
            bounds.left + bounds.width() * 0.24f,
            bounds.top + bounds.height() * 0.5f,
            bounds.right - bounds.width() * 0.24f,
            bounds.bottom - bounds.height() * 0.14f
        )
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#111827")
            strokeWidth = max(1.5f, bounds.width() * 0.05f)
        }
        canvas.drawPath(roofPath, fillPaint)
        canvas.drawPath(roofPath, strokePaint)
        canvas.drawRoundRect(bodyRect, bounds.width() * 0.06f, bounds.width() * 0.06f, fillPaint)
        canvas.drawRoundRect(bodyRect, bounds.width() * 0.06f, bounds.width() * 0.06f, strokePaint)

        val doorRect = RectF(
            bounds.centerX() - bounds.width() * 0.1f,
            bodyRect.top + bodyRect.height() * 0.45f,
            bounds.centerX() + bounds.width() * 0.1f,
            bodyRect.bottom
        )
        val doorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#2563EB")
        }
        canvas.drawRoundRect(doorRect, bounds.width() * 0.03f, bounds.width() * 0.03f, doorPaint)
    }

    private fun drawRoundaboutSymbol(canvas: Canvas, bounds: RectF) {
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.WHITE
            strokeWidth = max(2f, bounds.width() * 0.12f)
        }
        val ringRect = RectF(
            bounds.left + bounds.width() * 0.16f,
            bounds.top + bounds.height() * 0.16f,
            bounds.right - bounds.width() * 0.16f,
            bounds.bottom - bounds.height() * 0.16f
        )
        canvas.drawArc(ringRect, 0f, 320f, false, ringPaint)

        val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        val centerX = ringRect.centerX()
        val centerY = ringRect.centerY()
        val arrowDistance = ringRect.width() * 0.42f
        val arrowSize = bounds.width() * 0.11f

        listOf(40.0, 160.0, 280.0).forEach { angleDeg ->
            val angleRad = Math.toRadians(angleDeg)
            val x = centerX + (kotlin.math.cos(angleRad) * arrowDistance).toFloat()
            val y = centerY + (kotlin.math.sin(angleRad) * arrowDistance).toFloat()
            drawTriangle(
                canvas = canvas,
                centerX = x,
                centerY = y,
                size = arrowSize,
                rotationDegrees = angleDeg.toFloat() + 92f,
                paint = arrowPaint
            )
        }
    }

    private fun drawMiniRoundaboutSymbol(canvas: Canvas, bounds: RectF) {
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.WHITE
            strokeWidth = max(2f, bounds.width() * 0.11f)
        }
        val centerDiskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#FEF3C7")
        }
        val centerDiskStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#92400E")
            strokeWidth = max(1.2f, bounds.width() * 0.045f)
        }
        val ringRect = RectF(
            bounds.left + bounds.width() * 0.2f,
            bounds.top + bounds.height() * 0.2f,
            bounds.right - bounds.width() * 0.2f,
            bounds.bottom - bounds.height() * 0.2f
        )
        canvas.drawArc(ringRect, 0f, 300f, false, ringPaint)
        val centerRadius = bounds.width() * 0.14f
        canvas.drawCircle(bounds.centerX(), bounds.centerY(), centerRadius, centerDiskPaint)
        canvas.drawCircle(bounds.centerX(), bounds.centerY(), centerRadius, centerDiskStroke)

        val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        val arrowDistance = ringRect.width() * 0.42f
        val arrowSize = bounds.width() * 0.09f
        listOf(45.0, 175.0, 300.0).forEach { angleDeg ->
            val angleRad = Math.toRadians(angleDeg)
            val x = ringRect.centerX() + (kotlin.math.cos(angleRad) * arrowDistance).toFloat()
            val y = ringRect.centerY() + (kotlin.math.sin(angleRad) * arrowDistance).toFloat()
            drawTriangle(
                canvas = canvas,
                centerX = x,
                centerY = y,
                size = arrowSize,
                rotationDegrees = angleDeg.toFloat() + 95f,
                paint = arrowPaint
            )
        }
    }

    private fun drawBusSymbol(canvas: Canvas, bounds: RectF) {
        val busBody = RectF(
            bounds.left + bounds.width() * 0.12f,
            bounds.top + bounds.height() * 0.26f,
            bounds.right - bounds.width() * 0.12f,
            bounds.bottom - bounds.height() * 0.2f
        )
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#0F172A")
            strokeWidth = max(1.2f, bounds.width() * 0.045f)
        }
        canvas.drawRoundRect(busBody, bounds.width() * 0.1f, bounds.width() * 0.1f, bodyPaint)
        canvas.drawRoundRect(busBody, bounds.width() * 0.1f, bounds.width() * 0.1f, strokePaint)

        val windowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#93C5FD")
        }
        val windowTop = busBody.top + busBody.height() * 0.2f
        val windowBottom = busBody.top + busBody.height() * 0.48f
        val windowWidth = busBody.width() * 0.2f
        var windowLeft = busBody.left + busBody.width() * 0.1f
        repeat(3) {
            canvas.drawRoundRect(
                RectF(windowLeft, windowTop, windowLeft + windowWidth, windowBottom),
                bounds.width() * 0.03f,
                bounds.width() * 0.03f,
                windowPaint
            )
            windowLeft += windowWidth + busBody.width() * 0.06f
        }

        val wheelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#0F172A")
        }
        val wheelRadius = bounds.width() * 0.08f
        val wheelY = busBody.bottom + wheelRadius * 0.6f
        canvas.drawCircle(busBody.left + busBody.width() * 0.22f, wheelY, wheelRadius, wheelPaint)
        canvas.drawCircle(busBody.right - busBody.width() * 0.22f, wheelY, wheelRadius, wheelPaint)
    }

    private fun drawBusStopSymbol(canvas: Canvas, bounds: RectF) {
        val polePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#1F2937")
        }
        val signFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        val signStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#1D4ED8")
            strokeWidth = max(1.4f, bounds.width() * 0.05f)
        }
        val busPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#2563EB")
        }

        val poleWidth = bounds.width() * 0.09f
        val poleRect = RectF(
            bounds.centerX() - poleWidth / 2f,
            bounds.centerY() - bounds.height() * 0.02f,
            bounds.centerX() + poleWidth / 2f,
            bounds.bottom - bounds.height() * 0.08f
        )
        canvas.drawRoundRect(poleRect, poleWidth * 0.35f, poleWidth * 0.35f, polePaint)

        val signRect = RectF(
            bounds.left + bounds.width() * 0.2f,
            bounds.top + bounds.height() * 0.14f,
            bounds.right - bounds.width() * 0.2f,
            bounds.centerY() + bounds.height() * 0.08f
        )
        canvas.drawRoundRect(signRect, bounds.width() * 0.09f, bounds.width() * 0.09f, signFillPaint)
        canvas.drawRoundRect(signRect, bounds.width() * 0.09f, bounds.width() * 0.09f, signStrokePaint)

        val busRect = RectF(
            signRect.left + signRect.width() * 0.2f,
            signRect.top + signRect.height() * 0.32f,
            signRect.right - signRect.width() * 0.2f,
            signRect.bottom - signRect.height() * 0.2f
        )
        canvas.drawRoundRect(busRect, bounds.width() * 0.06f, bounds.width() * 0.06f, busPaint)

        val wheelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        val wheelRadius = bounds.width() * 0.045f
        val wheelY = busRect.bottom - wheelRadius
        canvas.drawCircle(busRect.left + busRect.width() * 0.24f, wheelY, wheelRadius, wheelPaint)
        canvas.drawCircle(busRect.right - busRect.width() * 0.24f, wheelY, wheelRadius, wheelPaint)
    }

    private fun drawNoEntrySymbol(canvas: Canvas, bounds: RectF) {
        val ringFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#DC2626")
        }
        val ringStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.WHITE
            strokeWidth = max(1.8f, bounds.width() * 0.09f)
        }
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }

        val outerRadius = bounds.width() * 0.44f
        val centerX = bounds.centerX()
        val centerY = bounds.centerY()
        canvas.drawCircle(centerX, centerY, outerRadius, ringFill)
        canvas.drawCircle(centerX, centerY, outerRadius, ringStroke)

        val barHeight = bounds.height() * 0.18f
        val barRect = RectF(
            bounds.left + bounds.width() * 0.2f,
            centerY - barHeight / 2f,
            bounds.right - bounds.width() * 0.2f,
            centerY + barHeight / 2f
        )
        canvas.drawRoundRect(barRect, barHeight / 2f, barHeight / 2f, barPaint)
    }

    private fun drawSpeedCameraSymbol(canvas: Canvas, bounds: RectF) {
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        val bodyStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#7F1D1D")
            strokeWidth = max(1.4f, bounds.width() * 0.05f)
        }
        val lensPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#1D4ED8")
        }
        val flashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#FACC15")
        }

        val cameraBody = RectF(
            bounds.left + bounds.width() * 0.12f,
            bounds.top + bounds.height() * 0.34f,
            bounds.right - bounds.width() * 0.18f,
            bounds.bottom - bounds.height() * 0.24f
        )
        canvas.drawRoundRect(cameraBody, bounds.width() * 0.08f, bounds.width() * 0.08f, bodyPaint)
        canvas.drawRoundRect(cameraBody, bounds.width() * 0.08f, bounds.width() * 0.08f, bodyStroke)

        val hood = Path().apply {
            moveTo(cameraBody.right - bounds.width() * 0.04f, cameraBody.top + bounds.height() * 0.12f)
            lineTo(bounds.right - bounds.width() * 0.02f, bounds.top + bounds.height() * 0.42f)
            lineTo(cameraBody.right - bounds.width() * 0.06f, cameraBody.bottom - bounds.height() * 0.06f)
            close()
        }
        canvas.drawPath(hood, bodyPaint)
        canvas.drawPath(hood, bodyStroke)

        val lensRadius = bounds.width() * 0.14f
        canvas.drawCircle(
            cameraBody.left + cameraBody.width() * 0.45f,
            cameraBody.centerY(),
            lensRadius,
            lensPaint
        )
        canvas.drawCircle(
            cameraBody.left + cameraBody.width() * 0.45f,
            cameraBody.centerY(),
            lensRadius,
            bodyStroke
        )

        val flashRect = RectF(
            cameraBody.left + cameraBody.width() * 0.72f,
            cameraBody.top + cameraBody.height() * 0.2f,
            cameraBody.right - cameraBody.width() * 0.08f,
            cameraBody.top + cameraBody.height() * 0.46f
        )
        canvas.drawRoundRect(flashRect, bounds.width() * 0.03f, bounds.width() * 0.03f, flashPaint)
    }

    private fun drawBusLaneArrowSymbol(canvas: Canvas, bounds: RectF) {
        val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#DC2626")
        }
        val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#7F1D1D")
            strokeWidth = max(1.3f, bounds.width() * 0.05f)
            strokeJoin = Paint.Join.ROUND
        }

        val path = Path().apply {
            val left = bounds.left + bounds.width() * 0.2f
            val right = bounds.right - bounds.width() * 0.2f
            val top = bounds.top + bounds.height() * 0.18f
            val midY = bounds.centerY()
            val bottom = bounds.bottom - bounds.height() * 0.18f
            val shaftWidth = bounds.width() * 0.22f

            moveTo(left + shaftWidth, bottom)
            lineTo(left + shaftWidth, midY + shaftWidth * 0.6f)
            lineTo(right, midY + shaftWidth * 0.6f)
            lineTo(right, bottom)
            lineTo(right + shaftWidth, bottom)
            lineTo(right + shaftWidth, midY)
            lineTo(right + shaftWidth, midY)
            lineTo(right + shaftWidth, top)
            lineTo(left + shaftWidth, top)
            lineTo(left + shaftWidth, top + shaftWidth * 0.4f)
            lineTo(left, top + shaftWidth * 0.4f)
            lineTo(left + bounds.width() * 0.42f, bounds.top + bounds.height() * 0.02f)
            lineTo(left + bounds.width() * 0.84f, top + shaftWidth * 0.4f)
            lineTo(right + shaftWidth * 0.45f, top + shaftWidth * 0.4f)
            lineTo(right + shaftWidth * 0.45f, midY + shaftWidth * 0.6f)
            lineTo(right + shaftWidth * 0.95f, midY + shaftWidth * 0.6f)
            lineTo(right + shaftWidth * 0.95f, bottom + shaftWidth * 0.95f)
            lineTo(right - shaftWidth * 0.2f, bottom + shaftWidth * 0.95f)
            lineTo(right - shaftWidth * 0.2f, bottom)
            close()
        }
        canvas.drawPath(path, arrowPaint)
        canvas.drawPath(path, outlinePaint)
    }

    private fun drawTriangle(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        size: Float,
        rotationDegrees: Float,
        paint: Paint
    ) {
        val path = Path().apply {
            moveTo(centerX, centerY - size)
            lineTo(centerX - size * 0.6f, centerY + size * 0.7f)
            lineTo(centerX + size * 0.6f, centerY + size * 0.7f)
            close()
        }
        canvas.save()
        canvas.rotate(rotationDegrees, centerX, centerY)
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    private fun promptBadgeFillColor(type: PromptType): Int {
        return when (type) {
            PromptType.TRAFFIC_SIGNAL -> Color.parseColor("#B91C1C")
            PromptType.ZEBRA_CROSSING -> Color.parseColor("#111827")
            PromptType.GIVE_WAY -> Color.parseColor("#EF4444")
            PromptType.SCHOOL_ZONE -> Color.parseColor("#FBBF24")
            PromptType.ROUNDABOUT -> Color.parseColor("#2563EB")
            PromptType.MINI_ROUNDABOUT -> Color.parseColor("#1D4ED8")
            PromptType.SPEED_CAMERA -> Color.parseColor("#DC2626")
            PromptType.BUS_LANE -> Color.parseColor("#0F766E")
            PromptType.BUS_STOP -> Color.parseColor("#F97316")
            PromptType.NO_ENTRY -> Color.parseColor("#B91C1C")
        }
    }

    private fun promptBadgeStrokeColor(type: PromptType): Int {
        return when (type) {
            PromptType.TRAFFIC_SIGNAL -> Color.parseColor("#FECACA")
            PromptType.ZEBRA_CROSSING -> Color.parseColor("#D1D5DB")
            PromptType.GIVE_WAY -> Color.parseColor("#FEE2E2")
            PromptType.SCHOOL_ZONE -> Color.parseColor("#92400E")
            PromptType.ROUNDABOUT -> Color.parseColor("#DBEAFE")
            PromptType.MINI_ROUNDABOUT -> Color.parseColor("#BFDBFE")
            PromptType.SPEED_CAMERA -> Color.parseColor("#FECACA")
            PromptType.BUS_LANE -> Color.parseColor("#CCFBF1")
            PromptType.BUS_STOP -> Color.parseColor("#FED7AA")
            PromptType.NO_ENTRY -> Color.parseColor("#FEE2E2")
        }
    }

    private fun dpToPx(dp: Float): Int {
        return (dp * resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
    }

    private fun unavailablePromptIconBitmap(): Bitmap {
        return unavailablePromptBannerBitmap ?: run {
            val bitmap = Bitmap.createBitmap(dpToPx(24f), dpToPx(24f), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#374151")
                style = Paint.Style.FILL
            }
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#D1D5DB")
                style = Paint.Style.STROKE
                strokeWidth = max(2f, bitmap.width * 0.08f)
            }
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
                textSize = dpToPx(10f).toFloat()
                typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            }
            val cx = bitmap.width / 2f
            val cy = bitmap.height / 2f
            val radius = (bitmap.width / 2f) - (strokePaint.strokeWidth / 2f) - 1f
            canvas.drawCircle(cx, cy, radius, fillPaint)
            canvas.drawCircle(cx, cy, radius, strokePaint)
            val baseline = cy - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText("!", cx, baseline, textPaint)
            unavailablePromptBannerBitmap = bitmap
            bitmap
        }
    }

    private fun handleNavigationPrimaryAction() {
        when (navSessionState) {
            NavSessionState.BROWSE -> {
                val destination = selectedDestinationPoint
                if (destination == null) {
                    Toast.makeText(this, getString(R.string.destination_required), Toast.LENGTH_SHORT).show()
                } else {
                    ensureSessionManager()
                    sessionManager.previewDestination(destination)
                }
            }
            NavSessionState.PREVIEW -> beginGuidanceWithSafetyGate()
            NavSessionState.ACTIVE -> {
                // Active session is controlled by Stop.
            }
        }
    }

    private fun previewNavigationToDestination() {
        val destination = selectedDestinationPoint
        if (destination == null) {
            Toast.makeText(this, getString(R.string.destination_required), Toast.LENGTH_SHORT).show()
            return
        }

        binding.startNavigation.isEnabled = false
        lifecycleScope.launch {
            val origin = latestEnhancedLocationPoint ?: selectedCentrePoint() ?: fallbackTestCentrePoint
            val previewRoutes = requestRoutesForCoordinates(
                coordinates = listOf(origin, destination),
                alternatives = true
            )
            if (previewRoutes.isNotEmpty()) {
                val prioritizedRoutes = prioritizeRoutesForConfidence(previewRoutes)
                navSessionState = NavSessionState.PREVIEW
                mainViewModel.setSessionState(NavigationSessionManager.SessionState.PREVIEW)
                mapboxNavigation.setNavigationRoutes(prioritizedRoutes)
                updatePreviewSummaryFromRoutes(prioritizedRoutes)
                binding.maneuverView.isVisible = false
                binding.routeProgressBanner.isVisible = false
            } else {
                navSessionState = NavSessionState.BROWSE
                mainViewModel.setSessionState(NavigationSessionManager.SessionState.BROWSE)
                clearPreviewSummary()
                binding.maneuverView.isVisible = false
                binding.routeProgressBanner.isVisible = false
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.nav_preview_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
            applyUiModeState()
            binding.startNavigation.isEnabled = true
        }
    }

    private fun beginGuidanceWithSafetyGate() {
        lifecycleScope.launch {
            if (!requestSafetyAcknowledgementIfNeeded()) return@launch
            beginGuidance()
        }
    }

    private fun beginGuidance() {
        if (uiMode != UiMode.NAVIGATION || navSessionState != NavSessionState.PREVIEW) {
            return
        }
        extraPromptsUnavailableShown = false
        ensureSessionManager()
        if (replayEnabled) {
            syncReplayToPrimaryRoute()
        }
        setNavigationCameraMode(NavigationCameraModePolicy.onSessionStart())
        sessionManager.start()
        navSessionState = NavSessionState.ACTIVE
        val activeNavRoute = mapboxNavigation.getNavigationRoutes().firstOrNull()
        val navIntelligence = activeNavRoute?.let { route ->
            routeIntelligenceEngine.evaluate(
                routeGeometry = decodeNavigationRoutePoints(route),
                hazards = hazardFeatures
            )
        }
        currentActiveRouteStressIndex = navIntelligence?.stressIndex
        currentActiveRouteDifficultyLabel = navIntelligence?.difficultyLabel
        renderActiveRouteStressBanner()
        startSessionSummaryTracking(
            mode = UiMode.NAVIGATION,
            routeId = activeNavRoute?.id,
            initialDistanceM = activeNavRoute?.directionsRoute?.distance() ?: 0.0,
            intelligence = navIntelligence
        )
        binding.maneuverView.isVisible = true
        binding.routeProgressBanner.isVisible = true
        binding.navPreviewSummaryBanner.isVisible = false
        applyUiModeState()
    }

    private fun stopNavigationSession() {
        if (uiMode != UiMode.NAVIGATION) return
        val completed = latestRouteCompletionPercent >= practiceRouteFinishPercent
        submitSessionSummary(completed = completed, reason = "stopped")
        ensureSessionManager()
        sessionManager.stop()
        latestNearestMiniRoundaboutMeters = Double.MAX_VALUE
        navSessionState = NavSessionState.BROWSE
        setNavigationCameraMode(NavigationCameraMode.FOLLOW)
        extraPromptsUnavailableShown = false
        mainViewModel.setActivePrompt(null)
        hazardVoiceController.stopSpeaking()
        hazardVoiceController.clear()
        binding.maneuverView.isVisible = false
        binding.routeProgressBanner.isVisible = false
        currentActiveRouteStressIndex = null
        currentActiveRouteDifficultyLabel = null
        renderActiveRouteStressBanner()
        clearPreviewSummary()
        applyUiModeState()
    }

    private fun handleActiveNavigationRouteStressUpdates(routes: List<NavigationRoute>) {
        if (uiMode != UiMode.NAVIGATION || navSessionState != NavSessionState.ACTIVE) return
        if (routes.isEmpty()) return

        val evaluations = routes.map { route -> evaluateRouteStress(route) }
        val primary = evaluations.first()
        val previousStress = currentActiveRouteStressIndex
        currentActiveRouteStressIndex = primary.stressIndex
        currentActiveRouteDifficultyLabel = primary.difficultyLabel
        activeSessionIntelligence = primary.summary
        renderActiveRouteStressBanner()

        if (
            !lowStressModeEnabled &&
            previousStress != null &&
            previousStress != primary.stressIndex
        ) {
            Toast.makeText(this, getString(R.string.stress_level_updated_toast), Toast.LENGTH_SHORT).show()
        }

        if (!lowStressModeEnabled || evaluations.size < 2) return

        val best = evaluations.minWithOrNull(
            compareBy<RouteStressEvaluation> { it.stressIndex }
                .thenBy { it.etaSeconds }
                .thenBy { it.distanceMeters }
        ) ?: return

        if (best.route == primary.route) return

        val shouldSwitch = StressAdjustmentPolicy.shouldSwitchToLowerStressRoute(
            current = StressAdjustmentPolicy.RouteStressSnapshot(
                stressIndex = primary.stressIndex,
                etaSeconds = primary.etaSeconds,
                distanceMeters = primary.distanceMeters
            ),
            candidate = StressAdjustmentPolicy.RouteStressSnapshot(
                stressIndex = best.stressIndex,
                etaSeconds = best.etaSeconds,
                distanceMeters = best.distanceMeters
            )
        )
        if (!shouldSwitch) return

        val reorderedRoutes = routes
            .filterNot { route -> route == best.route }
            .toMutableList()
            .also { list -> list.add(0, best.route) }
        mapboxNavigation.setNavigationRoutes(reorderedRoutes)

        maybeAnnounceStressAdjustment()
        Log.d(
            TAG,
            "Live low-stress switch applied. currentStress=${primary.stressIndex} bestStress=${best.stressIndex} etaDeltaS=${(best.etaSeconds - primary.etaSeconds).roundToInt()}"
        )
    }

    private fun evaluateRouteStress(route: NavigationRoute): RouteStressEvaluation {
        val summary = routeIntelligenceEngine.evaluate(
            routeGeometry = decodeNavigationRoutePoints(route),
            hazards = hazardFeatures
        )
        return RouteStressEvaluation(
            route = route,
            summary = summary,
            stressIndex = summary.stressIndex,
            difficultyLabel = summary.difficultyLabel,
            etaSeconds = route.directionsRoute.duration() ?: Double.MAX_VALUE,
            distanceMeters = route.directionsRoute.distance() ?: Double.MAX_VALUE
        )
    }

    private fun maybeAnnounceStressAdjustment() {
        val nowMs = System.currentTimeMillis()
        if (
            !StressAdjustmentPolicy.canAnnounceAdjustment(
                nowMs = nowMs,
                lastAnnouncementAtMs = lastStressAdjustmentAnnouncementAtMs
            )
        ) {
            return
        }
        lastStressAdjustmentAnnouncementAtMs = nowMs
        playSystemAnnouncement(getString(R.string.stress_adjustment_selected_announcement))
    }

    private fun renderActiveRouteStressBanner() {
        val stressIndex = currentActiveRouteStressIndex
        val difficultyLabel = currentActiveRouteDifficultyLabel
        val shouldShow = uiMode == UiMode.NAVIGATION &&
            navSessionState == NavSessionState.ACTIVE &&
            stressIndex != null &&
            difficultyLabel != null
        if (!shouldShow) {
            binding.routeStressValue.isVisible = false
            binding.routeStressValue.text = getString(R.string.route_progress_stress_placeholder)
            return
        }
        val safeStressIndex = stressIndex ?: return
        val safeDifficultyLabel = difficultyLabel ?: return
        binding.routeStressValue.isVisible = true
        binding.routeStressValue.text = getString(
            R.string.route_progress_stress_value,
            formatDifficultyLabel(safeDifficultyLabel),
            safeStressIndex
        )
    }

    private fun prioritizeRoutesForConfidence(routes: List<NavigationRoute>): List<NavigationRoute> {
        if (routes.size < 2) return routes
        val noEntryFeatures = NoEntryRestrictionGuard.noEntryFeatures(hazardFeatures)
        val noEntryPrioritized = if (noEntryFeatures.isEmpty()) {
            routes
        } else {
            val scored = routes.map { route ->
                val routePoints = decodeNavigationRoutePoints(route)
                val noEntryConflicts = NoEntryRestrictionGuard.countRouteConflicts(
                    routePoints = routePoints,
                    noEntryFeatures = noEntryFeatures,
                    routeMatchDistanceMeters = noEntryRouteMatchDistanceMeters
                )
                route to noEntryConflicts
            }
            val best = scored.minByOrNull { it.second }?.first
            if (best == null) {
                routes
            } else {
                routes.filterNot { it == best }.toMutableList().also { it.add(0, best) }
            }
        }
        if (!lowStressModeEnabled && currentConfidenceScore >= lowConfidenceRouteThreshold) {
            return noEntryPrioritized
        }

        val scored = noEntryPrioritized.map { route ->
            val routeStress = routeIntelligenceEngine.evaluate(
                routeGeometry = decodeNavigationRoutePoints(route),
                hazards = hazardFeatures
            ).stressIndex
            route to routeStress
        }
        val lowestStressRoute = scored.minByOrNull { it.second }?.first ?: return routes
        val reorderedRoutes = noEntryPrioritized.filterNot { it == lowestStressRoute }.toMutableList()
        reorderedRoutes.add(0, lowestStressRoute)
        Log.d(
            TAG,
            "Low confidence routing applied. confidence=$currentConfidenceScore primaryStress=${scored.firstOrNull { it.first == lowestStressRoute }?.second}"
        )
        return reorderedRoutes
    }

    private fun updatePreviewSummaryFromRoutes(routes: List<NavigationRoute>) {
        if (routes.isEmpty()) {
            clearPreviewSummary()
            return
        }
        val primary = routes.first()
        val directionsRoute = primary.directionsRoute
        val distanceMeters = directionsRoute.distance() ?: 0.0
        val durationSeconds = directionsRoute.duration() ?: 0.0
        val etaTime = Date(System.currentTimeMillis() + (durationSeconds * 1000).toLong())
        val etaLabel = SimpleDateFormat("h:mm a", Locale.UK).format(etaTime).lowercase(Locale.UK)
        val routePoints = decodeNavigationRoutePoints(primary)
        val intelligence = routeIntelligenceEngine.evaluate(
            routeGeometry = routePoints,
            hazards = hazardFeatures
        )
        val difficultyLabel = formatDifficultyLabel(intelligence.difficultyLabel)

        binding.navPreviewSummaryBanner.isVisible = true
        binding.navPreviewDistanceValue.text = getString(
            R.string.preview_summary_distance,
            formatMiles(distanceMeters)
        )
        binding.navPreviewEtaValue.text = getString(R.string.preview_summary_eta, etaLabel)
        binding.navPreviewDifficultyValue.text = getString(
            R.string.preview_summary_difficulty,
            difficultyLabel,
            intelligence.stressIndex
        )
        binding.navPreviewDestinationValue.text = selectedDestinationName
            ?: getString(R.string.destination_marker_fallback)
    }

    private fun clearPreviewSummary() {
        binding.navPreviewSummaryBanner.isVisible = false
        binding.navPreviewDistanceValue.text = "-"
        binding.navPreviewEtaValue.text = "-"
        binding.navPreviewDifficultyValue.text = "-"
        binding.navPreviewDestinationValue.text = "-"
    }

    private fun formatMiles(distanceMeters: Double): String {
        return if (preferredUnitsSetting == PreferredUnitsSetting.METRIC_KMH) {
            val km = distanceMeters / 1000.0
            String.format(Locale.UK, "%.1f km", km)
        } else {
            val miles = distanceMeters / 1609.344
            String.format(Locale.UK, "%.1f mi", miles)
        }
    }

    private fun formatDifficultyLabel(label: RouteDifficultyLabel): String {
        return when (label) {
            RouteDifficultyLabel.EASY -> getString(R.string.route_difficulty_easy)
            RouteDifficultyLabel.MEDIUM -> getString(R.string.route_difficulty_medium)
            RouteDifficultyLabel.HARD -> getString(R.string.route_difficulty_hard)
        }
    }

    private fun resetCameraBearingToNorth() {
        val cameraState = binding.mapView.mapboxMap.cameraState
        val mapAnimationOptions = MapAnimationOptions.Builder().duration(700L).build()
        binding.mapView.camera.easeTo(
            CameraOptions.Builder()
                .center(cameraState.center)
                .zoom(cameraState.zoom)
                .pitch(cameraState.pitch)
                .bearing(0.0)
                .padding(cameraState.padding)
                .build(),
            mapAnimationOptions
        )
    }

    private fun handleOverviewButtonPressed() {
        val hasRoute = hasRouteForOverview()
        val nextMode = NavigationCameraModePolicy.onOverviewButtonPressed(
            currentMode = navigationCameraMode,
            hasRouteForOverview = hasRoute
        )
        if (!hasRoute && navigationCameraMode != NavigationCameraMode.OVERVIEW) {
            Toast.makeText(this, getString(R.string.no_route_for_overview), Toast.LENGTH_SHORT).show()
            return
        }
        setNavigationCameraMode(nextMode)
        if (nextMode == NavigationCameraMode.OVERVIEW) {
            showRouteOverview()
        } else {
            recenterFollowCamera(force = true)
        }
    }

    private fun hasRouteForOverview(): Boolean {
        return routeLineApi.getPrimaryNavigationRoute() != null ||
            mapboxNavigation.getNavigationRoutes().isNotEmpty()
    }

    private fun setNavigationCameraMode(mode: NavigationCameraMode) {
        if (mode == navigationCameraMode) return
        navigationCameraMode = mode
        binding.overviewButton.alpha = if (mode == NavigationCameraMode.OVERVIEW) 1.0f else 0.8f
    }

    private fun recenterFollowCamera(force: Boolean) {
        val center = latestEnhancedLocationPoint ?: selectedCentrePoint() ?: fallbackTestCentrePoint
        val bearing = if (latestSpeedMetersPerSecond < 1.5) null else binding.mapView.mapboxMap.cameraState.bearing
        updateCamera(
            point = center,
            bearing = bearing,
            speedMetersPerSecond = latestSpeedMetersPerSecond,
            force = force
        )
    }

    private fun showRouteOverview() {
        val route = routeLineApi.getPrimaryNavigationRoute()
            ?: mapboxNavigation.getNavigationRoutes().firstOrNull()
        val geometry = route?.directionsRoute?.geometry()

        if (geometry.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.no_route_for_overview), Toast.LENGTH_SHORT).show()
            return
        }

        val routePoints = runCatching { LineString.fromPolyline(geometry, 6).coordinates() }
            .recoverCatching { LineString.fromPolyline(geometry, 5).coordinates() }
            .getOrNull()

        if (routePoints.isNullOrEmpty()) {
            Toast.makeText(this, getString(R.string.no_route_for_overview), Toast.LENGTH_SHORT).show()
            return
        }

        val overviewCamera = binding.mapView.mapboxMap.cameraForCoordinates(
            routePoints,
            EdgeInsets(180.0, 80.0, 320.0, 80.0),
            null,
            null
        )
        binding.mapView.camera.easeTo(
            overviewCamera,
            MapAnimationOptions.Builder().duration(900L).build()
        )
    }

    private fun renderSpeedometer(speedInfo: SpeedInfoValue?) {
        if (!speedometerEnabledSetting) {
            lastSpeedThresholdExceeded = false
            binding.speedometerCard.visibility = View.GONE
            binding.speedLimitValue.isVisible = false
            return
        }

        if (speedInfo == null) {
            lastSpeedThresholdExceeded = false
            binding.speedometerCard.visibility =
                if (uiMode == UiMode.NAVIGATION && navSessionState == NavSessionState.PREVIEW) {
                    View.INVISIBLE
                } else {
                    View.GONE
                }
            return
        }

        val unitForConversion = speedInfo.postedSpeedUnit
        val displayedCurrentSpeed = convertSpeedForPreferredUnits(speedInfo.currentSpeed, unitForConversion)
        val mapboxPostedSpeed = speedInfo.postedSpeed?.let { posted ->
            val converted = convertSpeedForPreferredUnits(posted, unitForConversion)
            normalizeSpeedLimitForPreferredUnits(converted)
        }
        val schoolZoneOverrideLimit = currentSchoolZoneLimitForDisplay()
        val displayedPostedSpeed = when {
            schoolZoneOverrideLimit == null -> mapboxPostedSpeed
            mapboxPostedSpeed == null || mapboxPostedSpeed <= 0 -> schoolZoneOverrideLimit
            else -> min(mapboxPostedSpeed, schoolZoneOverrideLimit)
        }
        val thresholdDisplayDelta = speedingThresholdDeltaForDisplay()
        val thresholdSpeed = displayedPostedSpeed?.takeIf { it > 0 }?.plus(thresholdDisplayDelta)
        val isOverThreshold =
            thresholdSpeed != null && displayedCurrentSpeed >= thresholdSpeed
        val limitTextColorRes =
            if (isOverThreshold) R.color.speedometer_limit_text_alert else R.color.speedometer_limit_text_default
        val limitBackgroundRes =
            if (isOverThreshold) R.drawable.bg_speed_limit_sign_alert else R.drawable.bg_speed_limit_sign
        val speedometerBackgroundRes =
            if (isOverThreshold) R.drawable.bg_speedometer_waze_alert else R.drawable.bg_speedometer_waze

        binding.speedometerCard.isInvisible = false
        binding.speedometerCard.isVisible = true
        binding.currentSpeedValue.text = displayedCurrentSpeed.toString()
        binding.currentSpeedUnit.text = speedUnitLabel(speedInfo.postedSpeedUnit)
        binding.speedometerDialBackground.setBackgroundResource(speedometerBackgroundRes)

        val shouldShowSpeedLimit = when (speedLimitDisplaySetting) {
            SpeedLimitDisplaySetting.ALWAYS -> true
            SpeedLimitDisplaySetting.ONLY_WHEN_SPEEDING -> isOverThreshold
            SpeedLimitDisplaySetting.NEVER -> false
        }

        if (displayedPostedSpeed != null && displayedPostedSpeed > 0 && shouldShowSpeedLimit) {
            binding.speedLimitValue.isVisible = true
            binding.speedLimitValue.text = displayedPostedSpeed.toString()
            binding.speedLimitValue.setTextColor(ContextCompat.getColor(this, limitTextColorRes))
            binding.speedLimitValue.setBackgroundResource(limitBackgroundRes)
            binding.speedLimitValue.contentDescription =
                if (schoolZoneOverrideLimit != null) {
                    "School zone speed limit $displayedPostedSpeed ${speedUnitLabel(speedInfo.postedSpeedUnit)}"
                } else {
                    "Speed limit $displayedPostedSpeed ${speedUnitLabel(speedInfo.postedSpeedUnit)}"
                }
        } else {
            binding.speedLimitValue.isVisible = false
        }

        maybePlaySpeedThresholdAlert(isOverThreshold = isOverThreshold)
    }

    private fun speedingThresholdDeltaForDisplay(): Int {
        return when (speedingThresholdSetting) {
            SpeedingThresholdSetting.AT_LIMIT -> 0
            SpeedingThresholdSetting.PLUS_SMALL -> {
                if (preferredUnitsSetting == PreferredUnitsSetting.METRIC_KMH) 10 else 5
            }
            SpeedingThresholdSetting.PLUS_LARGE -> {
                if (preferredUnitsSetting == PreferredUnitsSetting.METRIC_KMH) 20 else 10
            }
        }
    }

    private fun maybePlaySpeedThresholdAlert(isOverThreshold: Boolean) {
        if (!speedAlertAtThresholdEnabledSetting) {
            lastSpeedThresholdExceeded = isOverThreshold
            return
        }
        if (!isOverThreshold) {
            lastSpeedThresholdExceeded = false
            return
        }
        val nowMs = System.currentTimeMillis()
        val crossedThreshold = !lastSpeedThresholdExceeded
        val cooldownElapsed = nowMs - lastSpeedThresholdAlertAtMs >= speedThresholdAlertCooldownMs
        if (crossedThreshold && cooldownElapsed) {
            speedThresholdToneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 180)
            lastSpeedThresholdAlertAtMs = nowMs
        }
        lastSpeedThresholdExceeded = true
    }

    private fun currentSchoolZoneLimitForDisplay(): Int? {
        if (hazardFeatures.isEmpty()) return null
        if (!binding.routeProgressBanner.isVisible) return null
        val locationPoint = latestEnhancedLocationPoint ?: return null

        val nearbySchoolZones = hazardFeatures
            .asSequence()
            .filter { feature -> feature.type == OsmFeatureType.SCHOOL_ZONE }
            .map { feature -> feature to distanceMeters(locationPoint, Point.fromLngLat(feature.lon, feature.lat)) }
            .filter { (_, distanceMeters) -> distanceMeters <= schoolZoneOverrideDistanceMeters }
            .toList()
        if (nearbySchoolZones.isEmpty()) return null

        val limitsFromTags = nearbySchoolZones.mapNotNull { (feature, _) ->
            parseSpeedLimitForDisplay(feature.tags["maxspeed"])
                ?: parseSpeedLimitForDisplay(feature.tags["maxspeed:advisory"])
                ?: parseSpeedLimitForDisplay(feature.tags["maxspeed:conditional"])
        }
        val defaultLimit = when (preferredUnitsSetting) {
            PreferredUnitsSetting.UK_MPH -> schoolZoneDefaultLimitMph
            PreferredUnitsSetting.METRIC_KMH -> (schoolZoneDefaultLimitMph * 1.609344).roundToInt()
        }
        val bestTaggedLimit = limitsFromTags.minOrNull()
        return bestTaggedLimit ?: defaultLimit
    }

    private fun parseSpeedLimitForDisplay(raw: String?): Int? {
        val value = raw?.trim()?.lowercase(Locale.US).orEmpty()
        if (value.isBlank()) return null
        val numeric = Regex("(\\d{1,3})").find(value)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return null
        val interpretedMph = when {
            value.contains("mph") -> numeric.toDouble()
            value.contains("km") -> numeric / 1.609344
            else -> numeric.toDouble() // UK default when unit is omitted
        }
        return when (preferredUnitsSetting) {
            PreferredUnitsSetting.UK_MPH -> SpeedLimitNormalizer.normalizeSpeedLimit(interpretedMph.roundToInt())
            PreferredUnitsSetting.METRIC_KMH -> (interpretedMph * 1.609344).roundToInt()
        }
    }

    private fun normalizeSpeedLimitForPreferredUnits(rawLimit: Int): Int {
        return when (preferredUnitsSetting) {
            PreferredUnitsSetting.UK_MPH -> SpeedLimitNormalizer.normalizeSpeedLimit(rawLimit)
            PreferredUnitsSetting.METRIC_KMH -> rawLimit
        }
    }

    private fun speedUnitLabel(speedUnit: SpeedUnit): String {
        if (preferredUnitsSetting == PreferredUnitsSetting.METRIC_KMH) {
            return "KM/H"
        }
        if (preferredUnitsSetting == PreferredUnitsSetting.UK_MPH) {
            return "MPH"
        }
        return when (speedUnit) {
            SpeedUnit.MILES_PER_HOUR -> "MPH"
            SpeedUnit.KILOMETERS_PER_HOUR -> "KM/H"
            SpeedUnit.METERS_PER_SECOND -> "M/S"
        }
    }

    private fun convertSpeedForPreferredUnits(value: Int, rawUnit: SpeedUnit): Int {
        val speedInMps = when (rawUnit) {
            SpeedUnit.MILES_PER_HOUR -> value * 0.44704
            SpeedUnit.KILOMETERS_PER_HOUR -> value / 3.6
            SpeedUnit.METERS_PER_SECOND -> value.toDouble()
        }
        return when (preferredUnitsSetting) {
            PreferredUnitsSetting.UK_MPH -> (speedInMps / 0.44704).roundToInt()
            PreferredUnitsSetting.METRIC_KMH -> (speedInMps * 3.6).roundToInt()
        }
    }

    private fun cycleVoiceGuidanceMode() {
        val next = when (voiceGuidanceMode) {
            VoiceGuidanceMode.FULL -> VoiceModeSetting.ALERTS
            VoiceGuidanceMode.ALERTS_ONLY -> VoiceModeSetting.MUTE
            VoiceGuidanceMode.MUTE -> VoiceModeSetting.ALL
        }
        val nextLabel = when (next) {
            VoiceModeSetting.ALL -> getString(R.string.voice_mode_all)
            VoiceModeSetting.ALERTS -> getString(R.string.voice_mode_alerts)
            VoiceModeSetting.MUTE -> getString(R.string.voice_mode_mute)
        }

        lifecycleScope.launch {
            settingsRepository.setVoiceMode(next)
        }
        Toast.makeText(this, nextLabel, Toast.LENGTH_SHORT).show()
    }

    private fun cycleAppearanceMode() {
        val next = AppearanceModeManager.next(appearanceModeSetting)
        lifecycleScope.launch {
            settingsRepository.setAppearanceMode(next)
        }
        val messageRes = when (next) {
            AppearanceModeSetting.AUTO -> R.string.appearance_mode_changed_auto
            AppearanceModeSetting.DAY -> R.string.appearance_mode_changed_day
            AppearanceModeSetting.NIGHT -> R.string.appearance_mode_changed_night
        }
        Toast.makeText(this, getString(messageRes), Toast.LENGTH_SHORT).show()
    }

    private fun renderAppearanceModeControl() {
        if (!::binding.isInitialized) return
        val iconRes = when (appearanceModeSetting) {
            AppearanceModeSetting.AUTO -> R.drawable.ic_appearance_auto
            AppearanceModeSetting.DAY -> R.drawable.ic_appearance_day
            AppearanceModeSetting.NIGHT -> R.drawable.ic_appearance_night
        }
        val tintRes = when (appearanceModeSetting) {
            AppearanceModeSetting.AUTO -> R.color.map_control_appearance_icon_auto
            AppearanceModeSetting.DAY -> R.color.map_control_appearance_icon_day
            AppearanceModeSetting.NIGHT -> R.color.map_control_appearance_icon_night
        }
        val contentDescriptionRes = when (appearanceModeSetting) {
            AppearanceModeSetting.AUTO -> R.string.appearance_mode_auto
            AppearanceModeSetting.DAY -> R.string.appearance_mode_day
            AppearanceModeSetting.NIGHT -> R.string.appearance_mode_night
        }
        binding.appearanceModeButton.setImageResource(iconRes)
        binding.appearanceModeButton.imageTintList =
            ContextCompat.getColorStateList(this, tintRes)
        binding.appearanceModeButton.contentDescription = getString(contentDescriptionRes)
    }

    private fun currentNavigationStyleUri(): String {
        val useNightStyle = AppearanceModeManager.isNightActive(this, appearanceModeSetting)
        return if (useNightStyle) {
            NavigationStyles.NAVIGATION_NIGHT_STYLE
        } else {
            NavigationStyles.NAVIGATION_DAY_STYLE
        }
    }

    private fun loadNavigationMapStyle(
        forceReload: Boolean = false,
        resetCamera: Boolean = false
    ) {
        if (!::binding.isInitialized) return
        val targetStyleUri = currentNavigationStyleUri()
        if (!forceReload && styleLoaded && lastAppliedNavigationStyleUri == targetStyleUri) {
            return
        }

        styleLoaded = false
        lastAppliedNavigationStyleUri = targetStyleUri
        binding.mapView.mapboxMap.loadStyle(targetStyleUri) {
            val desiredStyleUri = currentNavigationStyleUri()
            if (desiredStyleUri != targetStyleUri) {
                // Appearance mode changed while the style was loading; immediately apply the newer style.
                loadNavigationMapStyle(forceReload = true, resetCamera = false)
                return@loadStyle
            }
            styleLoaded = true
            lastRenderedRouteLineSignature = null
            destinationAnnotationManager?.deleteAll()
            hazardAnnotationManager?.deleteAll()
            roadMarkingAnnotationManager?.deleteAll()
            destinationAnnotationManager = binding.mapView.annotations.createPointAnnotationManager()
            hazardAnnotationManager = binding.mapView.annotations.createPointAnnotationManager()
            roadMarkingAnnotationManager = binding.mapView.annotations.createPointAnnotationManager()
            if (resetCamera) {
                binding.mapView.mapboxMap.setCamera(
                    CameraOptions.Builder()
                        .center(initialCameraCenter())
                        .zoom(12.5)
                        .build()
                )
            }
            updateTopOrnamentsPosition()
            renderDestinationMarker()
            renderHazardMarkers(hazardFeatures)
            rerenderCurrentRoutesOnLoadedStyle()
            if (
                uiMode == UiMode.NAVIGATION &&
                selectedDestinationPoint != null &&
                mainViewModel.uiState.value.sessionState == NavigationSessionManager.SessionState.BROWSE
            ) {
                if (::sessionManager.isInitialized) {
                    sessionManager.previewDestination(selectedDestinationPoint!!)
                } else {
                    pendingDestinationPreview = true
                }
            }
        }
    }

    private fun rerenderCurrentRoutesOnLoadedStyle() {
        if (!coreMapboxObserversRegistered) return
        val style = binding.mapView.mapboxMap.style ?: return
        val currentRoutes = mapboxNavigation.getNavigationRoutes()
        if (currentRoutes.isEmpty()) return
        lifecycleScope.launch {
            routeLineApi.setNavigationRoutes(
                newRoutes = currentRoutes,
                alternativeRoutesMetadata = mapboxNavigation.getAlternativeMetadataFor(currentRoutes)
            ).apply {
                routeLineView.renderRouteDrawData(style, this)
            }
        }
    }

    private fun renderVoiceGuidanceMode() {
        when (voiceGuidanceMode) {
            VoiceGuidanceMode.FULL -> {
                binding.voiceModeButton.setImageResource(R.drawable.ic_voice_all_waze)
                binding.voiceModeButton.imageTintList =
                    ContextCompat.getColorStateList(this, R.color.map_control_voice_icon_all)
            }
            VoiceGuidanceMode.ALERTS_ONLY -> {
                binding.voiceModeButton.setImageResource(R.drawable.ic_voice_alerts_waze)
                binding.voiceModeButton.imageTintList =
                    ContextCompat.getColorStateList(this, R.color.voice_chip_icon_alert)
            }
            VoiceGuidanceMode.MUTE -> {
                binding.voiceModeButton.setImageResource(R.drawable.ic_voice_mute_waze)
                binding.voiceModeButton.imageTintList =
                    ContextCompat.getColorStateList(this, R.color.voice_chip_icon_mute)
            }
        }
        binding.voiceModeButton.contentDescription = currentVoiceModeLabel()
    }

    private fun currentVoiceModeLabel(): String {
        return when (voiceGuidanceMode) {
            VoiceGuidanceMode.FULL -> getString(R.string.voice_mode_all)
            VoiceGuidanceMode.ALERTS_ONLY -> getString(R.string.voice_mode_alerts)
            VoiceGuidanceMode.MUTE -> getString(R.string.voice_mode_mute)
        }
    }

    private fun currentVoiceModeSetting(): VoiceModeSetting {
        return when (voiceGuidanceMode) {
            VoiceGuidanceMode.FULL -> VoiceModeSetting.ALL
            VoiceGuidanceMode.ALERTS_ONLY -> VoiceModeSetting.ALERTS
            VoiceGuidanceMode.MUTE -> VoiceModeSetting.MUTE
        }
    }

    private fun speakVoiceInstructions(
        voiceInstructions: VoiceInstructions,
        onStarted: (() -> Unit)? = null,
        onCompleted: (() -> Unit)? = null
    ) {
        syncVoicePipelineToCurrentLanguage()
        val announcementText = voiceInstructions.announcement().orEmpty().trim()
        if (announcementText.isEmpty()) {
            onCompleted?.invoke()
            return
        }

        val player = requireVoiceInstructionsPlayer()
        if (useOnDeviceTtsForVoice) {
            val onboardAnnouncement = SpeechAnnouncement.Builder(announcementText)
                .build()
            Log.d(
                TAG,
                "Voice guidance using on-device TTS locale=$voiceLanguageTag language=${voiceLanguageSetting.storageValue}"
            )
            onStarted?.invoke()
            player.play(onboardAnnouncement) {
                onCompleted?.invoke()
            }
            return
        }

        val speechApi = requireSpeechApi()
        speechApi.generate(voiceInstructions) { speechResult ->
            speechResult.fold(
                { speechError ->
                    onStarted?.invoke()
                    player.play(speechError.fallback) { spokenAnnouncement ->
                        speechApi.clean(spokenAnnouncement)
                        onCompleted?.invoke()
                    }
                },
                { speechValue ->
                    onStarted?.invoke()
                    player.play(speechValue.announcement) { spokenAnnouncement ->
                        speechApi.clean(spokenAnnouncement)
                        onCompleted?.invoke()
                    }
                }
            )
        }
    }

    private fun requireSpeechApi(): MapboxSpeechApi {
        syncVoicePipelineToCurrentLanguage()
        return speechApiInstance ?: MapboxSpeechApi(this, voiceLanguageTag).also {
            speechApiInstance = it
        }
    }

    private fun requireVoiceInstructionsPlayer(): MapboxVoiceInstructionsPlayer {
        syncVoicePipelineToCurrentLanguage()
        return voiceInstructionsPlayerInstance ?: MapboxVoiceInstructionsPlayer(this, voiceLanguageTag).also {
            voiceInstructionsPlayerInstance = it
        }
    }

    private fun cancelSpeechGeneration() {
        speechApiInstance?.cancel()
    }

    private fun clearVoicePlaybackQueue() {
        voiceInstructionsPlayerInstance?.clear()
    }

    private fun shutdownVoicePipeline() {
        voiceInstructionsPlayerInstance?.shutdown()
        voiceInstructionsPlayerInstance = null
        speechApiInstance = null
    }

    private fun syncVoicePipelineToCurrentLanguage() {
        val targetLanguage = currentAppLanguageSetting()
        val targetTag = mapboxVoiceLanguageTag(targetLanguage)
        val targetOnDeviceTts = shouldUseOnDeviceTtsOnly(targetLanguage)
        if (voiceLanguageSetting == targetLanguage &&
            voiceLanguageTag.equals(targetTag, ignoreCase = true) &&
            useOnDeviceTtsForVoice == targetOnDeviceTts
        ) {
            return
        }

        cancelSpeechGeneration()
        clearVoicePlaybackQueue()
        shutdownVoicePipeline()

        voiceLanguageSetting = targetLanguage
        voiceLanguageTag = targetTag
        useOnDeviceTtsForVoice = targetOnDeviceTts
        Log.d(
            TAG,
            "Voice pipeline updated locale=$voiceLanguageTag language=${targetLanguage.storageValue} mode=${if (useOnDeviceTtsForVoice) "on_device_tts" else "mapbox_speech_api"}"
        )
    }

    private fun currentAppLanguageSetting(): AppLanguageSetting {
        val appLocaleTag = AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag()
        val resourceLocaleTag = resources.configuration.locales[0]?.toLanguageTag()
        val fallbackLocaleTag = Locale.getDefault().toLanguageTag()
        val rawTag = appLocaleTag ?: resourceLocaleTag ?: fallbackLocaleTag
        return AppLanguageSetting.entries.firstOrNull { it.bcp47Tag.equals(rawTag, ignoreCase = true) }
            ?: AppLanguageSetting.entries.firstOrNull {
                Locale.forLanguageTag(it.bcp47Tag).language.equals(
                    Locale.forLanguageTag(rawTag).language,
                    ignoreCase = true
                )
            }
            ?: AppLanguageSetting.ENGLISH_UK
    }

    private fun mapboxVoiceLanguageTag(language: AppLanguageSetting): String {
        return when (language) {
            AppLanguageSetting.ENGLISH_UK -> "en-GB"
            AppLanguageSetting.FRENCH -> "fr-FR"
            AppLanguageSetting.GERMAN -> "de-DE"
            AppLanguageSetting.SPANISH -> "es-ES"
            AppLanguageSetting.ITALIAN -> "it-IT"
            AppLanguageSetting.DUTCH -> "nl-NL"
            AppLanguageSetting.PORTUGUESE_PORTUGAL -> "pt-PT"
            AppLanguageSetting.POLISH -> "pl-PL"
        }
    }

    private fun shouldUseOnDeviceTtsOnly(language: AppLanguageSetting): Boolean {
        return language != AppLanguageSetting.ENGLISH_UK
    }

    private fun playHazardPrompt(speechText: String) {
        if (currentVoiceModeSetting() == VoiceModeSetting.MUTE) {
            hazardVoiceController.onHazardSpeechCompleted()
            return
        }

        val voiceInstructions = VoiceInstructions.builder()
            .announcement(speechText)
            .distanceAlongGeometry(0.0)
            .build()
        speakVoiceInstructions(
            voiceInstructions = voiceInstructions,
            onStarted = { isHazardSpeechPlaying = true },
            onCompleted = {
                isHazardSpeechPlaying = false
                hazardVoiceController.onHazardSpeechCompleted()
            }
        )
    }

    private fun playSystemAnnouncement(speechText: String) {
        if (currentVoiceModeSetting() == VoiceModeSetting.MUTE) {
            return
        }
        val voiceInstructions = VoiceInstructions.builder()
            .announcement(speechText)
            .distanceAlongGeometry(0.0)
            .build()
        speakVoiceInstructions(voiceInstructions)
    }

    private fun stopHazardSpeech() {
        if (!isHazardSpeechPlaying) return
        clearVoicePlaybackQueue()
        isHazardSpeechPlaying = false
    }

    private fun shouldSpeakVoiceInstruction(voiceInstructions: VoiceInstructions): Boolean {
        return when (voiceGuidanceMode) {
            VoiceGuidanceMode.FULL -> true
            VoiceGuidanceMode.MUTE -> false
            VoiceGuidanceMode.ALERTS_ONLY -> {
                val distanceToManeuver = voiceInstructions.distanceAlongGeometry() ?: Double.MAX_VALUE
                val announcement = voiceInstructions.announcement().orEmpty().lowercase(Locale.getDefault())
                distanceToManeuver <= alertsOnlyDistanceMeters || isCriticalVoiceAnnouncement(announcement)
            }
        }
    }

    private fun isCriticalVoiceAnnouncement(announcement: String): Boolean {
        if (announcement.isBlank()) {
            return false
        }
        val criticalKeywords = listOf(
            "arrive",
            "destination",
            "roundabout",
            "exit",
            "u-turn",
            "merge",
            "keep",
            "ferry"
        )
        return criticalKeywords.any { keyword -> announcement.contains(keyword) }
    }

    private fun isDuplicateVoiceInstruction(voiceInstructions: VoiceInstructions): Boolean {
        val announcement = voiceInstructions.announcement()?.trim().orEmpty()
        if (announcement.isBlank()) {
            return false
        }

        val nowMs = System.currentTimeMillis()
        val isDuplicate = announcement == lastSpokenAnnouncement &&
            nowMs - lastSpokenAtMs < duplicateVoiceWindowMs
        if (!isDuplicate) {
            lastSpokenAnnouncement = announcement
            lastSpokenAtMs = nowMs
        }
        return isDuplicate
    }

    override fun onDestroy() {
        unregisterNotificationStopReceiver()
        lowStressPersistJob?.cancel()
        promptAutoHideJob?.cancel()
        hazardVoiceController.stopSpeaking()
        hazardVoiceController.clear()
        if (coreMapboxObserversRegistered) {
            unregisterCoreMapboxObserversIfNeeded(mapboxNavigation)
        }
        if (::sessionManager.isInitialized) {
            sessionManager.onDestroy()
        }
        destinationAnnotationManager?.deleteAll()
        hazardAnnotationManager?.deleteAll()
        roadMarkingAnnotationManager?.deleteAll()
        destinationAnnotationManager = null
        hazardAnnotationManager = null
        roadMarkingAnnotationManager = null
        lastHazardMarkerRefreshAtMs = 0L
        hazardMarkerBitmapCache.clear()
        roadMarkingBitmapCache.clear()
        promptBannerBitmapCache.clear()
        unavailablePromptBannerBitmap = null
        super.onDestroy()
        cancelSpeechGeneration()
        shutdownVoicePipeline()
        speedThresholdToneGenerator?.release()
        routeLineApi.cancel()
        routeLineView.cancel()
        maneuverApi.cancel()
        if (!shouldKeepForegroundNavigationServiceActive()) {
            syncNavigationForegroundServiceState()
        }
    }

    private fun hasLocationPermission(): Boolean {
        return LocationPrePromptDialog.hasLocationPermission(this)
    }

    private fun requestLocationPermission(onGranted: (() -> Unit)? = null) {
        pendingLocationGrantedAction = onGranted
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun startPrimaryRouteAction() {
        when (uiMode) {
            UiMode.PRACTICE -> generatePracticeRoutes()
            UiMode.NAVIGATION -> handleNavigationPrimaryAction()
        }
    }

    private fun initNavigation() {
        MapboxNavigationApp.setup(
            NavigationOptions.Builder(this)
                .routeAlternativesOptions(
                    RouteAlternativesOptions.Builder()
                        .intervalMillis(TimeUnit.MINUTES.toMillis(3))
                        .build()
                )
                .build()
        )

        binding.mapView.location.apply {
            locationPuck = LocationPuck2D(
                bearingImage = ImageHolder.from(
                    com.mapbox.navigation.ui.components.R.drawable.mapbox_navigation_puck_icon
                )
            )
            setLocationProvider(navigationLocationProvider)
            puckBearingEnabled = true
            enabled = true
        }

        updateTopOrnamentsPosition()
        if (replayEnabled) {
            replayOriginLocation()
        }
    }

    private fun generatePracticeRoutes() {
        lifecycleScope.launch {
            if (!requestSafetyAcknowledgementIfNeeded()) return@launch
            extraPromptsUnavailableShown = false
            isPracticeRouteLoading = true
            applyUiModeState()
            try {
                val configuredRoutes = runCatching {
                    practiceRouteStore.loadRoutesForCentre(selectedCentreId)
                }.getOrDefault(emptyList())
                maybeShowOfflineDataSourceBanner()

                if (configuredRoutes.isEmpty()) {
                    resetPracticeRunState()
                    binding.maneuverView.isVisible = false
                    binding.routeProgressBanner.isVisible = false
                    applyUiModeState()
                    updateTopOrnamentsPosition()
                    Toast.makeText(
                        this@MainActivity,
                        "No practice routes found for ${selectedCentreLabel()}.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                val selectedRoute = orderRoutesBySelection(configuredRoutes).firstOrNull()
                if (selectedRoute == null) {
                    resetPracticeRunState()
                    binding.maneuverView.isVisible = false
                    binding.routeProgressBanner.isVisible = false
                    applyUiModeState()
                    updateTopOrnamentsPosition()
                    Toast.makeText(
                        this@MainActivity,
                        "Could not load selected practice route.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                selectedPracticeRoute = selectedRoute
                val practiceNavigationRoute = requestRouteForPracticeRoute(selectedRoute)
                if (practiceNavigationRoute == null) {
                    resetPracticeRunState()
                    binding.maneuverView.isVisible = false
                    binding.routeProgressBanner.isVisible = false
                    applyUiModeState()
                    updateTopOrnamentsPosition()
                    Toast.makeText(
                        this@MainActivity,
                        "Could not generate ${selectedCentreLabel()} routes. Check token/network.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                val startPoint = Point.fromLngLat(selectedRoute.startLon, selectedRoute.startLat)
                val originPoint = awaitCurrentEnhancedLocationPoint()
                if (originPoint == null) {
                    resetPracticeRunState()
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.practice_waiting_for_gps),
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                selectedPracticeNavigationRoute = practiceNavigationRoute
                practiceStartPoint = startPoint
                practiceRouteExpectedDistanceM = practiceNavigationRoute.directionsRoute.distance()
                    ?: selectedRoute.distanceM
                practiceRouteCompletionArmed = false
                practiceRouteActivatedAtMs = 0L
                practiceToCentreWithinRadiusSinceMs = null
                practiceToCentreClosestDistanceM = Double.MAX_VALUE
                lastPracticeStartRerouteAtMs = 0L
                isReroutingToPracticeStart = false

                ensureSessionManager()
                sessionManager.stop()
                sessionManager.previewPracticeRoute(selectedRoute)

                val distanceToStartMeters = distanceMeters(originPoint, startPoint)
                val nowMs = System.currentTimeMillis()
                val arrivalEvaluation = PracticeFlowDecisions.evaluateArrival(
                    nowMs = nowMs,
                    distanceToStartMeters = distanceToStartMeters,
                    speedMps = latestSpeedMetersPerSecond,
                    withinRadiusSinceMs = practiceToCentreWithinRadiusSinceMs,
                    immediateRadiusMeters = practiceArrivalImmediateRadiusMeters,
                    dwellRadiusMeters = practiceArrivalDwellRadiusMeters,
                    dwellWindowMs = practiceArrivalDwellWindowMs,
                    maxArrivalSpeedMps = practiceArrivalMaxSpeedMps
                )
                practiceToCentreWithinRadiusSinceMs = arrivalEvaluation.withinRadiusSinceMs
                practiceToCentreClosestDistanceM = min(practiceToCentreClosestDistanceM, distanceToStartMeters)

                if (!arrivalEvaluation.arrived) {
                    val approachRoute = requestRouteForCoordinates(
                        coordinates = listOf(originPoint, startPoint),
                        alternatives = false
                    )
                    if (approachRoute == null) {
                        resetPracticeRunState()
                        binding.maneuverView.isVisible = false
                        binding.routeProgressBanner.isVisible = false
                        applyUiModeState()
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.practice_start_approach_failed),
                            Toast.LENGTH_LONG
                        ).show()
                        return@launch
                    }
                    mapboxNavigation.setNavigationRoutes(listOf(approachRoute))
                    if (replayEnabled) {
                        syncReplayToRoute(approachRoute)
                    }
                    practiceRunStage = PracticeRunStage.TO_CENTRE
                    setNavigationCameraMode(NavigationCameraModePolicy.onSessionStart())
                    sessionManager.start()
                    binding.maneuverView.isVisible = true
                    binding.routeProgressBanner.isVisible = true
                    updateTopOrnamentsPosition()
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.practice_driving_to_start),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    sessionManager.start()
                    beginPracticeRouteTransition()
                }
            } finally {
                isPracticeRouteLoading = false
                applyUiModeState()
            }
        }
    }

    private suspend fun awaitCurrentEnhancedLocationPoint(
        timeoutMs: Long = 12_000L,
        pollIntervalMs: Long = 300L
    ): Point? {
        val retries = (timeoutMs / pollIntervalMs).toInt().coerceAtLeast(1)
        repeat(retries) {
            latestEnhancedLocationPoint?.let { return it }
            delay(pollIntervalMs)
        }
        return latestEnhancedLocationPoint
    }

    private fun handlePracticeRunProgress(
        distanceRemainingMeters: Double,
        completionPercent: Int
    ) {
        if (uiMode != UiMode.PRACTICE) return
        when (practiceRunStage) {
            PracticeRunStage.IDLE -> Unit
            PracticeRunStage.TO_CENTRE -> {
                val startPoint = practiceStartPoint ?: return
                val currentPoint = latestEnhancedLocationPoint ?: return
                val distanceToStartMeters = distanceMeters(currentPoint, startPoint)
                practiceToCentreClosestDistanceM = min(practiceToCentreClosestDistanceM, distanceToStartMeters)
                val nowMs = System.currentTimeMillis()
                val arrivalEvaluation = PracticeFlowDecisions.evaluateArrival(
                    nowMs = nowMs,
                    distanceToStartMeters = distanceToStartMeters,
                    speedMps = latestSpeedMetersPerSecond,
                    withinRadiusSinceMs = practiceToCentreWithinRadiusSinceMs,
                    immediateRadiusMeters = practiceArrivalImmediateRadiusMeters,
                    dwellRadiusMeters = practiceArrivalDwellRadiusMeters,
                    dwellWindowMs = practiceArrivalDwellWindowMs,
                    maxArrivalSpeedMps = practiceArrivalMaxSpeedMps
                )
                practiceToCentreWithinRadiusSinceMs = arrivalEvaluation.withinRadiusSinceMs
                val shouldTransition = PracticeFlowDecisions.shouldTransitionToPracticeRoute(
                    arrived = arrivalEvaluation.arrived,
                    transitionInProgress = practiceRunStage == PracticeRunStage.AT_CENTRE_TRANSITION,
                    routeStartInProgress = isStartingSelectedPracticeRoute
                )
                if (shouldTransition) {
                    beginPracticeRouteTransition()
                    return
                }

                val shouldRerouteToStart = PracticeFlowDecisions.shouldRerouteToStart(
                    nowMs = nowMs,
                    lastRerouteAtMs = lastPracticeStartRerouteAtMs,
                    rerouteCooldownMs = practiceStartRerouteCooldownMs,
                    distanceToStartMeters = distanceToStartMeters,
                    closestDistanceSeenMeters = practiceToCentreClosestDistanceM,
                    missedDeltaMeters = practiceStartMissedDeltaMeters,
                    routeDistanceRemainingMeters = distanceRemainingMeters,
                    completionPercent = completionPercent,
                    routeArrivalDistanceMeters = practiceApproachArrivalMeters
                )
                if (shouldRerouteToStart) {
                    Log.d(
                        TAG,
                        "Practice start missed; rerouting. distanceToStart=${distanceToStartMeters.roundToInt()}m closest=${practiceToCentreClosestDistanceM.roundToInt()}m"
                    )
                    rerouteToPracticeStart(startPoint, currentPoint)
                }
            }
            PracticeRunStage.AT_CENTRE_TRANSITION -> Unit
            PracticeRunStage.PRACTICE_ACTIVE -> {
                if (!practiceRouteCompletionArmed) {
                    val armDistance = practiceCompletionArmDistance()
                    if (distanceRemainingMeters >= armDistance) {
                        practiceRouteCompletionArmed = true
                        Log.d(
                            TAG,
                            "Practice route completion armed. distanceRemaining=$distanceRemainingMeters armDistance=$armDistance"
                        )
                    } else {
                        val elapsedSinceActivation = System.currentTimeMillis() - practiceRouteActivatedAtMs
                        if (elapsedSinceActivation < practiceRouteActivationGraceMs) {
                            return
                        }
                        Log.d(
                            TAG,
                            "Ignoring completion check until selected route fully loads. distanceRemaining=$distanceRemainingMeters"
                        )
                        return
                    }
                }
                val finishedByProgress = completionPercent >= practiceRouteFinishPercent
                val finishedByDistance = distanceRemainingMeters <= practiceRouteFinishMeters
                if (finishedByProgress || finishedByDistance) {
                    completePracticeRun()
                }
            }
            PracticeRunStage.PRACTICE_COMPLETED -> Unit
        }
    }

    private fun beginPracticeRouteTransition() {
        if (
            practiceRunStage == PracticeRunStage.AT_CENTRE_TRANSITION ||
            practiceRunStage == PracticeRunStage.PRACTICE_ACTIVE ||
            isStartingSelectedPracticeRoute
        ) {
            return
        }

        practiceRunStage = PracticeRunStage.AT_CENTRE_TRANSITION
        isReroutingToPracticeStart = false
        practiceToCentreWithinRadiusSinceMs = null

        val fullPracticeDistanceMeters = practiceRouteExpectedDistanceM.coerceAtLeast(0.0)
        latestRouteCompletionPercent = 0
        latestRouteDistanceRemainingM = fullPracticeDistanceMeters
        binding.routeCompletedValue.text = "0%"
        binding.routeProgressBar.progress = 0
        if (fullPracticeDistanceMeters > 0.0) {
            binding.routeDistanceLeftValue.text = formatDistanceForBanner(fullPracticeDistanceMeters)
        }

        val transitionMessage = getString(R.string.practice_starting_route_now)
        Log.d(
            TAG,
            "Practice state transition: TO_CENTRE -> AT_CENTRE_TRANSITION distanceM=${fullPracticeDistanceMeters.roundToInt()}"
        )
        showSystemPromptBanner(transitionMessage)
        playSystemAnnouncement(transitionMessage)
        ensureSessionManager()
        sessionManager.start()
        applyUiModeState()
        startSelectedPracticeRoute()
    }

    private fun rerouteToPracticeStart(startPoint: Point, currentPoint: Point) {
        if (isReroutingToPracticeStart) {
            return
        }
        isReroutingToPracticeStart = true
        lastPracticeStartRerouteAtMs = System.currentTimeMillis()

        lifecycleScope.launch {
            try {
                val reroute = requestRouteForCoordinates(
                    coordinates = listOf(currentPoint, startPoint),
                    alternatives = false
                )
                if (reroute == null) {
                    Log.w(TAG, "Practice start reroute failed; keeping current route to centre.")
                    return@launch
                }
                mapboxNavigation.setNavigationRoutes(listOf(reroute))
                if (replayEnabled) {
                    syncReplayToRoute(reroute)
                }
                ensureSessionManager()
                sessionManager.start()
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.practice_rerouting_to_start),
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                isReroutingToPracticeStart = false
            }
        }
    }

    private fun evaluatePracticeOffRoute(nowMs: Long) {
        if (uiMode != UiMode.PRACTICE) {
            PracticeOffRouteDebugStore.update(null, null, "INACTIVE")
            return
        }
        if (practiceRunStage != PracticeRunStage.PRACTICE_ACTIVE) {
            val stateLabel = when (practiceRunStage) {
                PracticeRunStage.IDLE -> "INACTIVE"
                PracticeRunStage.TO_CENTRE -> "TO_CENTRE"
                PracticeRunStage.AT_CENTRE_TRANSITION -> "AT_CENTRE_TRANSITION"
                PracticeRunStage.PRACTICE_ACTIVE -> PracticeOffRouteState.ON_ROUTE.name
                PracticeRunStage.PRACTICE_COMPLETED -> "PRACTICE_COMPLETED"
            }
            PracticeOffRouteDebugStore.update(null, null, stateLabel)
            return
        }

        val currentPoint = latestEnhancedLocationPoint ?: return
        val routePolyline = activePracticeRoutePolyline
        if (routePolyline.size < 2) {
            PracticeOffRouteDebugStore.update(null, null, "NO_ROUTE_GEOMETRY")
            return
        }

        val rawDistanceMeters = PolylineDistance.minimumDistanceMeters(currentPoint, routePolyline)
        val smoothedDistanceMeters = practiceOffRouteMedian.add(rawDistanceMeters)

        val elapsedSinceRouteStartMs = nowMs - practiceRouteActivatedAtMs
        if (elapsedSinceRouteStartMs < practiceOffRouteIgnoreInitialMs) {
            practiceOffRouteAboveThresholdSinceMs = 0L
            practiceOffRouteBelowThresholdSinceMs = 0L
            PracticeOffRouteDebugStore.update(
                rawDistanceM = rawDistanceMeters,
                smoothedDistanceM = smoothedDistanceMeters,
                offRouteState = "GRACE_PERIOD"
            )
            return
        }

        if (latestGpsAccuracyMeters > practiceOffRouteMaxGpsAccuracyMeters) {
            practiceOffRouteAboveThresholdSinceMs = 0L
            practiceOffRouteBelowThresholdSinceMs = 0L
            PracticeOffRouteDebugStore.update(
                rawDistanceM = rawDistanceMeters,
                smoothedDistanceM = smoothedDistanceMeters,
                offRouteState = "GPS_LOW_QUALITY"
            )
            return
        }

        when (practiceOffRouteState) {
            PracticeOffRouteState.ON_ROUTE -> {
                if (smoothedDistanceMeters > practiceOffRouteEnterDistanceMeters) {
                    if (practiceOffRouteAboveThresholdSinceMs == 0L) {
                        practiceOffRouteAboveThresholdSinceMs = nowMs
                    }
                    val aboveThresholdForMs = nowMs - practiceOffRouteAboveThresholdSinceMs
                    if (aboveThresholdForMs >= practiceOffRouteEnterDurationMs) {
                        practiceOffRouteState = PracticeOffRouteState.OFF_ROUTE
                        currentSessionOffRouteEvents += 1
                        emitOffRouteTelemetry(
                            eventType = "off_route_enter",
                            rawDistanceMeters = rawDistanceMeters,
                            smoothedDistanceMeters = smoothedDistanceMeters
                        )
                        practiceOffRouteAboveThresholdSinceMs = 0L
                        practiceOffRouteBelowThresholdSinceMs = 0L
                        Log.w(
                            TAG,
                            "Practice OFF_ROUTE entered. raw=${rawDistanceMeters.roundToInt()}m smoothed=${smoothedDistanceMeters.roundToInt()}m"
                        )
                    }
                } else {
                    practiceOffRouteAboveThresholdSinceMs = 0L
                }
            }
            PracticeOffRouteState.OFF_ROUTE -> {
                if (smoothedDistanceMeters < practiceOffRouteExitDistanceMeters) {
                    if (practiceOffRouteBelowThresholdSinceMs == 0L) {
                        practiceOffRouteBelowThresholdSinceMs = nowMs
                    }
                    val belowThresholdForMs = nowMs - practiceOffRouteBelowThresholdSinceMs
                    if (belowThresholdForMs >= practiceOffRouteExitDurationMs) {
                        practiceOffRouteState = PracticeOffRouteState.ON_ROUTE
                        emitOffRouteTelemetry(
                            eventType = "off_route_exit",
                            rawDistanceMeters = rawDistanceMeters,
                            smoothedDistanceMeters = smoothedDistanceMeters
                        )
                        practiceOffRouteBelowThresholdSinceMs = 0L
                        practiceOffRouteAboveThresholdSinceMs = 0L
                        Log.d(
                            TAG,
                            "Practice OFF_ROUTE cleared. raw=${rawDistanceMeters.roundToInt()}m smoothed=${smoothedDistanceMeters.roundToInt()}m"
                        )
                    }
                } else {
                    practiceOffRouteBelowThresholdSinceMs = 0L
                }
            }
        }

        PracticeOffRouteDebugStore.update(
            rawDistanceM = rawDistanceMeters,
            smoothedDistanceM = smoothedDistanceMeters,
            offRouteState = practiceOffRouteState.name
        )
    }

    private fun resetPracticeOffRouteTracking() {
        practiceOffRouteMedian.reset()
        practiceOffRouteState = PracticeOffRouteState.ON_ROUTE
        practiceOffRouteAboveThresholdSinceMs = 0L
        practiceOffRouteBelowThresholdSinceMs = 0L
        activePracticeRoutePolyline = emptyList()
        PracticeOffRouteDebugStore.reset()
    }

    private fun startSelectedPracticeRoute() {
        if (practiceRunStage == PracticeRunStage.PRACTICE_ACTIVE || isStartingSelectedPracticeRoute) {
            return
        }
        val routeDefinition = selectedPracticeRoute ?: return
        isStartingSelectedPracticeRoute = true

        lifecycleScope.launch {
            try {
                val selectedRoute = selectedPracticeNavigationRoute
                    ?.takeIf { isPracticeRouteDistanceValid(routeDefinition, it) }
                    ?: requestRouteForPracticeRoute(routeDefinition)

                if (selectedRoute == null) {
                    resetPracticeRunState()
                    if (::sessionManager.isInitialized) {
                        sessionManager.stop()
                    }
                    binding.maneuverView.isVisible = false
                    binding.routeProgressBanner.isVisible = false
                    applyUiModeState()
                    Toast.makeText(
                        this@MainActivity,
                        "Could not start ${selectedCentreLabel()} practice route.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                selectedPracticeNavigationRoute = selectedRoute
                practiceRouteExpectedDistanceM = selectedRoute.directionsRoute.distance()
                    ?: routeDefinition.distanceM
                practiceRouteActivatedAtMs = System.currentTimeMillis()
                practiceRouteCompletionArmed = false
                practiceRunStage = PracticeRunStage.PRACTICE_ACTIVE
                val practiceRoutePoints = routeDefinition.geometry.map { point ->
                    Point.fromLngLat(point.lon, point.lat)
                }
                val practiceIntelligence = routeIntelligenceEngine.evaluate(
                    routeGeometry = practiceRoutePoints,
                    hazards = hazardFeatures
                )
                resetPracticeOffRouteTracking()
                activePracticeRoutePolyline = decodeNavigationRoutePoints(selectedRoute)
                if (activePracticeRoutePolyline.isEmpty()) {
                    activePracticeRoutePolyline = practiceRoutePoints
                }
                PracticeOffRouteDebugStore.update(
                    rawDistanceM = null,
                    smoothedDistanceM = null,
                    offRouteState = PracticeOffRouteState.ON_ROUTE.name
                )

                mapboxNavigation.setNavigationRoutes(listOf(selectedRoute))
                if (replayEnabled) {
                    syncReplayToRoute(selectedRoute)
                }
                setNavigationCameraMode(NavigationCameraModePolicy.onSessionStart())
                binding.routeCompletedValue.text = "0%"
                binding.routeProgressBar.progress = 0
                binding.routeDistanceLeftValue.text = formatDistanceForBanner(practiceRouteExpectedDistanceM)
                latestRouteDistanceRemainingM = practiceRouteExpectedDistanceM.coerceAtLeast(0.0)
                latestRouteCompletionPercent = 0
                startSessionSummaryTracking(
                    mode = UiMode.PRACTICE,
                    routeId = routeDefinition.id,
                    initialDistanceM = practiceRouteExpectedDistanceM,
                    intelligence = practiceIntelligence
                )
                binding.maneuverView.isVisible = true
                binding.routeProgressBanner.isVisible = true
                updateTopOrnamentsPosition()
                applyUiModeState()
                Log.d(
                    TAG,
                    "Selected practice route started. distanceM=${practiceRouteExpectedDistanceM.roundToInt()} routeId=${routeDefinition.id}"
                )
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.practice_start_selected_route),
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                isStartingSelectedPracticeRoute = false
            }
        }
    }

    private fun completePracticeRun() {
        if (practiceRunStage != PracticeRunStage.PRACTICE_ACTIVE) return
        submitSessionSummary(completed = true, reason = "completed")
        resetPracticeRunState(PracticeRunStage.PRACTICE_COMPLETED)
        if (::sessionManager.isInitialized) {
            sessionManager.stop()
        }
        binding.maneuverView.isVisible = false
        binding.routeProgressBanner.isVisible = false
        applyUiModeState()
        Toast.makeText(
            this,
            getString(R.string.practice_route_complete),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun resetPracticeRunState(targetStage: PracticeRunStage = PracticeRunStage.IDLE) {
        practiceRunStage = targetStage
        setNavigationCameraMode(NavigationCameraMode.FOLLOW)
        selectedPracticeRoute = null
        selectedPracticeNavigationRoute = null
        practiceStartPoint = null
        practiceRouteActivatedAtMs = 0L
        practiceRouteExpectedDistanceM = 0.0
        practiceRouteCompletionArmed = false
        isStartingSelectedPracticeRoute = false
        isReroutingToPracticeStart = false
        practiceToCentreWithinRadiusSinceMs = null
        practiceToCentreClosestDistanceM = Double.MAX_VALUE
        lastPracticeStartRerouteAtMs = 0L
        extraPromptsUnavailableShown = false
        resetPracticeOffRouteTracking()
        clearSessionSummaryTracking()
        mainViewModel.setActivePrompt(null)
        hazardVoiceController.stopSpeaking()
        hazardVoiceController.clear()
    }

    private fun startSessionSummaryTracking(
        mode: UiMode,
        routeId: String?,
        initialDistanceM: Double,
        intelligence: RouteIntelligenceSummary?
    ) {
        activeSessionStartedAtMs = System.currentTimeMillis()
        activeSessionInitialDistanceM = initialDistanceM.coerceAtLeast(0.0)
        activeSessionRouteId = routeId
        activeSessionMode = mode
        activeSessionIntelligence = intelligence
        currentSessionOffRouteEvents = 0
        latestRouteCompletionPercent = 0
        latestRouteDistanceRemainingM = activeSessionInitialDistanceM
    }

    private fun submitSessionSummary(
        completed: Boolean,
        reason: String
    ) {
        val mode = activeSessionMode ?: return
        val startedAtMs = activeSessionStartedAtMs
        if (startedAtMs <= 0L) return

        val durationSeconds = ((System.currentTimeMillis() - startedAtMs) / 1000L)
            .coerceAtLeast(0L)
            .toInt()
        val remainingDistanceM = latestRouteDistanceRemainingM.coerceAtLeast(0.0)
        val drivenDistanceM = if (activeSessionInitialDistanceM > 0.0) {
            (activeSessionInitialDistanceM - remainingDistanceM).coerceAtLeast(0.0)
        } else {
            0.0
        }
        val routeId = activeSessionRouteId
        val intelligence = activeSessionIntelligence
        val offRouteEvents = currentSessionOffRouteEvents
        val modeStorageValue = when (mode) {
            UiMode.PRACTICE -> AppFlow.MODE_PRACTICE
            UiMode.NAVIGATION -> AppFlow.MODE_NAV
        }

        val summaryPayload = SessionSummaryPayload(
            centreId = selectedCentreId,
            routeId = routeId,
            stressIndex = intelligence?.stressIndex ?: 0,
            complexityScore = intelligence?.complexityScore ?: 0,
            confidenceScore = currentConfidenceScore,
            offRouteCount = offRouteEvents,
            completionFlag = completed,
            durationSeconds = durationSeconds,
            distanceMetersDriven = drivenDistanceM.roundToInt(),
            roundaboutCount = intelligence?.roundaboutCount ?: 0,
            trafficSignalCount = intelligence?.trafficSignalCount ?: 0,
            zebraCount = intelligence?.zebraCount ?: 0,
            schoolCount = intelligence?.schoolCount ?: 0,
            busLaneCount = intelligence?.busLaneCount ?: 0
        )
        val dismissRate = if (promptShownCount > 0) {
            (promptReplacedQuicklyCount.toFloat() / promptShownCount.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        clearSessionSummaryTracking()

        lifecycleScope.launch {
            val stressIndex = intelligence?.stressIndex ?: 0
            driverProfileRepository.recordSession(
                distanceMetersDriven = drivenDistanceM.roundToInt(),
                stressIndex = stressIndex,
                offRouteEvents = offRouteEvents,
                isPracticeCompletion = mode == UiMode.PRACTICE && completed
            )
            if (mode == UiMode.PRACTICE && completed) {
                maybeShowPracticalPassPrompt()
            }
            if (TheoryFeatureFlags.isTheoryModuleEnabled()) {
                val routeTags = MapRouteTagsToTheoryTopics.inferTagsFromSessionSummary(summaryPayload)
                if (routeTags.isNotEmpty()) {
                    theoryProgressStore.recordLastRouteTagSnapshot(
                        tags = routeTags,
                        centreId = selectedCentreId,
                        routeId = routeId
                    )
                }
            }
            telemetryRepository.sendSessionSummary(
                SessionSummaryTelemetry(
                    mode = modeStorageValue,
                    centreId = selectedCentreId,
                    routeId = routeId,
                    completed = completed,
                    durationSeconds = durationSeconds,
                    distanceMetersDriven = drivenDistanceM.roundToInt(),
                    offRouteCount = offRouteEvents,
                    averageStressIndex = stressIndex,
                    averageConfidenceScore = currentConfidenceScore,
                    intelligenceSummary = intelligence,
                    organisationCode = currentOrganisationCode.takeIf { it.isNotBlank() },
                    centresPackVersion = packStore.readPackVersion(PackType.CENTRES, "all"),
                    routesPackVersion = packStore.readPackVersion(PackType.ROUTES, selectedCentreId),
                    hazardsPackVersion = packStore.readPackVersion(PackType.HAZARDS, selectedCentreId)
                )
            )
            settingsRepository.autoAdjustPromptSensitivity(
                confidenceScore = currentConfidenceScore,
                dismissRate = dismissRate
            )
            if (currentInstructorModeEnabled) {
                telemetryRepository.sendEvent(
                    TelemetryEvent.InstructorSession(
                        summary = summaryPayload,
                        organisationCode = currentOrganisationCode.takeIf { it.isNotBlank() }
                    )
                )
            }
        }
        if (completed) {
            lifecycleScope.launch {
                val hasCoachingAccess = featureAccessManager.hasTrainingPlanAccess().first()
                if (hasCoachingAccess) {
                    openSessionReport(summaryPayload)
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.session_report_locked),
                        Toast.LENGTH_SHORT
                    ).show()
                    openPaywall(getString(R.string.paywall_feature_coaching_report))
                }
            }
        }
        Log.d(
            TAG,
            "session_summary queued mode=$modeStorageValue completed=$completed reason=$reason durationS=$durationSeconds distanceM=${drivenDistanceM.roundToInt()} routeId=$routeId"
        )
    }

    private fun openSessionReport(payload: SessionSummaryPayload) {
        startActivity(
            Intent(this, SessionReportActivity::class.java).apply {
                putExtra(SessionReportActivity.EXTRA_CENTRE_ID, payload.centreId)
                putExtra(SessionReportActivity.EXTRA_ROUTE_ID, payload.routeId)
                putExtra(SessionReportActivity.EXTRA_STRESS_INDEX, payload.stressIndex)
                putExtra(SessionReportActivity.EXTRA_COMPLEXITY_SCORE, payload.complexityScore)
                putExtra(SessionReportActivity.EXTRA_CONFIDENCE_SCORE, payload.confidenceScore)
                putExtra(SessionReportActivity.EXTRA_OFF_ROUTE_COUNT, payload.offRouteCount)
                putExtra(SessionReportActivity.EXTRA_COMPLETION_FLAG, payload.completionFlag)
                putExtra(SessionReportActivity.EXTRA_DURATION_SECONDS, payload.durationSeconds)
                putExtra(SessionReportActivity.EXTRA_DISTANCE_METERS, payload.distanceMetersDriven)
                putExtra(SessionReportActivity.EXTRA_ROUNDABOUT_COUNT, payload.roundaboutCount)
                putExtra(SessionReportActivity.EXTRA_TRAFFIC_SIGNAL_COUNT, payload.trafficSignalCount)
                putExtra(SessionReportActivity.EXTRA_ZEBRA_COUNT, payload.zebraCount)
                putExtra(SessionReportActivity.EXTRA_SCHOOL_COUNT, payload.schoolCount)
                putExtra(SessionReportActivity.EXTRA_BUS_LANE_COUNT, payload.busLaneCount)
            }
        )

        val exportFile = sessionSummaryExporter.export(payload)
        Log.d(TAG, "Session summary exported to ${exportFile.absolutePath}")
    }

    private fun openPaywall(featureLabel: String) {
        startActivity(
            Intent(this, PaywallActivity::class.java).putExtra(
                PaywallActivity.EXTRA_FEATURE_LABEL,
                featureLabel
            )
        )
    }

    private fun clearSessionSummaryTracking() {
        activeSessionStartedAtMs = 0L
        activeSessionInitialDistanceM = 0.0
        activeSessionRouteId = null
        activeSessionMode = null
        activeSessionIntelligence = null
        currentSessionOffRouteEvents = 0
        latestRouteDistanceRemainingM = 0.0
        latestRouteCompletionPercent = 0
        promptShownCount = 0
        promptReplacedQuicklyCount = 0
        lastPromptShownAtMs = 0L
        lastPromptTelemetryType = null
        currentActiveRouteStressIndex = null
        currentActiveRouteDifficultyLabel = null
        renderActiveRouteStressBanner()
    }

    private data class SettingsTuple(
        val voiceMode: VoiceModeSetting,
        val units: PreferredUnitsSetting,
        val hazardsEnabled: Boolean,
        val lowStressMode: Boolean,
        val appearanceMode: AppearanceModeSetting,
        val speedometerEnabled: Boolean = true,
        val speedLimitDisplay: SpeedLimitDisplaySetting = SpeedLimitDisplaySetting.ALWAYS,
        val speedingThreshold: SpeedingThresholdSetting = SpeedingThresholdSetting.AT_LIMIT,
        val speedAlertAtThresholdEnabled: Boolean = true
    )

    private data class NoEntryScoredRoute(
        val route: NavigationRoute,
        val conflictCount: Int,
        val distanceMeters: Double
    )

    private data class RouteStressEvaluation(
        val route: NavigationRoute,
        val summary: RouteIntelligenceSummary,
        val stressIndex: Int,
        val difficultyLabel: RouteDifficultyLabel,
        val etaSeconds: Double,
        val distanceMeters: Double
    )

    private fun orderRoutesBySelection(routes: List<PracticeRoute>): List<PracticeRoute> {
        val preferredRouteId = selectedRouteId ?: return routes
        val preferredRoute = routes.firstOrNull { it.id == preferredRouteId } ?: return routes
        return buildList {
            add(preferredRoute)
            addAll(routes.filterNot { it.id == preferredRouteId })
        }
    }

    private suspend fun requestRouteForPracticeRoute(route: PracticeRoute): NavigationRoute? {
        val rawPoints = route.geometry.map { point ->
            Point.fromLngLat(point.lon, point.lat)
        }
        val points = normalizePracticeRouteCoordinates(rawPoints)
        val silentWaypointRoute = requestRouteForCoordinates(
            coordinates = points,
            alternatives = false,
            waypointIndices = buildWaypointIndices(points.size)
        )
        if (silentWaypointRoute != null && isPracticeRouteDistanceValid(route, silentWaypointRoute)) {
            return silentWaypointRoute
        }
        if (silentWaypointRoute != null) {
            Log.w(
                TAG,
                "Practice route looked too short with silent waypoints. Retrying via explicit waypoints."
            )
        }

        val fallbackRoute = requestRouteForCoordinates(
            coordinates = points,
            alternatives = false,
            waypointIndices = null
        )
        if (fallbackRoute != null && isPracticeRouteDistanceValid(route, fallbackRoute)) {
            return fallbackRoute
        }

        return null
    }

    private suspend fun requestRouteForCoordinates(
        coordinates: List<Point>,
        alternatives: Boolean = false,
        waypointIndices: List<Int>? = null
    ): NavigationRoute? {
        return requestRoutesForCoordinates(
            coordinates = coordinates,
            alternatives = alternatives,
            waypointIndices = waypointIndices
        ).firstOrNull()
    }

    private suspend fun requestRoutesForCoordinates(
        coordinates: List<Point>,
        alternatives: Boolean = false,
        waypointIndices: List<Int>? = null
    ): List<NavigationRoute> {
        val routeOptionsBuilder = RouteOptions.builder()
            .applyDefaultNavigationOptions()
            .applyLanguageAndVoiceUnitOptions(this)
            .coordinatesList(coordinates)
            .layersList(buildLayersList(coordinates.size))
            .alternatives(alternatives)
        if (!waypointIndices.isNullOrEmpty()) {
            routeOptionsBuilder.waypointIndicesList(waypointIndices)
        }
        val routeOptions = routeOptionsBuilder.build()

        return suspendCancellableCoroutine { continuation ->
            mapboxNavigation.requestRoutes(
                routeOptions,
                object : NavigationRouterCallback {
                    override fun onRoutesReady(
                        routes: List<NavigationRoute>,
                        @RouterOrigin routerOrigin: String
                    ) {
                        val firstRoute = routes.firstOrNull()?.directionsRoute
                        Log.d(
                            TAG,
                            "Routes ready: count=${routes.size} distanceM=${firstRoute?.distance()} durationS=${firstRoute?.duration()}"
                        )
                        if (continuation.isActive) {
                            continuation.resume(routes)
                        }
                    }

                    override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
                        if (continuation.isActive) {
                            continuation.resume(emptyList())
                        }
                    }

                    override fun onCanceled(
                        routeOptions: RouteOptions,
                        @RouterOrigin routerOrigin: String
                    ) {
                        if (continuation.isActive) {
                            continuation.resume(emptyList())
                        }
                    }
                }
            )
        }
    }

    private fun buildLayersList(pointCount: Int): List<Int?> {
        val list = MutableList<Int?>(pointCount) { null }
        list[0] = mapboxNavigation.getZLevel()
        return list
    }

    private fun buildWaypointIndices(pointCount: Int): List<Int>? {
        if (pointCount < 2) return null
        return listOf(0, pointCount - 1)
    }

    private fun decodeNavigationRoutePoints(navigationRoute: NavigationRoute): List<Point> {
        val geometry = navigationRoute.directionsRoute.geometry().orEmpty()
        if (geometry.isBlank()) return emptyList()
        return runCatching {
            LineString.fromPolyline(geometry, 6).coordinates()
        }.recoverCatching {
            LineString.fromPolyline(geometry, 5).coordinates()
        }.getOrDefault(emptyList())
    }

    private fun shouldRenderRouteLine(routes: List<NavigationRoute>): Boolean {
        val signature = buildRouteLineSignature(routes)
        if (signature == lastRenderedRouteLineSignature) {
            return false
        }
        lastRenderedRouteLineSignature = signature
        return true
    }

    private fun buildRouteLineSignature(routes: List<NavigationRoute>): String {
        if (routes.isEmpty()) return "empty"
        return routes.joinToString(separator = "|") { route ->
            val directionsRoute = route.directionsRoute
            val geometryHash = directionsRoute.geometry()?.hashCode() ?: 0
            val distanceMeters = directionsRoute.distance()?.roundToInt() ?: -1
            val durationSeconds = directionsRoute.duration()?.roundToInt() ?: -1
            "$geometryHash:$distanceMeters:$durationSeconds"
        }
    }

    private fun isPracticeRouteDistanceValid(
        routeDefinition: PracticeRoute,
        navigationRoute: NavigationRoute
    ): Boolean {
        val routeDistance = navigationRoute.directionsRoute.distance() ?: 0.0
        val expectedDistance = routeDefinition.distanceM.takeIf { it > 0.0 } ?: routeDistance
        val minimumAcceptableDistance =
            max(practiceRouteMinValidDistanceMeters, expectedDistance * practiceRouteDistanceValidityRatio)
        val stepCount = navigationRoute.directionsRoute.legs().orEmpty().sumOf { leg ->
            leg.steps()?.size ?: 0
        }
        val isValid = routeDistance >= minimumAcceptableDistance && stepCount >= 2
        if (!isValid) {
            Log.w(
                TAG,
                "Rejecting short practice route. actualDistance=$routeDistance expectedDistance=$expectedDistance minAllowed=$minimumAcceptableDistance steps=$stepCount routeId=${routeDefinition.id}"
            )
        }
        return isValid
    }

    private fun practiceCompletionArmDistance(): Double {
        val expectedDistance = practiceRouteExpectedDistanceM
        if (expectedDistance <= 0.0) {
            return practiceRouteMinDistanceArmMeters
        }
        return max(
            practiceRouteMinDistanceArmMeters,
            min(350.0, expectedDistance * 0.06)
        )
    }

    private fun formatDistanceForBanner(distanceMeters: Double): String {
        val safeDistanceMeters = distanceMeters.coerceAtLeast(0.0)
        return when (preferredUnitsSetting) {
            PreferredUnitsSetting.UK_MPH -> {
                val miles = safeDistanceMeters / 1_609.344
                String.format(Locale.UK, "%.1f mi", miles)
            }
            PreferredUnitsSetting.METRIC_KMH -> {
                val kilometers = safeDistanceMeters / 1_000.0
                String.format(Locale.UK, "%.1f km", kilometers)
            }
        }
    }

    private fun normalizePracticeRouteCoordinates(points: List<Point>): List<Point> {
        if (points.size < 3) return points
        val firstPoint = points.first()
        val lastPoint = points.last()
        val closesLoopAtSameCoordinate = distanceMeters(firstPoint, lastPoint) <= 12.0
        if (!closesLoopAtSameCoordinate) {
            return points
        }

        val secondPoint = points[1]
        val nudgedStart = Point.fromLngLat(
            firstPoint.longitude() + (secondPoint.longitude() - firstPoint.longitude()) * 0.03,
            firstPoint.latitude() + (secondPoint.latitude() - firstPoint.latitude()) * 0.03
        )

        return buildList {
            add(nudgedStart)
            addAll(points.drop(1))
        }
    }

    private fun distanceMeters(a: Point, b: Point): Double {
        val earthRadiusMeters = 6_371_000.0
        val lat1 = Math.toRadians(a.latitude())
        val lat2 = Math.toRadians(b.latitude())
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude() - a.longitude())
        val haversine = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
        return earthRadiusMeters * c
    }

    private fun normalizedCompletionPercent(rawPercent: Double): Int {
        if (!rawPercent.isFinite()) return 0
        val normalized = if (rawPercent <= 1.0) rawPercent * 100.0 else rawPercent
        return normalized.roundToInt().coerceIn(0, 100)
    }

    private fun updateCamera(
        point: Point,
        bearing: Double? = null,
        speedMetersPerSecond: Double = latestSpeedMetersPerSecond,
        locationUpdateIntervalMs: Long? = null,
        force: Boolean = false
    ) {
        if (!NavigationCameraModePolicy.shouldApplyLocationCameraUpdate(navigationCameraMode, force)) {
            return
        }
        val nowMs = System.currentTimeMillis()
        val movementSinceLastCameraUpdateMeters = lastCameraCenterPoint?.let { lastPoint ->
            distanceMeters(lastPoint, point)
        } ?: Double.MAX_VALUE
        if (
            !force &&
            lastCameraUpdateAtMs != 0L &&
            nowMs - lastCameraUpdateAtMs < cameraUpdateMinGapMs &&
            movementSinceLastCameraUpdateMeters < 2.0
        ) {
            return
        }

        val targetZoom = dynamicZoomLevel(speedMetersPerSecond, latestDistanceToManeuverMeters)
        val targetPitch = dynamicPitchLevel(speedMetersPerSecond)

        // Smooth zoom/pitch transitions to avoid jitter when speed changes rapidly.
        val zoomToApply = smoothCameraValue(lastCameraZoom, targetZoom, 0.3)
        val pitchToApply = smoothCameraValue(lastCameraPitch, targetPitch, 0.22)
        lastCameraZoom = zoomToApply
        lastCameraPitch = pitchToApply

        val effectiveIntervalMs = locationUpdateIntervalMs
            ?: if (lastCameraUpdateAtMs == 0L) 900L else (nowMs - lastCameraUpdateAtMs).coerceIn(250L, 1_600L)
        val animationDurationMs =
            if (navSessionState == NavSessionState.ACTIVE || uiMode == UiMode.PRACTICE) {
                (effectiveIntervalMs * 0.9).toLong().coerceIn(280L, 900L)
            } else {
                (effectiveIntervalMs * 1.1).toLong().coerceIn(550L, 1_450L)
            }
        val cameraPadding = dynamicCameraPadding(speedMetersPerSecond, latestDistanceToManeuverMeters)

        val mapAnimationOptions = MapAnimationOptions.Builder().duration(animationDurationMs).build()
        binding.mapView.camera.easeTo(
            CameraOptions.Builder()
                .center(point)
                .bearing(bearing)
                .zoom(zoomToApply)
                .pitch(pitchToApply)
                .padding(cameraPadding)
                .build(),
            mapAnimationOptions
        )
        lastCameraUpdateAtMs = nowMs
        lastCameraCenterPoint = point
    }

    private fun configurePuckAnimator(animator: ValueAnimator, durationMs: Long) {
        animator.duration = durationMs.coerceIn(puckMinTransitionMs, puckMaxTransitionMs)
        animator.interpolator = DecelerateInterpolator()
    }

    private fun dynamicZoomLevel(
        speedMetersPerSecond: Double,
        distanceToManeuverMeters: Double
    ): Double {
        val speedKmh = max(0.0, speedMetersPerSecond * 3.6)
        val baseZoom = when {
            speedKmh < 10.0 -> 18.5
            speedKmh < 20.0 -> 18.1
            speedKmh < 32.0 -> 17.7
            speedKmh < 45.0 -> 17.3
            speedKmh < 60.0 -> 16.9
            speedKmh < 80.0 -> 16.5
            else -> 16.1
        }
        val maneuverBoost = when {
            distanceToManeuverMeters <= 35.0 -> 1.25
            distanceToManeuverMeters <= 70.0 -> 1.0
            distanceToManeuverMeters <= 120.0 -> 0.75
            distanceToManeuverMeters <= 200.0 -> 0.45
            distanceToManeuverMeters <= 320.0 -> 0.25
            else -> 0.0
        }
        // Mini roundabouts are small and easy to miss — apply a strong dedicated boost so the
        // roundabout fills the screen clearly. This is independent of the Mapbox step maneuver
        // distance because mini roundabouts come from OSM features, not route step maneuvers.
        val miniRoundaboutBoost = when {
            latestNearestMiniRoundaboutMeters <= 25.0 -> 1.8
            latestNearestMiniRoundaboutMeters <= 50.0 -> 1.5
            latestNearestMiniRoundaboutMeters <= 80.0 -> 1.2
            latestNearestMiniRoundaboutMeters <= 130.0 -> 0.8
            latestNearestMiniRoundaboutMeters <= 200.0 -> 0.4
            else -> 0.0
        }

        // Learners need a tighter, more detailed view; standard drivers get a wider field.
        val modeOffset = when (currentDriverMode) {
            DriverMode.LEARNER -> 0.6
            DriverMode.NEW_DRIVER -> 0.3
            DriverMode.STANDARD -> 0.0
        }
        val zoomFloor = when (currentDriverMode) {
            DriverMode.LEARNER -> 16.4
            DriverMode.NEW_DRIVER -> 16.1
            DriverMode.STANDARD -> 15.8
        }
        val zoomCeiling = when (currentDriverMode) {
            DriverMode.LEARNER -> 19.5
            DriverMode.NEW_DRIVER -> 19.2
            DriverMode.STANDARD -> 19.0
        }
        return (baseZoom + maxOf(maneuverBoost, miniRoundaboutBoost) + modeOffset).coerceIn(zoomFloor, zoomCeiling)
    }

    private fun dynamicPitchLevel(speedMetersPerSecond: Double): Double {
        val speedKmh = max(0.0, speedMetersPerSecond * 3.6)
        val basePitch = when {
            speedKmh < 16.0 -> 34.0
            speedKmh < 32.0 -> 38.0
            speedKmh < 55.0 -> 42.0
            speedKmh < 80.0 -> 46.0
            else -> 48.0
        }
        // Learners benefit from a flatter (lower pitch) view so more road ahead is visible.
        val modeOffset = when (currentDriverMode) {
            DriverMode.LEARNER -> -4.0
            DriverMode.NEW_DRIVER -> -2.0
            DriverMode.STANDARD -> 0.0
        }
        return (basePitch + modeOffset).coerceIn(28.0, 50.0)
    }

    private fun dynamicCameraPadding(
        speedMetersPerSecond: Double,
        distanceToManeuverMeters: Double
    ): EdgeInsets {
        val speedKmh = max(0.0, speedMetersPerSecond * 3.6)
        val baseTopPadding = when {
            speedKmh < 20.0 -> 640.0
            speedKmh < 45.0 -> 700.0
            speedKmh < 70.0 -> 760.0
            else -> 820.0
        }
        val maneuverTighten = when {
            distanceToManeuverMeters <= 60.0 -> 150.0
            distanceToManeuverMeters <= 140.0 -> 100.0
            distanceToManeuverMeters <= 250.0 -> 60.0
            else -> 0.0
        }
        val topPadding = (baseTopPadding - maneuverTighten).coerceIn(520.0, 860.0)
        return EdgeInsets(topPadding, 20.0, 250.0, 20.0)
    }

    private fun smoothCameraValue(previous: Double, target: Double, factor: Double): Double {
        val clampedFactor = factor.coerceIn(0.05, 0.9)
        if (abs(target - previous) < 0.02) {
            return target
        }
        return previous + ((target - previous) * clampedFactor)
    }

    private fun replayOriginLocation() {
        if (!replayEnabled) return
        val originPoint = initialCameraCenter()
        with(mapboxNavigation.mapboxReplayer) {
            stop()
            clearEvents()
            pushEvents(
                listOf(
                    ReplayRouteMapper.mapToUpdateLocation(Date().time.toDouble(), originPoint)
                )
            )
            playFirstLocation()
            playbackSpeed(1.0)
            play()
        }
        mapboxNavigation.startReplayTripSession()
        Log.d(TAG, "Replay anchored to origin only.")
    }

    private fun syncReplayToPrimaryRoute() {
        if (!replayEnabled) return
        val primaryRoute = mapboxNavigation.getNavigationRoutes().firstOrNull()
        if (primaryRoute == null) {
            replayOriginLocation()
            return
        }
        syncReplayToRoute(primaryRoute)
    }

    private fun syncReplayToRoute(route: NavigationRoute) {
        if (!replayEnabled) return
        val replayEvents = replayRouteMapper.mapDirectionsRouteGeometry(route.directionsRoute)
        if (replayEvents.isEmpty()) {
            replayOriginLocation()
            return
        }

        with(mapboxNavigation.mapboxReplayer) {
            stop()
            clearEvents()
            pushEvents(replayEvents)
            playFirstLocation()
            playbackSpeed(1.0)
            play()
        }
        mapboxNavigation.startReplayTripSession()
        Log.d(TAG, "Replay synced to route geometry. events=${replayEvents.size}")
    }

    private fun isLikelyEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase(Locale.US)
        val model = Build.MODEL.lowercase(Locale.US)
        val product = Build.PRODUCT.lowercase(Locale.US)
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.US)
        val brand = Build.BRAND.lowercase(Locale.US)
        val device = Build.DEVICE.lowercase(Locale.US)
        val hardware = Build.HARDWARE.lowercase(Locale.US)

        return fingerprint.startsWith("generic") ||
            fingerprint.contains("emulator") ||
            model.contains("emulator") ||
            model.contains("android sdk built for") ||
            product.contains("sdk") ||
            product.contains("emulator") ||
            hardware.contains("goldfish") ||
            hardware.contains("ranchu") ||
            manufacturer.contains("genymotion") ||
            (brand.startsWith("generic") && device.startsWith("generic"))
    }

    private companion object {
        private const val TAG = "MainActivity"
    }
}
