package com.pulseloop

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.pulseloop.notifications.CoachNotifications
import com.pulseloop.ui.PulseLoopApp

/**
 * Single-activity host for the PulseLoop Compose UI.
 * Ported from the SwiftUI App entry point in PulseLoopApp.swift.
 */
class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            CoachNotifications.schedule(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Set up notification channel + schedule daily check-ins
        CoachNotifications.createChannel(this)
        requestNotificationPermission()

        setContent {
            PulseLoopApp()
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-schedule notifications (no-op if already scheduled)
        if (hasNotificationPermission()) {
            CoachNotifications.schedule(this)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                CoachNotifications.schedule(this)
            }
        } else {
            CoachNotifications.schedule(this)
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else true
    }
}
