package com.pulseloop.service

import com.pulseloop.data.PulseLoopDatabase
import com.pulseloop.data.entity.*
import com.pulseloop.ring.*
import kotlinx.coroutines.*

/**
 * Ported from [EventPersistenceSubscriber] in PulseEventBus.swift.
 * Subscribes to PulseEventBus and persists ring data to Room.
 */
class EventPersistenceSubscriber(
    private val db: PulseLoopDatabase,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch {
            PulseEventBus.events.collect { event -> persist(event) }
        }
    }

    fun stop() { job?.cancel(); job = null }

    private suspend fun persist(event: PulseEvent) {
        when (event) {
            is PulseEvent.DeviceStateChanged -> {
                val device = db.deviceDao().current() ?: DeviceEntity()
                val state = when (event.state) {
                    RingConnectionState.CONNECTED -> "CONNECTED"
                    RingConnectionState.DISCONNECTED -> "DISCONNECTED"
                    RingConnectionState.CONNECTING -> "CONNECTING"
                    RingConnectionState.SCANNING -> "SCANNING"
                    RingConnectionState.RECONNECTING -> "RECONNECTING"
                    RingConnectionState.FAILED -> "FAILED"
                    else -> "IDLE"
                }
                db.deviceDao().upsert(device.copy(
                    stateRaw = state,
                    bleAddressHint = event.address ?: device.bleAddressHint,
                    lastConnectedAt = if (state == "CONNECTED") System.currentTimeMillis() else device.lastConnectedAt,
                    updatedAt = System.currentTimeMillis(),
                ))
            }
            is PulseEvent.DeviceIdentified -> {
                val device = db.deviceDao().current() ?: DeviceEntity()
                db.deviceDao().upsert(device.copy(
                    deviceTypeRaw = event.deviceType.name,
                    capabilitiesRaw = event.capabilities.toCsv(),
                    updatedAt = System.currentTimeMillis(),
                ))
            }
            is PulseEvent.BatteryLevel -> {
                val device = db.deviceDao().current() ?: DeviceEntity()
                db.deviceDao().upsert(device.copy(
                    batteryPercent = event.percent,
                    updatedAt = System.currentTimeMillis(),
                ))
            }
            is PulseEvent.HeartRateSample -> {
                db.measurementDao().insert(MeasurementEntity(
                    kindRaw = MeasurementKind.HEART_RATE.name,
                    value = event.bpm.toDouble(), unit = "bpm",
                    timestamp = event.timestamp.toEpochMilli(),
                    sourceRaw = "live",
                ))
            }
            is PulseEvent.Spo2Result -> {
                db.measurementDao().insert(MeasurementEntity(
                    kindRaw = MeasurementKind.SPO2.name,
                    value = event.value.toDouble(), unit = "%",
                    timestamp = event.timestamp.toEpochMilli(),
                    sourceRaw = "live",
                ))
            }
            is PulseEvent.HistoryMeasurement -> {
                db.measurementDao().insert(MeasurementEntity(
                    kindRaw = event.kind.name,
                    value = event.value, unit = event.kind.unit,
                    timestamp = event.timestamp.toEpochMilli(),
                    sourceRaw = "history",
                ))
            }
            is PulseEvent.StressSample -> {
                db.measurementDao().insert(MeasurementEntity(
                    kindRaw = MeasurementKind.STRESS.name,
                    value = event.value.toDouble(), unit = "",
                    timestamp = event.timestamp.toEpochMilli(),
                    sourceRaw = "colmi",
                ))
            }
            is PulseEvent.HrvSample -> {
                db.measurementDao().insert(MeasurementEntity(
                    kindRaw = MeasurementKind.HRV.name,
                    value = event.value.toDouble(), unit = "ms",
                    timestamp = event.timestamp.toEpochMilli(),
                    sourceRaw = "colmi",
                ))
            }
            is PulseEvent.TemperatureSample -> {
                db.measurementDao().insert(MeasurementEntity(
                    kindRaw = MeasurementKind.TEMPERATURE.name,
                    value = event.celsius, unit = "°C",
                    timestamp = event.timestamp.toEpochMilli(),
                    sourceRaw = "colmi",
                ))
            }
            is PulseEvent.ActivityUpdate -> {
                upsertActivityDaily(event.timestamp.toEpochMilli(), event.steps, event.calories, event.distanceMeters)
            }
            is PulseEvent.ActivityBucket -> {
                upsertActivityDaily(event.timestamp.toEpochMilli(), event.steps, 0.0, event.distanceMeters)
            }
            is PulseEvent.SleepTimeline -> {
                upsertSleepSession(event.timestamp.toEpochMilli(), event.stages)
            }
            is PulseEvent.SyncProgress -> {} // UI feedback, no persistence needed
            is PulseEvent.HeartRateComplete -> {}
            is PulseEvent.RawPacket -> {}
            is PulseEvent.ActivitySyncReset -> {}
        }
    }

    private suspend fun upsertActivityDaily(ts: Long, steps: Int, calories: Double, distanceM: Double) {
        val dayStart = java.time.Instant.ofEpochMilli(ts).truncatedTo(java.time.temporal.ChronoUnit.DAYS).toEpochMilli()
        val existing = db.activityDailyDao().byDay(dayStart)
        if (existing != null) {
            db.activityDailyDao().upsert(existing.copy(
                steps = maxOf(existing.steps, steps),
                calories = maxOf(existing.calories, calories),
                distanceMeters = maxOf(existing.distanceMeters, distanceM),
                updatedAt = System.currentTimeMillis(),
            ))
        } else {
            db.activityDailyDao().upsert(ActivityDailyEntity(
                date = dayStart, steps = steps, calories = calories,
                distanceMeters = distanceM, source = "ring",
            ))
        }
    }

    private suspend fun upsertSleepSession(ts: Long, stages: List<SleepStage>) {
        val dayStart = java.time.Instant.ofEpochMilli(ts).truncatedTo(java.time.temporal.ChronoUnit.DAYS).toEpochMilli()
        val existing = db.sleepSessionDao().byDay(dayStart)
        val totalMin = stages.size
        if (existing != null) {
            db.sleepSessionDao().upsert(existing.copy(
                endAt = maxOf(existing.endAt, ts + totalMin * 60_000L),
                totalMinutes = maxOf(existing.totalMinutes, totalMin),
                updatedAt = System.currentTimeMillis(),
            ))
        } else {
            db.sleepSessionDao().upsert(SleepSessionEntity(
                date = dayStart, startAt = ts, endAt = ts + totalMin * 60_000L,
                totalMinutes = totalMin,
            ))
        }
    }
}

// Extension for Set<WearableCapability> CSV from WearableCapability.kt
private fun Set<WearableCapability>.toCsv(): String =
    WearableCapability.entries.filter { it in this }.joinToString(",") { it.key }
