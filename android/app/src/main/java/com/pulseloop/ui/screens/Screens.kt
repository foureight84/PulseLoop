package com.pulseloop.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulseloop.ui.components.MetricTile

/**
 * Today dashboard — ported from TodayView.swift.
 * Shows daily summary: steps, calories, distance, active minutes,
 * heart rate, SpO2, plus a mini sparkline for each.
 */
@Composable
fun TodayScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Today", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = "Steps",
                    value = "8,432",
                    unit = "steps",
                    trend = "+12%",
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = "Calories",
                    value = "342",
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
                    value = "5.2",
                    unit = "km",
                    trend = "+8%",
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = "Active",
                    value = "45",
                    unit = "min",
                    trend = null,
                )
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Heart Rate", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "72 bpm",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text("Resting · 58 bpm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("SpO₂", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "98%",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text("Last reading · 2 min ago", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Text(
                "Demo data — pair a ring to see your metrics",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/**
 * Vitals dashboard — ported from VitalsView.swift.
 * Shows historical trends: HR, SpO2, HRV, stress, temperature.
 */
@Composable
fun VitalsScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Vitals", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Heart Rate Trend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(24.dp))
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        Text("Chart placeholder", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        MetricTile(Modifier.weight(1f), "Min", "58", "bpm", null)
                        Spacer(Modifier.width(8.dp))
                        MetricTile(Modifier.weight(1f), "Avg", "72", "bpm", null)
                        Spacer(Modifier.width(8.dp))
                        MetricTile(Modifier.weight(1f), "Max", "142", "bpm", null)
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("SpO₂ · 7 days", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(24.dp))
                    Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        Text("Chart placeholder", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        listOf("HRV" to "42 ms", "Stress" to "Low", "Temperature" to "36.5°C").forEach { (label, value) ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = MaterialTheme.typography.titleMedium)
                        Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

/**
 * Sleep dashboard — ported from SleepView.swift.
 */
@Composable
fun SleepScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Sleep", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Last Night", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text("7h 23m", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                    Text("11:34 PM – 6:57 AM", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Sleep Stages", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(24.dp))
                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        Text("Hypnogram placeholder", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StageBadge("Awake", "45m", MaterialTheme.colorScheme.tertiary)
                        StageBadge("REM", "1h 30m", MaterialTheme.colorScheme.primary)
                        StageBadge("Light", "3h 45m", MaterialTheme.colorScheme.secondary)
                        StageBadge("Deep", "1h 23m", MaterialTheme.colorScheme.primaryContainer)
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Sleep Score", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text("85 / 100", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
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
fun ActivityScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Activity", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile(Modifier.weight(1f), "Steps", "8,432", "today", "+12%")
                MetricTile(Modifier.weight(1f), "Distance", "5.2", "km", null)
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Recent Workouts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    listOf(
                        "Morning Run" to "3.2 km · 22 min · 142 bpm avg",
                        "Evening Walk" to "1.8 km · 18 min · 98 bpm avg",
                    ).forEach { (title, details) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (title != "Evening Walk") HorizontalDivider()
                    }
                }
            }
        }

        item {
            Button(
                onClick = {},
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
fun CoachScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Coach", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Your AI health coach, grounded in your ring data.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }

        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("🤖 Coach", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Hi! I'm your PulseLoop coach. I can answer questions about your sleep, heart rate, activity, and recovery. What would you like to know?")
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Set up your coach", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "To enable the AI Coach, go to Settings and add your OpenAI API key. Until then, you can explore the dashboards with demo data.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = {}) {
                        Text("Open Settings")
                    }
                }
            }
        }
    }
}
