package com.example.mobilityapp.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MobilityApp()
        }
    }
}

@Composable
private fun MobilityApp() {
    MaterialTheme {
        Surface {
            Text(text = "MobilityAPP Offline")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MobilityAppPreview() {
    MobilityApp()
}
