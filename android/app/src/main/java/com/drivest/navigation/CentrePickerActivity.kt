package com.drivest.navigation

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.drivest.navigation.backend.BackendCentreRepository
import com.drivest.navigation.backend.BackendHazardRepository
import com.drivest.navigation.backend.BackendPracticeRouteStore
import com.drivest.navigation.data.CentreRepository
import com.drivest.navigation.data.TestCentre
import com.drivest.navigation.databinding.ActivityCentrePickerBinding
import com.drivest.navigation.pack.PackStore
import com.drivest.navigation.pack.PackType
import com.google.android.material.snackbar.Snackbar
import com.drivest.navigation.settings.SettingsRepository
import com.drivest.navigation.subscription.FeatureAccessManager
import com.drivest.navigation.subscription.SubscriptionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CentrePickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCentrePickerBinding
    private lateinit var centreAdapter: CentreAdapter
    private val settingsRepository by lazy { SettingsRepository(applicationContext) }
    private val centreRepository by lazy {
        CentreRepository(
            context = this,
            settingsRepository = settingsRepository,
            backendCentreRepository = BackendCentreRepository(context = applicationContext)
        )
    }
    private val backendPracticeRouteStore by lazy {
        BackendPracticeRouteStore(context = applicationContext)
    }
    private val backendHazardRepository by lazy {
        BackendHazardRepository(context = applicationContext)
    }
    private val packStore by lazy { PackStore(applicationContext) }
    private val subscriptionRepository by lazy { SubscriptionRepository(applicationContext) }
    private val featureAccessManager by lazy {
        FeatureAccessManager(
            subscriptionRepository = subscriptionRepository,
            settingsRepository = settingsRepository
        )
    }
    private val selectPracticeCentreOnly: Boolean by lazy {
        intent.getBooleanExtra(EXTRA_SELECT_PRACTICE_CENTRE_ONLY, false)
    }
    private var allCentres: List<TestCentre> = emptyList()
    private val downloadingCentreIds = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCentrePickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        centreAdapter = CentreAdapter(
            onCentreClick = ::onCentreSelected,
            onOfflineDownloadClick = ::onDownloadOfflinePackClicked
        )

        binding.centreRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@CentrePickerActivity)
            adapter = centreAdapter
        }

        binding.centreSearchInput.doAfterTextChanged { editable ->
            filterCentres(editable?.toString().orEmpty())
        }

        lifecycleScope.launch {
            loadCentres()
        }
    }

    private suspend fun loadCentres() {
        allCentres = runCatching {
            centreRepository.loadCentres()
        }
            .onFailure {
                Toast.makeText(
                    this,
                    getString(R.string.centre_picker_load_error),
                    Toast.LENGTH_LONG
                ).show()
            }
            .getOrDefault(emptyList())
        maybeShowOfflineDataSourceBanner()
        refreshOfflineAvailability()

        filterCentres(binding.centreSearchInput.text?.toString().orEmpty())
    }

    private fun filterCentres(rawQuery: String) {
        val query = rawQuery.trim().lowercase()
        val filtered = if (query.isBlank()) {
            allCentres
        } else {
            allCentres.filter { centre ->
                centre.name.lowercase().contains(query) ||
                    centre.address.lowercase().contains(query)
            }
        }

        binding.emptyCentresView.isVisible = filtered.isEmpty()
        centreAdapter.submitList(filtered)
        centreAdapter.setOfflineCentreIds(
            filtered
                .map { it.id }
                .filter { centreId -> packStore.isOfflineAvailable(PackType.ROUTES, centreId) }
                .toSet()
        )
        centreAdapter.setDownloadingCentreIds(downloadingCentreIds.toSet())
    }

    private fun onCentreSelected(centre: TestCentre) {
        lifecycleScope.launch {
            settingsRepository.setLastSelectedCentreId(centre.id)
            if (selectPracticeCentreOnly) {
                settingsRepository.setPracticeCentreId(centre.id)
                setResult(
                    RESULT_OK,
                    Intent().putExtra(RESULT_PRACTICE_CENTRE_ID, centre.id)
                )
                finish()
                return@launch
            }
            val practiceCentreId = settingsRepository.practiceCentreId.first()
            if (practiceCentreId.isNullOrBlank()) {
                settingsRepository.setPracticeCentreId(centre.id)
            }
            startActivity(
                Intent(this@CentrePickerActivity, PracticeRoutesActivity::class.java)
                    .putExtra(AppFlow.EXTRA_CENTRE_ID, centre.id)
            )
        }
    }

    private suspend fun maybeShowOfflineDataSourceBanner() {
        if (settingsRepository.consumeOfflineDataSourceBannerSlot()) {
            Snackbar.make(binding.root, getString(R.string.using_offline_data_source), Snackbar.LENGTH_LONG)
                .show()
        }
    }

    private fun onDownloadOfflinePackClicked(centre: TestCentre) {
        if (downloadingCentreIds.contains(centre.id)) return

        lifecycleScope.launch {
            val hasOfflineAccess = featureAccessManager.hasOfflineAccess().first()
            if (!hasOfflineAccess) {
                openPaywall(getString(R.string.paywall_feature_offline_download))
                return@launch
            }
            setCentreDownloading(centre.id, true)
            val (routesDownloaded, hazardsDownloaded, errorMessage) = withContext(Dispatchers.IO) {
                var routesOk = false
                var hazardsOk = false
                var failure: String? = null

                runCatching {
                    backendPracticeRouteStore.loadRoutesForCentre(centre.id)
                }.onSuccess { routes ->
                    if (routes.isNotEmpty()) {
                        packStore.markOfflineAvailable(PackType.ROUTES, centre.id, true)
                        routesOk = true
                    } else {
                        failure = getString(R.string.centre_picker_download_routes_empty)
                    }
                }.onFailure { error ->
                    failure = error.message ?: getString(R.string.centre_picker_download_failed)
                }

                if (routesOk) {
                    runCatching {
                        backendHazardRepository.loadHazardsForCentre(centre.id)
                    }.onSuccess {
                        packStore.markOfflineAvailable(PackType.HAZARDS, centre.id, true)
                        hazardsOk = true
                    }
                }

                Triple(routesOk, hazardsOk, failure)
            }

            setCentreDownloading(centre.id, false)
            refreshOfflineAvailability()

            when {
                routesDownloaded && hazardsDownloaded -> {
                    Toast.makeText(
                        this@CentrePickerActivity,
                        getString(R.string.centre_picker_download_success),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                routesDownloaded -> {
                    Toast.makeText(
                        this@CentrePickerActivity,
                        getString(R.string.centre_picker_download_routes_only),
                        Toast.LENGTH_LONG
                    ).show()
                }
                else -> {
                    Toast.makeText(
                        this@CentrePickerActivity,
                        errorMessage ?: getString(R.string.centre_picker_download_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun openPaywall(featureLabel: String) {
        startActivity(
            Intent(this, PaywallActivity::class.java).putExtra(
                PaywallActivity.EXTRA_FEATURE_LABEL,
                featureLabel
            )
        )
    }

    private fun setCentreDownloading(centreId: String, downloading: Boolean) {
        if (downloading) {
            downloadingCentreIds.add(centreId)
        } else {
            downloadingCentreIds.remove(centreId)
        }
        centreAdapter.setDownloadingCentreIds(downloadingCentreIds.toSet())
    }

    private fun refreshOfflineAvailability() {
        val offlineReadyIds = allCentres
            .map { it.id }
            .filter { centreId -> packStore.isOfflineAvailable(PackType.ROUTES, centreId) }
            .toSet()
        centreAdapter.setOfflineCentreIds(offlineReadyIds)
    }

    companion object {
        const val EXTRA_SELECT_PRACTICE_CENTRE_ONLY = "extra_select_practice_centre_only"
        const val RESULT_PRACTICE_CENTRE_ID = "result_practice_centre_id"
    }
}
