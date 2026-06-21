package com.pulseloop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pulseloop.ui.PulseLoopApp

/**
 * Single-activity host for the PulseLoop Compose UI.
 * Ported from the SwiftUI App entry point in PulseLoopApp.swift.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PulseLoopApp()
        }
    }
}
