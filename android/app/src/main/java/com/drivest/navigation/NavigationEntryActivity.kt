package com.drivest.navigation

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class NavigationEntryActivity : AppCompatActivity() {

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            openDestinationSearch()
        } else {
            Toast.makeText(
                this,
                getString(R.string.location_permission_required_for_routing),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_navigation_entry)

        findViewById<MaterialButton>(R.id.openNavigationMapButton).setOnClickListener {
            if (LocationPrePromptDialog.hasLocationPermission(this)) {
                openDestinationSearch()
                return@setOnClickListener
            }
            LocationPrePromptDialog.show(
                activity = this,
                onAllow = { requestLocationPermission() }
            )
        }
    }

    private fun requestLocationPermission() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun openDestinationSearch() {
        startActivity(Intent(this, DestinationSearchActivity::class.java))
    }
}
