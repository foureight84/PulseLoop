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
import com.pulseloop.ui.screens.*
import com.pulseloop.ui.theme.PulseLoopTheme

/**
 * Root composable — ported from RootViews.swift.
 * Bottom tab navigation: Today, Vitals, Sleep, Activity, Coach.
 */
@Composable
fun PulseLoopApp() {
    PulseLoopTheme {
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
                composable("today") { TodayScreen() }
                composable("vitals") { VitalsScreen() }
                composable("sleep") { SleepScreen() }
                composable("activity") { ActivityScreen() }
                composable("coach") { CoachScreen() }
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
