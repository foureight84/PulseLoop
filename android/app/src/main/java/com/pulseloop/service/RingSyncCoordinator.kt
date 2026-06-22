package com.pulseloop.service

import com.pulseloop.data.PulseLoopDatabase
import com.pulseloop.data.entity.DeviceEntity
import com.pulseloop.data.entity.MeasurementEntity
import com.pulseloop.data.entity.UserGoalEntity
import com.pulseloop.ring.*
import kotlinx.coroutines.*

/**
 * Ported from [RingSyncCoordinator] in RingSyncCoordinator.swift.
 * High-level orchestration of ring command flows. Subscribes to PulseEventBus to
 * track latest measurement values and expose app-facing measurement actions.
 */
class RingSyncCoordinator(
    private val client: RingBLEClient,
    private val db: PulseLoopDatabase,
) {
    enum class MeasureState { IDLE, MEASURING, DONE, FAILED }

    var hrState: MeasureState = MeasureState.IDLE
        private set
    var spo2State: MeasureState = MeasureState.IDLE
        private set
    var lastSyncAt: Long? = null
        private set

    /** Latest live HR bpm, mirrored for UI without a query. */
    var latestHRValue: Int? = null
        private set
    /** Latest live SpO2 %, mirrored for UI without a query. */
    var latestSpO2Value: Int? = null
        private set

    var workoutHRActive = false
        private set
    private var hrNoReadingReported = false
    private var measurementReceivedReading = false

    val connectionState: RingConnectionState get() = client.state.value.connectionState
    val isConnected: Boolean get() = connectionState == RingConnectionState.CONNECTED

    private val hrMeasureSeconds = 30L
    private val hrSettleSeconds = 4
    private val spo2MeasureSeconds = 40L

    private val engine: RingSyncEngine? get() = client.syncEngine
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var streamJob: Job? = null

    fun start() {
        streamJob?.cancel()
        streamJob = null
        streamJob = scope.launch {
            PulseEventBus.events.collect { event -> handle(event) }
        }
    }

    fun stop() {
        streamJob?.cancel()
        streamJob = null
    }

    // MARK: - Actions

    /** Canonical startup sequence run on connect. */
    fun runStartupSequence() {
        engine?.runStartup()
        lastSyncAt = System.currentTimeMillis()
    }

    fun syncNow() {
        if (!isConnected) return
        runStartupSequence()
    }

    /** Pull-to-refresh entry point. */
    suspend fun pullToRefresh() {
        if (isConnected) {
            runStartupSequence()
        } else if (client.state.value.activeDeviceType != null) {
            client.connectLastKnown()
        } else {
            client.startScanning()
        }
        delay(1200)
    }

    // MARK: - Workout HR streaming

    fun startWorkoutHeartRate() {
        if (!isConnected) return
        engine?.startHeartRate()
        workoutHRActive = true
    }

    fun stopWorkoutHeartRate() {
        if (!workoutHRActive) return
        engine?.stopHeartRate()
        workoutHRActive = false
    }

    fun querySleep() {
        if (!isConnected) return
        engine?.runStartup()
    }

    fun findRing() {
        if (!isConnected) return
        engine?.findDevice()
    }

    /** Send ring-side unpair commands (power-off, factory reset if supported),
     *  then disconnect and forget. */
    fun forgetRing(onCleared: () -> Unit) {
        val caps = client.state.value.activeCapabilities
        if (caps.contains(WearableCapability.POWER_OFF)) {
            engine?.powerOff()
        }
        if (caps.contains(WearableCapability.FACTORY_RESET)) {
            engine?.factoryReset()
        }
        // Give the ring a moment to process, then disconnect + forget
        scope.launch {
            kotlinx.coroutines.delay(500)
            client.forget()
            stop()
            onCleared()
        }
    }

    fun setGoal(steps: Int) {
        if (isConnected) engine?.setGoal(steps)
        scope.launch {
            val goal = db.userGoalDao().get()
            if (goal != null) {
                db.userGoalDao().upsert(goal.copy(steps = steps, updatedAt = System.currentTimeMillis()))
            } else {
                db.userGoalDao().upsert(UserGoalEntity(steps = steps))
            }
        }
    }

    // MARK: - Spot measurements

    suspend fun measureHR(): Int? {
        if (hrState == MeasureState.MEASURING) return null
        if (!isConnected) { hrState = MeasureState.FAILED; return null }
        hrState = MeasureState.MEASURING
        hrNoReadingReported = false
        measurementReceivedReading = false

        engine?.measureHeartRateSpot()
        pollForValue(hrMeasureSeconds, { if (measurementReceivedReading) latestHRValue else null }, { hrNoReadingReported })

        var result = if (measurementReceivedReading) latestHRValue else null
        if (result != null) {
            repeat(hrSettleSeconds * 2) {   // 0.5s granularity
                delay(500)
                latestHRValue?.let { result = it }
            }
        }
        engine?.stopHeartRate()
        hrState = if (result != null) MeasureState.DONE else MeasureState.FAILED
        return result
    }

    suspend fun measureSpO2(): Int? {
        if (spo2State == MeasureState.MEASURING) return null
        if (!isConnected) { spo2State = MeasureState.FAILED; return null }
        spo2State = MeasureState.MEASURING
        latestSpO2Value = null
        engine?.startSpO2()
        val result = pollForValue(spo2MeasureSeconds, { latestSpO2Value }, { false })
        engine?.stopSpO2()
        spo2State = if (result != null) MeasureState.DONE else MeasureState.FAILED
        return result
    }

    private suspend fun pollForValue(
        windowSec: Long,
        value: () -> Int?,
        abort: () -> Boolean,
    ): Int? {
        val steps = (windowSec * 2).toInt()
        repeat(steps) {
            value()?.let { return it }
            if (abort()) return null
            delay(500)
        }
        return value()
    }

    // MARK: - Event handling

    private fun handle(event: PulseEvent) {
        when (event) {
            is PulseEvent.HeartRateSample -> {
                latestHRValue = event.bpm
                if (hrState == MeasureState.MEASURING) measurementReceivedReading = true
            }
            is PulseEvent.HeartRateComplete -> {
                if (hrState == MeasureState.MEASURING && !measurementReceivedReading) {
                    hrNoReadingReported = true
                }
            }
            is PulseEvent.Spo2Result -> {
                latestSpO2Value = event.value
            }
            is PulseEvent.DeviceStateChanged -> {
                if (event.state == RingConnectionState.CONNECTED) {
                    lastSyncAt = System.currentTimeMillis()
                }
            }
            else -> {}
        }
    }
}
