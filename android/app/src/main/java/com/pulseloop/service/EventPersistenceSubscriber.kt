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

    /**
     * Compose the firmware string the way the official Jring app does in
     * onGetDeviceInfo(): `<cid><did>V<version>` e.g. "003A002AV138" — where the
     * hex prefix comes from the 0x0C device-info packet and the version from 0xF6.
     *
     * These two pieces arrive in separate BLE notifications in either order, so this
     * merges a new piece into whatever is already stored without dropping the other.
     * A non-hex string already present (e.g. a DIS 2A26 fallback) is treated as empty
     * so it can't corrupt the prefix. The Settings screen renders only the trailing
     * "V<version>" portion, matching the official app's `substring(len-4)` display.
     */
    private fun composeFirmware(existing: String?, hex: String? = null, version: Int? = null): String {
        fun looksHex(s: String) = s.isNotEmpty() && s.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
        val existingHex = existing?.substringBefore("V", "")?.takeIf { looksHex(it) }
        val existingVer = existing?.substringAfter("V", "")?.takeIf { it.isNotEmpty() }
        val finalHex = hex ?: existingHex
        val finalVer = version?.toString() ?: existingVer
        return buildString {
            finalHex?.let { append(it) }
            finalVer?.let { append("V").append(it) }
        }
    }

    private suspend fun persist(event: PulseEvent) {
        when (event) {
            is PulseEvent.DeviceStateChanged -> {
                val device = db.deviceDao().current() ?: DeviceEntity()
                val state = when (event.state) {
                    RingConnectionState.CONNECTED -> {
                        db.measurementDao().clearDemo()
                        db.activityDailyDao().clearDemo()
                        db.sleepSessionDao().clear()
                        "CONNECTED"
                    }
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
                    firmwareVersion = if (event.firmware != null)
                        composeFirmware(device.firmwareVersion, hex = event.firmware)
                    else device.firmwareVersion,
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
            is PulseEvent.RawPacket -> {
                db.rawPacketDao().insert(RawPacketEntity(
                    directionRaw = event.direction.name,
                    commandId = event.data.getOrNull(0)?.toInt()?.and(0xFF) ?: 0,
                    hexPayload = event.data.joinToString("") { "%02x".format(it) },
                    decodedKind = event.decoded.kind,
                ))
            }
            is PulseEvent.ActivitySyncReset -> {}
            is PulseEvent.FirmwareVersion -> {
                val device = db.deviceDao().current()
                if (device != null && event.version != null) {
                    // Merge the 0xF6 version into the stored string, order-independently,
                    // yielding the official app's "<hex>V<version>" form (e.g. "003A002AV138").
                    val combined = composeFirmware(device.firmwareVersion, version = event.version)
                    db.deviceDao().upsert(device.copy(firmwareVersion = combined, updatedAt = System.currentTimeMillis()))
                }
            }
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
        val sessionId = "sleep-$dayStart"

        // Persist individual stage blocks for hypnogram rendering
        val blocks = buildStageBlocks(sessionId, ts, stages)
        db.sleepStageBlockDao().deleteBySession(sessionId)
        blocks.forEach { db.sleepStageBlockDao().insert(it) }

        val existing = db.sleepSessionDao().byDay(dayStart)
        val totalMin = stages.size
        val deepMin = stages.count { it == SleepStage.DEEP }
        val lightMin = stages.count { it == SleepStage.LIGHT }
        val score = computeSleepScore(deepMin, totalMin)

        if (existing != null) {
            db.sleepSessionDao().upsert(existing.copy(
                endAt = maxOf(existing.endAt, ts + totalMin * 60_000L),
                totalMinutes = maxOf(existing.totalMinutes, totalMin),
                score = score,
                updatedAt = System.currentTimeMillis(),
            ))
        } else {
            db.sleepSessionDao().upsert(SleepSessionEntity(
                id = sessionId,
                date = dayStart,
                startAt = ts,
                endAt = ts + totalMin * 60_000L,
                totalMinutes = totalMin,
                score = score,
            ))
        }
    }

    /**
     * Build SleepStageBlockEntity entries with run-length encoding.
     * Consecutive minutes of the same stage are merged into one block.
     */
    private fun buildStageBlocks(sessionId: String, startTs: Long, stages: List<SleepStage>): List<SleepStageBlockEntity> {
        if (stages.isEmpty()) return emptyList()
        val blocks = mutableListOf<SleepStageBlockEntity>()
        var currentStage = stages[0]
        var blockStart = startTs
        var blockMinute = 0
        var duration = 1

        for (i in 1 until stages.size) {
            val stage = stages[i]
            if (stage == currentStage) {
                duration++
            } else {
                blocks.add(SleepStageBlockEntity(
                    sessionId = sessionId,
                    startAt = blockStart,
                    startMinute = blockMinute,
                    durationMinutes = duration,
                    stageRaw = currentStage.name,
                ))
                currentStage = stage
                blockStart = startTs + i * 60_000L
                blockMinute = i
                duration = 1
            }
        }
        // Final block
        blocks.add(SleepStageBlockEntity(
            sessionId = sessionId,
            startAt = blockStart,
            startMinute = blockMinute,
            durationMinutes = duration,
            stageRaw = currentStage.name,
        ))
        return blocks
    }

    /**
     * Sleep quality score (0-100) based on deep sleep ratio.
     * Medical research: optimal deep sleep = 15-25% of total.
     * Matches the official app's scoring.
     */
    private fun computeSleepScore(deepMin: Int, totalMin: Int): Int? {
        if (totalMin == 0) return null
        val deepPct = (deepMin.toFloat() / totalMin * 100).toInt()
        return when {
            deepPct >= 20 -> 90
            deepPct >= 15 -> 75
            deepPct >= 10 -> 60
            else -> 40
        }
    }
}

// Extension for Set<WearableCapability> CSV from WearableCapability.kt
private fun Set<WearableCapability>.toCsv(): String =
    WearableCapability.entries.filter { it in this }.joinToString(",") { it.key }
