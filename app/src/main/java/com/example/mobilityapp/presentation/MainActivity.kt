package com.example.mobilityapp.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.mobilityapp.presentation.map.OfflineMapScreen

class MainActivity : ComponentActivity() {
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            showLocationPermissionRequiredMessage()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                showLocationPermissionRequiredMessage()
            }
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        setContent {
            MobilityApp()
        }
    }

    private fun showLocationPermissionRequiredMessage() {
        android.widget.Toast.makeText(
            this,
            getString(com.example.mobilityapp.R.string.location_permission_required),
            android.widget.Toast.LENGTH_LONG
        ).show()
    }
}

@Composable
private fun MobilityApp() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            OfflineMapScreen()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MobilityAppPreview() {
    MobilityApp()
}
