package com.pulseloop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pulseloop.data.PulseLoopDatabase
import com.pulseloop.ring.RingBLEClient
import com.pulseloop.ui.screens.*
import com.pulseloop.ui.theme.PulseLoopTheme
import com.pulseloop.ui.viewmodels.TodayViewModel

/**
 * Root composable — ported from RootViews.swift.
 * Bottom tab navigation: Today, Vitals, Sleep, Activity, Coach.
 */
@Composable
fun PulseLoopApp() {
    PulseLoopTheme {
        val context = androidx.compose.ui.platform.LocalContext.current
        val db = remember { PulseLoopDatabase.getInstance(context) }
        val todayVM = remember { TodayViewModel(db) }
        val bleClient = remember { RingBLEClient(context) }
        val navController = rememberNavController()
        val tabs = listOf(
            Tab("today", "Today", Icons.Filled.Today, Icons.Outlined.Today),
            Tab("vitals", "Vitals", Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder),
            Tab("sleep", "Sleep", Icons.Filled.Bedtime, Icons.Outlined.Bedtime),
            Tab("activity", "Activity", Icons.Filled.DirectionsRun, Icons.Outlined.DirectionsRun),
            Tab("coach", "Coach", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
        )

        Scaffold(
            bottomBar = {
                NavigationBar {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination
                    tabs.forEach { tab ->
                        val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label,
                                )
                            },
                            label = { Text(tab.label) },
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = "today",
                modifier = Modifier.padding(padding),
            ) {
                composable("today") { TodayScreen(navController, todayVM) }
                composable("vitals") { VitalsScreen() }
                composable("sleep") { SleepScreen() }
                composable("activity") { ActivityScreen() }
                composable("coach") { CoachScreen() }
                composable("settings") { SettingsScreen() }
                composable("debug") { DebugScreen(onBack = { navController.popBackStack() }) }
                composable("onboarding") { OnboardingScreen(onComplete = { navController.navigate("pairing") }) }
                composable("pairing") {
                    PairingScreen(
                        bleClient = bleClient,
                        onConnected = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}

private data class Tab(
    val route: String,
    val label: String,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
)
