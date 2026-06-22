package com.pulseloop.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pulseloop.data.DemoDataSeeder
import com.pulseloop.data.PulseLoopDatabase
import com.pulseloop.notifications.CoachNotifications
import com.pulseloop.service.RingSyncWorker
import com.pulseloop.settings.ApiKeyStore
import com.pulseloop.settings.UnitSystem
import kotlinx.coroutines.launch

/**
 * Ported from SettingsView.swift + CoachSettingsSection.swift.
 * Settings screen: API key, model selection, provider mode, write tools,
 * live measurements, coach memory list, notifications, demo data.
 */
@Composable
fun SettingsScreen(
    navController: androidx.navigation.NavController? = null,
    bleClient: com.pulseloop.ring.RingBLEClient? = null,
    coordinator: com.pulseloop.service.RingSyncCoordinator? = null,
) {
    val context = LocalContext.current
    val keyStore = remember { ApiKeyStore(context) }
    val scope = rememberCoroutineScope()

    var apiKey by remember { mutableStateOf(keyStore.apiKey) }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf(keyStore.model) }
    var coachEnabled by remember { mutableStateOf(keyStore.coachEnabled) }
    var webSearch by remember { mutableStateOf(keyStore.webSearchEnabled) }
    var writeTools by remember { mutableStateOf(keyStore.writeToolsEnabled) }
    var liveMeasurements by remember { mutableStateOf(keyStore.liveMeasurementsEnabled) }
    var notificationEnabled by remember { mutableStateOf(keyStore.notificationsEnabled) }
    var showSeedDialog by remember { mutableStateOf(false) }
    var showMemory by remember { mutableStateOf(false) }

    // Coach memories — loaded on composition via LaunchedEffect
    val db = remember { PulseLoopDatabase.getInstance(context) }
    var memories by remember { mutableStateOf(emptyList<com.pulseloop.data.entity.CoachMemoryEntity>()) }
    LaunchedEffect(coachEnabled) {
        if (coachEnabled) {
            memories = db.coachMemoryDao().allRanked()
        }
    }

    val models = listOf("gpt-5.4", "gpt-4o", "gpt-4o-mini", "o4-mini")

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)

        // AI Coach section — ported from CoachSettingsSection.swift
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("AI Coach", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (coachEnabled && keyStore.apiKey.isNotBlank()) "Active — ${selectedModel}"
                    else if (coachEnabled) "API key needed"
                    else "Disabled",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))

                // Master toggle
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable AI Coach")
                    Switch(checked = coachEnabled, onCheckedChange = {
                        coachEnabled = it; keyStore.coachEnabled = it
                        if (!it) {
                            CoachNotifications.cancel(context)
                            keyStore.notificationsEnabled = false
                            notificationEnabled = false
                        }
                    })
                }

                if (coachEnabled) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    // Model picker as dropdown
                    Text("Model", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    var modelExpanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { modelExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selectedModel, Modifier.weight(1f))
                            Icon(Icons.Filled.ArrowDropDown, null)
                        }
                        DropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                            models.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model) },
                                    onClick = { selectedModel = model; keyStore.model = model; modelExpanded = false },
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // API Key field (ported from CoachSettingsSection keyField)
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("OpenAI API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (apiKeyVisible) androidx.compose.ui.text.input.VisualTransformation.None
                            else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        trailingIcon = {
                            IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                Icon(
                                    if (apiKeyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = "Toggle visibility"
                                )
                            }
                        },
                        keyboardActions = KeyboardActions(onDone = {
                            keyStore.apiKey = apiKey
                        }),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { keyStore.apiKey = apiKey },
                            enabled = apiKey.isNotBlank(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (keyStore.apiKey.isNotBlank()) "Update Key" else "Save Key")
                        }
                        if (keyStore.apiKey.isNotBlank()) {
                            OutlinedButton(
                                onClick = { apiKey = ""; keyStore.apiKey = "" },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Remove")
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Stored securely in Android Keystore. Never leaves your device except to call the model.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()

                    // Tool toggles
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Web Search")
                            Text("Uses additional tokens", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = webSearch, onCheckedChange = {
                            webSearch = it; keyStore.webSearchEnabled = it
                        })
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Write Actions")
                            Text("Set goals, log, edit data", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = writeTools, onCheckedChange = {
                            writeTools = it; keyStore.writeToolsEnabled = it
                        })
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Live Measurements")
                            Text("Trigger real-time ring readings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = liveMeasurements, onCheckedChange = {
                            liveMeasurements = it; keyStore.liveMeasurementsEnabled = it
                        })
                    }
                }
            }
        }

        // Notifications section — ported from CoachSettingsSection notificationsSection
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Notifications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Daily Check-in Notifications")
                    Switch(checked = notificationEnabled, onCheckedChange = { enabled ->
                        notificationEnabled = enabled
                        keyStore.notificationsEnabled = enabled
                        if (enabled) {
                            CoachNotifications.schedule(context)
                        } else {
                            CoachNotifications.cancel(context)
                        }
                    })
                }

                if (notificationEnabled) {
                    Spacer(Modifier.height(8.dp))
                    // Morning time picker
                    var morningHour by remember { mutableStateOf(keyStore.morningHour) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Morning")
                        var amExpanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { amExpanded = true }) {
                                Text(String.format("%02d:00", morningHour))
                            }
                            DropdownMenu(expanded = amExpanded, onDismissRequest = { amExpanded = false }) {
                                (0..<24).forEach { h ->
                                    DropdownMenuItem(
                                        text = { Text(String.format("%02d:00", h)) },
                                        onClick = { morningHour = h; keyStore.morningHour = h; amExpanded = false },
                                    )
                                }
                            }
                        }
                    }

                    // Evening time picker
                    var eveningHour by remember { mutableStateOf(keyStore.eveningHour) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Evening")
                        var pmExpanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { pmExpanded = true }) {
                                Text(String.format("%02d:00", eveningHour))
                            }
                            DropdownMenu(expanded = pmExpanded, onDismissRequest = { pmExpanded = false }) {
                                (0..<24).forEach { h ->
                                    DropdownMenuItem(
                                        text = { Text(String.format("%02d:00", h)) },
                                        onClick = { eveningHour = h; keyStore.eveningHour = h; pmExpanded = false },
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                CoachNotifications.showNow(
                                    context,
                                    "PulseLoop Coach",
                                    "This is a test check-in notification.",
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Send Test Check-in Now")
                    }
                }
            }
        }

        // Coach Memory — ported from CoachSettingsSection memoryRow
        if (memories.isNotEmpty() && coachEnabled) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Coach Memory", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { showMemory = !showMemory }) {
                            Text(if (showMemory) "Hide" else "Show (${memories.size})")
                        }
                    }
                    if (showMemory) {
                        Spacer(Modifier.height(8.dp))
                        memories.forEach { memory ->
                            Card(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(memory.key, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                        Text(memory.value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                    }
                                    IconButton(onClick = {
                                        scope.launch { db.coachMemoryDao().deleteByKey(memory.key) }
                                    }) {
                                        Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Units", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                var useImperial by remember { mutableStateOf(keyStore.resolvedUnitSystem == UnitSystem.IMPERIAL) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Use Imperial units")
                        Text(
                            if (keyStore.unitSystem == null) "Auto-detected: ${keyStore.resolvedUnitSystem.label}"
                            else "Manual override",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = useImperial, onCheckedChange = {
                        useImperial = it
                        keyStore.unitSystem = if (it) UnitSystem.IMPERIAL.name else UnitSystem.METRIC.name
                    })
                }
                if (keyStore.unitSystem != null) {
                    TextButton(onClick = {
                        keyStore.unitSystem = null
                        useImperial = keyStore.resolvedUnitSystem == UnitSystem.IMPERIAL
                    }) {
                        Text("Reset to auto-detect", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Ring — connection management & unpair
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Ring", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                val device = remember { mutableStateOf<com.pulseloop.data.entity.DeviceEntity?>(null) }
                LaunchedEffect(Unit) {
                    device.value = db.deviceDao().current()
                }
                val isConnected = device.value?.stateRaw == "CONNECTED"
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Bluetooth, null, Modifier.size(18.dp),
                        tint = if (isConnected) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isConnected) "Connected — ${device.value?.name ?: "Ring"} · ${device.value?.batteryPercent ?: 0}%"
                        else device.value?.let { "Last seen: ${it.name}" } ?: "No ring paired",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                // Firmware version
                if (device.value?.firmwareVersion != null) {
                    Text("Firmware: ${device.value!!.firmwareVersion}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                } else if (device.value != null) {
                    Text("Firmware: reading… (connect ring to read)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                }
                if (device.value != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Forget removes the ring from the app and tells it to reset. Disconnect just drops the BLE link — the ring can reconnect.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    // Cancel background sync before unpair
                                    RingSyncWorker.cancel(context)
                                    // Send ring-side unpair commands, then disconnect & clear DB
                                    if (coordinator != null && isConnected) {
                                        coordinator.forgetRing {
                                            scope.launch {
                                                db.deviceDao().clear()
                                                device.value = null
                                            }
                                        }
                                    } else {
                                        bleClient?.forget()
                                        db.deviceDao().clear()
                                        device.value = null
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) {
                            Icon(Icons.Filled.DeleteForever, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Forget Ring")
                        }
                        if (isConnected) {
                            OutlinedButton(
                                onClick = { scope.launch { bleClient?.disconnect() } },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Disconnect")
                            }
                        }
                    }
                }
            }
        }

        // Demo data
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Demo Data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Explore the app without a ring. Seeds 7 days of activity, HR, SpO2, sleep, and a coach conversation.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { showSeedDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Reseed Demo Data")
                }
            }
        }

        // Developer
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Developer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text("View raw ring data, BLE packets, database stats, and export diagnostics.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { navController?.navigate("debug") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.BugReport, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open Debug View")
                }
            }
        }

        // About
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("About", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text("PulseLoop v1.0.0", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Open-source health tracker. Ported from iOS.\nCC BY 4.0 · github.com/foureight84/PulseLoop",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(32.dp))
    }

    // Seed confirmation dialog
    if (showSeedDialog) {
        AlertDialog(
            onDismissRequest = { showSeedDialog = false },
            title = { Text("Reseed Demo Data?") },
            text = { Text("This will replace all existing demo data. Your synced ring data will not be affected.") },
            confirmButton = {
                TextButton(onClick = {
                    showSeedDialog = false
                    scope.launch {
                        DemoDataSeeder.seed(PulseLoopDatabase.getInstance(context))
                    }
                }) { Text("Reseed") }
            },
            dismissButton = {
                TextButton(onClick = { showSeedDialog = false }) { Text("Cancel") }
            },
        )
    }
}
