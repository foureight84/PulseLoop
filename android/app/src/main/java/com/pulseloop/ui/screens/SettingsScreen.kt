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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pulseloop.data.DemoDataSeeder
import com.pulseloop.data.PulseLoopDatabase
import com.pulseloop.notifications.CoachNotifications
import com.pulseloop.settings.ApiKeyStore
import kotlinx.coroutines.launch

/**
 * Ported from SettingsView.swift.
 * Settings screen: API key, model selection, coach toggles, demo data, notifications.
 */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val keyStore = remember { ApiKeyStore(context) }
    val scope = rememberCoroutineScope()

    var apiKey by remember { mutableStateOf(keyStore.apiKey) }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf(keyStore.model) }
    var coachEnabled by remember { mutableStateOf(keyStore.coachEnabled) }
    var webSearch by remember { mutableStateOf(keyStore.webSearchEnabled) }
    var showSeedDialog by remember { mutableStateOf(false) }

    val models = listOf("gpt-5.4", "gpt-4o", "gpt-4o-mini", "o4-mini")

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)

        // Coach section
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Coach", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))

                // API Key
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
                Spacer(Modifier.height(4.dp))
                Text(
                    "Stored securely in Android Keystore. Never leaves your device except to call the model.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))

                // Model selector
                Text("Model", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                models.forEach { model ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedModel == model,
                            onClick = { selectedModel = model; keyStore.model = model }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(model, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))

                // Toggles
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable Coach")
                    Switch(checked = coachEnabled, onCheckedChange = {
                        coachEnabled = it; keyStore.coachEnabled = it
                    })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Web Search")
                        Text("Uses additional tokens", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = webSearch, onCheckedChange = {
                        webSearch = it; keyStore.webSearchEnabled = it
                    })
                }
            }
        }

        // Notifications
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Notifications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { CoachNotifications.schedule(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Notifications, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Enable Daily Check-ins")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { CoachNotifications.cancel(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Disable Check-ins")
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
