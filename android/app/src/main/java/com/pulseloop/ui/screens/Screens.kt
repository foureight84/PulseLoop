package com.pulseloop.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.navigation.NavController
import com.pulseloop.service.HeartRateZones
import com.pulseloop.ui.components.MetricTile
import com.pulseloop.ui.components.SimpleLineChart
import com.pulseloop.ui.viewmodels.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Today dashboard — ported from TodayView.swift.
 * Shows daily summary: steps, calories, distance, active minutes,
 * heart rate, SpO2, plus a mini sparkline for each.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material.ExperimentalMaterialApi::class)
@Composable
fun TodayScreen(
    navController: androidx.navigation.NavController? = null,
    viewModel: TodayViewModel? = null,
    coordinator: com.pulseloop.service.RingSyncCoordinator? = null,
) {
    val state by (viewModel?.state?.collectAsState() ?: remember { mutableStateOf(TodayViewModel.TodayState()) })
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            scope.launch {
                coordinator?.pullToRefresh()
                kotlinx.coroutines.delay(1500)
                isRefreshing = false
            }
        },
    )

    Box(Modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Today", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    // Connection status
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Icon(
                            Icons.Filled.Bluetooth, null,
                            modifier = Modifier.size(14.dp),
                            tint = if (state.isConnected) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (state.isConnected) "Connected · ${state.batteryPercent}%"
                            else if (state.deviceState == "CONNECTING") "Connecting…"
                            else "Disconnected",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.isConnected) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // Last data refresh indicator
                        if (state.lastUpdated > 0) {
                            val secondsAgo = (System.currentTimeMillis() - state.lastUpdated) / 1000
                            Text(
                                if (secondsAgo < 5) "just now" else "${secondsAgo}s ago",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
                Row {
                    if (navController != null) {
                        IconButton(onClick = { navController.navigate("settings") }) {
                            Icon(Icons.Filled.Settings, "Settings")
                        }
                        IconButton(onClick = {
                            if (state.isConnected) {
                                // Already connected — sync now
                                scope.launch { coordinator?.syncNow() }
                            } else {
                                navController.navigate("pairing")
                            }
                        }) {
                            Icon(
                                if (state.isConnected) Icons.Filled.Sync else Icons.Filled.BluetoothConnected,
                                contentDescription = if (state.isConnected) "Sync" else "Pair Ring",
                                tint = if (state.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = "Steps",
                    value = formatNumber(state.steps),
                    unit = "steps",
                    trend = null,
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = "Calories",
                    value = state.calories?.let { formatNumber(it.toInt()) } ?: "--",
                    unit = "kcal",
                    trend = null,
                )
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = "Distance",
                    value = state.distanceMeters?.let { "%.1f".format(it / 1000) } ?: "--",
                    unit = "km",
                    trend = null,
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = "Active",
                    value = state.activeMinutes?.toString() ?: "--",
                    unit = "min",
                    trend = null,
                )
            }
        }
        // Distance + Active come from VM data above (no duplicate)

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Heart Rate", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        state.heartRate?.let { "$it bpm" } ?: "-- bpm",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(state.restingHR?.let { "Resting · %.0f bpm".format(it) } ?: "No recent data",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("SpO₂", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        state.spo2?.let { "$it%" } ?: "--%",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(if (state.spo2 != null) "Latest reading" else "No recent data",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile(Modifier.weight(1f), "Sleep", state.sleepMinutes?.let { "${it / 60}h ${it % 60}m" } ?: "--", "last night", null)
                MetricTile(Modifier.weight(1f), "Battery", "${state.batteryPercent}%", if (state.isConnected) "connected" else "--", null)
            }
        }

        item {
            if (state.steps == null) {
                Text(
                    "No ring data yet — pair a ring to see your metrics",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }

        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

/**
 * Vitals dashboard — ported from VitalsView.swift.
 * Shows historical trends: HR, SpO2, HRV, stress, temperature with real data from Room.
 */
@Composable
fun VitalsScreen(viewModel: VitalsViewModel? = null) {
    val state by (viewModel?.state?.collectAsState() ?: remember { mutableStateOf(VitalsViewModel.VitalsState()) })
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Vitals", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Live measurements and trends", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
        }

        // Heart Rate
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Heart Rate", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    if (state.hrSamples.isNotEmpty()) {
                        val avg = state.hrSamples.average().toInt()
                        val min = state.hrSamples.min().toInt()
                        val max = state.hrSamples.max().toInt()
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(state.latestHr?.toString() ?: "--", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                            Text(" bpm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                        }
                        Text("Range: $min – $max · Avg: $avg bpm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        SimpleLineChart(points = state.hrSamples, color = androidx.compose.ui.graphics.Color(0xFFE53935))
                    } else {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("--", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(" bpm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                        }
                        Text("No HR samples yet — sync your ring to start your trend.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // SpO2
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Blood Oxygen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    if (state.spo2Samples.isNotEmpty()) {
                        val avg = state.spo2Samples.average().toInt()
                        val min = state.spo2Samples.min().toInt()
                        val max = state.spo2Samples.max().toInt()
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(state.latestSpo2?.toString() ?: "--", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                            Text(" %", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                        }
                        Text("Range: $min – $max% · Avg: $avg%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        SimpleLineChart(points = state.spo2Samples, color = androidx.compose.ui.graphics.Color(0xFF1E88E5))
                    } else {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("--", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(" %", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                        }
                        Text("No SpO₂ samples yet — take a reading to start your trend.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Stress (capability-gated)
        if (state.supportsStress) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Stress", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        if (state.stressSamples.isNotEmpty()) {
                            val latest = state.latestStress?.toInt() ?: 0
                            val label = when {
                                latest <= 30 -> "Low"
                                latest <= 60 -> "Moderate"
                                else -> "High"
                            }
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(label, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(12.dp))
                            SimpleLineChart(points = state.stressSamples, color = androidx.compose.ui.graphics.Color(0xFF8E24AA))
                        } else {
                            Text("No stress data yet — wear the ring through the day and sync.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // HRV (capability-gated)
        if (state.supportsHrv) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("HRV", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        val hrvVal = state.latestHrv
                        if (hrvVal != null && state.hrvSamples.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(String.format("%.0f", hrvVal), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                                Text(" ms", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                            }
                            Spacer(Modifier.height(12.dp))
                            SimpleLineChart(points = state.hrvSamples, color = androidx.compose.ui.graphics.Color(0xFF43A047))
                        } else {
                            Text("No HRV data yet — HRV builds up over a few hours of wear.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // Temperature (capability-gated)
        if (state.supportsTemp) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Skin Temperature", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        val tempVal = state.latestTemp
                        if (tempVal != null && state.tempSamples.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(String.format("%.1f", tempVal), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                                Text(" °C", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                            }
                            Spacer(Modifier.height(12.dp))
                            SimpleLineChart(points = state.tempSamples, color = androidx.compose.ui.graphics.Color(0xFFFF7043))
                        } else {
                            Text("No temperature data yet — temperature trends appear after overnight wear.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }
}

/**
 * Sleep dashboard — ported from SleepView.swift.
 */
@Composable
fun SleepScreen(
    navController: androidx.navigation.NavController? = null,
    viewModel: SleepViewModel? = null,
) {
    val state by (viewModel?.state?.collectAsState() ?: remember { mutableStateOf(SleepViewModel.SleepState()) })
    val lastNight = state.lastNight
    val totalHr = lastNight?.totalMinutes?.let { it / 60 }
    val totalMin = lastNight?.totalMinutes?.let { it % 60 }
    val timeStr = if (totalHr != null) "${totalHr}h ${totalMin}m" else "--"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Sleep", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                if (navController != null) {
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Filled.Settings, "Settings")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Last Night", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(timeStr, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                    if (lastNight != null) {
                        Text(
                            "${java.time.Instant.ofEpochMilli(lastNight.startAt).atZone(java.time.ZoneId.systemDefault()).toLocalTime()} – ${java.time.Instant.ofEpochMilli(lastNight.endAt).atZone(java.time.ZoneId.systemDefault()).toLocalTime()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text("No sleep data yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Sleep Score", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    val score = lastNight?.totalMinutes?.let { (it / 5).coerceAtMost(100) } ?: 0
                    Text("$score / 100", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (state.recentSessions.size > 1) {
            item {
                Text("Recent Nights", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            state.recentSessions.drop(1).forEach { session ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                java.time.Instant.ofEpochMilli(session.date).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString(),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text("${session.totalMinutes / 60}h ${session.totalMinutes % 60}m", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StageBadge(label: String, duration: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        Text(duration, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Activity dashboard — ported from ActivityView.swift.
 */
@Composable
fun ActivityScreen(
    navController: androidx.navigation.NavController? = null,
    viewModel: ActivityViewModel? = null,
) {
    val state by (viewModel?.state?.collectAsState() ?: remember { mutableStateOf(ActivityViewModel.ActivityState()) })
    val today = state.recentDays.firstOrNull()
    val todaySteps = today?.steps ?: 0
    val todayDistance = today?.distanceMeters ?: 0.0

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Activity", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                if (navController != null) {
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Filled.Settings, "Settings")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile(Modifier.weight(1f), "Steps", formatNumber(todaySteps), "today", null)
                MetricTile(Modifier.weight(1f), "Distance", if (todayDistance > 0) "%.1f".format(todayDistance / 1000) else "--", "km", null)
            }
        }

        if (state.recentWorkouts.isNotEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Recent Workouts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(12.dp))
                        state.recentWorkouts.forEach { wo ->
                            val elapsed = wo.endedAt?.let { (it - wo.startedAt) / 1000 }?.toInt() ?: 0
                            val min = elapsed / 60
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(wo.type, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(
                                        "${wo.distanceMeters?.let { "%.1f km · ".format(it / 1000) } ?: ""}${min} min",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = { navController?.navigate("record") },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text("Start Workout")
            }
        }
    }
}

/**
 * Coach chat screen — ported from CoachView.swift.
 */
@Composable
fun CoachScreen(
    navController: androidx.navigation.NavController? = null,
    viewModel: CoachViewModel? = null,
) {
    val state by (viewModel?.state?.collectAsState() ?: remember {
        mutableStateOf(CoachViewModel.CoachState(
            messages = listOf(CoachViewModel.ChatMessage("assistant",
                "Hi! I'm your PulseLoop coach. I can answer questions about your sleep, heart rate, activity, and recovery. What would you like to know?"))
        ))
    })
    var inputText by remember { mutableStateOf("") }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Auto-scroll to bottom on new messages
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Coach", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            if (navController != null) {
                IconButton(onClick = { navController.navigate("settings") }) {
                    Icon(Icons.Filled.Settings, "Settings")
                }
            }
        }

        // Messages
        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.messages.size) { idx ->
                val msg = state.messages[idx]
                val isUser = msg.role == "user"
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                if (isUser) "You" else "🤖 Coach",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(msg.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            if (state.isThinking) {
                item {
                    Text(
                        "Coach is thinking…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }

            if (state.error != null) {
                item {
                    Text(
                        "Error: ${state.error}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }
        }

        // Input
        Surface(
            tonalElevation = 2.dp,
            shadowElevation = 2.dp,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask your coach…") },
                    singleLine = false,
                    maxLines = 3,
                    enabled = !state.isThinking,
                )
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank() && viewModel != null) {
                            viewModel.sendMessage(inputText.trim())
                            inputText = ""
                        }
                    },
                    enabled = inputText.isNotBlank() && !state.isThinking,
                ) {
                    Icon(Icons.Filled.Send, "Send")
                }
            }
        }
    }
}

private fun formatNumber(value: Int?): String {
    if (value == null) return "--"
    return "%,d".format(value)
}
