package com.pulseloop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pulseloop.coach.orchestration.CoachOrchestrator
import com.pulseloop.coach.openai.OpenAIResponsesClient
import com.pulseloop.coach.tools.*
import com.pulseloop.data.PulseLoopDatabase
import com.pulseloop.ring.RingBLEClient
import com.pulseloop.service.*
import com.pulseloop.coach.summaries.CoachSummaryCoordinator
import com.pulseloop.settings.ApiKeyStore
import com.pulseloop.ui.screens.*
import com.pulseloop.ui.theme.PulseLoopTheme
import com.pulseloop.ui.viewmodels.*

/**
 * Root composable — ported from PulseLoopApp.swift + RootViews.swift.
 * Wires BLE, persistence, coach, and all ViewModels at app startup.
 */
@Composable
fun PulseLoopApp() {
    PulseLoopTheme {
        val context = LocalContext.current

        // ── Singletons ───────────────────────────────────────────────────
        val db = remember { PulseLoopDatabase.getInstance(context) }
        val bleClient = remember { RingBLEClient(context) }
        val coordinator = remember { RingSyncCoordinator(bleClient, db) }
        val gpsRecorder = remember { GpsRouteRecorder(context) }
        val liveWorkout = remember { LiveWorkoutManager(coordinator, db, gpsRecorder, context) }
        val persistence = remember { EventPersistenceSubscriber(db) }
        val apiKeyStore = remember { ApiKeyStore(context) }
        val summaryCoordinator = remember { CoachSummaryCoordinator(db, apiKeyStore) }

        // ── Coach wiring ─────────────────────────────────────────────────
        val coachOrchestrator = remember {
            val apiKey = apiKeyStore.apiKey
            val flags = CoachFeatureFlags(
                coachEnabled = apiKeyStore.coachEnabled && apiKey.isNotEmpty(),
                webSearchEnabled = apiKeyStore.webSearchEnabled,
                writeToolsEnabled = false,  // safe default
                liveMeasurementsEnabled = true,
                model = apiKeyStore.model.ifEmpty { "gpt-5.4" },
            )
            val client = OpenAIResponsesClient(apiKey)
            val registry = ToolRegistry(flags)
            val toolContext = ToolExecutionContext(
                db = db,
                flags = flags,
                coordinator = coordinator,
            )
            CoachOrchestrator(client, registry, flags, toolContext)
        }

        // ── ViewModels ───────────────────────────────────────────────────
        val todayVM = remember { TodayViewModel(db) }
        val vitalsVM = remember { VitalsViewModel(db) }
        val sleepVM = remember { SleepViewModel(db) }
        val activityVM = remember { ActivityViewModel(db) }
        val coachVM = remember { CoachViewModel(db, coachOrchestrator) }

        // ── Start services (one-shot on composition) ─────────────────────
        LaunchedEffect(Unit) {
            // Wire onConnected → run startup sequence
            bleClient.onConnected = { coordinator.runStartupSequence() }

            // Wire firmware read → persist to DB
            bleClient.onFirmwareRead = { fw ->
                kotlinx.coroutines.runBlocking {
                    val dev = db.deviceDao().current()
                    if (dev != null) {
                        db.deviceDao().upsert(dev.copy(firmwareVersion = fw, updatedAt = System.currentTimeMillis()))
                    }
                }
            }

            // Start services
            persistence.start()
            coordinator.start()
            summaryCoordinator.start()

            // Auto-reconnect to last-known ring if any
            if (bleClient.hasPermissions()) {
                bleClient.connectLastKnown()
            }

            // Seed demo data ONLY on first launch with no ring data
            val device = db.deviceDao().current()
            val hasAnyActivity = db.activityDailyDao().recent(1).isNotEmpty()
            if (device == null && !hasAnyActivity && !apiKeyStore.demoDataSeeded) {
                com.pulseloop.data.DemoDataSeeder.seed(db)
                apiKeyStore.demoDataSeeded = true
            }
        }

        // ── Navigation ───────────────────────────────────────────────────
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
                                if (selected) return@NavigationBarItem
                                // Pop everything above the start destination but keep it.
                                // launchSingleTop jumps back to the existing tab instance.
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.startDestinationId)
                                    launchSingleTop = true
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
                composable("today") { TodayScreen(navController, todayVM, coordinator) }
                composable("vitals") { VitalsScreen(viewModel = vitalsVM) }
                composable("sleep") { SleepScreen(navController = navController, viewModel = sleepVM) }
                composable("activity") { ActivityScreen(navController = navController, viewModel = activityVM) }
                composable("coach") { CoachScreen(navController = navController, viewModel = coachVM) }
                composable("settings") { SettingsScreen(navController, bleClient, coordinator) }
                composable("debug") { DebugScreen(onBack = { navController.popBackStack() }) }
                composable("onboarding") { OnboardingScreen(onComplete = { navController.navigate("pairing") }) }
                composable("record") {
                    val workoutState = liveWorkout.state.collectAsState().value
                    RecordScreen(
                        activityName = workoutState.activeSession?.type ?: "Workout",
                        elapsedSeconds = workoutState.elapsedSeconds,
                        distanceMeters = workoutState.distanceMeters,
                        heartRate = workoutState.latestHeartRate,
                        spO2 = workoutState.latestSpO2,
                        isPaused = workoutState.isPaused,
                        hrZone = workoutState.hrZone,
                        onPause = {
                            workoutState.activeSession?.let { kotlinx.coroutines.runBlocking { liveWorkout.pause(it) } }
                        },
                        onResume = {
                            workoutState.activeSession?.let { kotlinx.coroutines.runBlocking { liveWorkout.resume(it) } }
                        },
                        onFinish = {
                            workoutState.activeSession?.let {
                                kotlinx.coroutines.runBlocking { liveWorkout.finish(it) }
                                navController.popBackStack()
                            }
                        },
                    )
                }
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
