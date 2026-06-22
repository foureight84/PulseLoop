package com.pulseloop.diagnostics

import com.pulseloop.data.PulseLoopDatabase
import com.pulseloop.data.entity.WearableLogCategory
import com.pulseloop.data.entity.WearableLogEntity
import com.pulseloop.data.entity.WearableLogLevel
import com.pulseloop.ring.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json

/**
 * Ported from DiagnosticsSubscriber.swift.
 * Subscribes to PulseEventBus and records high-level connection/sync/battery/error
 * events into the structured WearableLog store.
 */
class DiagnosticsSubscriber(
    private val db: PulseLoopDatabase,
    private val eventBus: PulseEventBus,
) {
    private var job: Job? = null
    private var activeDeviceType: RingDeviceType? = null

    fun start(scope: CoroutineScope) {
        if (job != null) return
        job = scope.launch {
            val stream = eventBus.events()
            stream.collect { event ->
                record(event)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun record(event: PulseEvent) {
        when (event) {
            is PulseEvent.DeviceStateChanged -> {
                log(WearableLogCategory.CONNECTION, WearableLogLevel.INFO, "Connection state: ${event.state.name}")
            }
            is PulseEvent.DeviceIdentified -> {
                activeDeviceType = event.deviceType
                log(WearableLogCategory.CONNECTION, WearableLogLevel.INFO,
                    "Identified ${event.deviceType.displayName}",
                    mapOf("capabilities" to event.capabilities.joinToString(",") { it.key }),
                )
            }
            is PulseEvent.BatteryLevel -> {
                log(WearableLogCategory.BATTERY, WearableLogLevel.INFO, "Battery ${event.percent}%")
            }
            is PulseEvent.SyncProgress -> {
                log(WearableLogCategory.SYNC, WearableLogLevel.INFO, "Sync: ${event.stage}")
            }
            is PulseEvent.HeartRateComplete -> {
                log(WearableLogCategory.SYNC, WearableLogLevel.INFO, "Heart-rate measurement complete")
            }
            // Spo2Result indicates completion
            is PulseEvent.Spo2Result -> {
                log(WearableLogCategory.SYNC, WearableLogLevel.INFO, "SpO₂ measurement complete")
            }
            else -> { /* not logged */ }
        }
    }

    private suspend fun log(
        category: WearableLogCategory,
        level: WearableLogLevel,
        message: String,
        metadata: Map<String, String>? = null,
    ) {
        val json = metadata?.let {
            kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.builtins.MapSerializer(
                    kotlinx.serialization.builtins.serializer<String>(),
                    kotlinx.serialization.builtins.serializer<String>(),
                ), it
            )
        }
        db.wearableLogDao().insert(WearableLogEntity(
            deviceTypeRaw = activeDeviceType?.name ?: "",
            categoryRaw = category.name,
            levelRaw = level.name,
            message = message,
            metadataJSON = json,
        ))
    }
}
