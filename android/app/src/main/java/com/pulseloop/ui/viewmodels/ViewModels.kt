package com.pulseloop.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulseloop.data.PulseLoopDatabase
import com.pulseloop.data.entity.*
import com.pulseloop.ring.*
import com.pulseloop.service.SleepCoach
import com.pulseloop.service.SleepInsights
import com.pulseloop.service.SleepScore
import com.pulseloop.service.SleepScoreResult
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * TodayViewModel — reads Room data for the Today dashboard.
 * Ported from MetricsService.buildTodaySummary in PulseServices.swift.
 * Uses reactive Flow queries so live ring data appears immediately.
 */
class TodayViewModel(db: PulseLoopDatabase) : ViewModel() {
    private val todayStart = Instant.now().truncatedTo(ChronoUnit.DAYS).toEpochMilli()

    data class TodayState(
        val steps: Int? = null,
        val calories: Double? = null,
        val distanceMeters: Double? = null,
        val activeMinutes: Int? = null,
        val heartRate: Int? = null,
        val spo2: Int? = null,
        val restingHR: Double? = null,
        val bloodPressureSystolic: Int? = null,
        val bloodPressureDiastolic: Int? = null,
        val bloodSugar: Double? = null,
        val supportsBP: Boolean = false,
        val supportsGlucose: Boolean = false,
        val batteryPercent: Int = 0,
        val deviceState: String = "idle",
        val isConnected: Boolean = false,
        val sleepMinutes: Int? = null,
        val sleepScore: Int? = null,
        val lastUpdated: Long = 0L,
    )

    private val _state = MutableStateFlow(TodayState())
    val state: StateFlow<TodayState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            db.activityDailyDao().byDayFlow(todayStart).collect { activity ->
                _state.update { it.copy(
                    steps = activity?.steps,
                    calories = activity?.calories,
                    distanceMeters = activity?.distanceMeters,
                    activeMinutes = activity?.activeMinutes,
                ) }
            }
        }
        viewModelScope.launch {
            db.deviceDao().currentFlow().collect { device ->
                _state.update { it.copy(
                    batteryPercent = device?.batteryPercent ?: 0,
                    deviceState = device?.stateRaw ?: "idle",
                    isConnected = device?.stateRaw == "CONNECTED",
                    supportsBP = device?.capabilities?.contains(WearableCapability.BLOOD_PRESSURE) ?: false,
                    supportsGlucose = device?.capabilities?.contains(WearableCapability.BLOOD_SUGAR) ?: false,
                ) }
            }
        }
        // Reactive HR — poll latest every 2s, resilient to DB errors
        viewModelScope.launch {
            while (true) {
                try {
                    val hr = db.measurementDao().latest(MeasurementKind.HEART_RATE.name)
                    _state.update { it.copy(heartRate = hr?.toInt(), lastUpdated = System.currentTimeMillis()) }
                } catch (_: Exception) {}
                kotlinx.coroutines.delay(2000)
            }
        }
        // Reactive SpO2 — poll latest every 2s
        viewModelScope.launch {
            while (true) {
                try {
                    val spo2 = db.measurementDao().latest(MeasurementKind.SPO2.name)
                    _state.update { it.copy(spo2 = spo2?.toInt(), lastUpdated = System.currentTimeMillis()) }
                } catch (_: Exception) {}
                kotlinx.coroutines.delay(2000)
            }
        }
        // Reactive BP — poll latest every 5s
        viewModelScope.launch {
            while (true) {
                try {
                    val sys = db.measurementDao().latest(MeasurementKind.BLOOD_PRESSURE_SYSTOLIC.name)
                    val dia = db.measurementDao().latest(MeasurementKind.BLOOD_PRESSURE_DIASTOLIC.name)
                    _state.update { it.copy(bloodPressureSystolic = sys?.toInt(), bloodPressureDiastolic = dia?.toInt(), lastUpdated = System.currentTimeMillis()) }
                } catch (_: Exception) {}
                kotlinx.coroutines.delay(5000)
            }
        }
        // Reactive Glucose — poll latest every 5s
        viewModelScope.launch {
            while (true) {
                try {
                    val glucose = db.measurementDao().latest(MeasurementKind.BLOOD_SUGAR.name)
                    _state.update { it.copy(bloodSugar = glucose, lastUpdated = System.currentTimeMillis()) }
                } catch (_: Exception) {}
                kotlinx.coroutines.delay(5000)
            }
        }
    }
}

/**
 * SleepViewModel — reads Room data for the Sleep screen.
 */
class SleepViewModel(db: PulseLoopDatabase) : ViewModel() {
    data class SleepState(
        val lastNight: SleepSessionEntity? = null,
        val lastNightBlocks: List<SleepStageBlockEntity> = emptyList(),
        val score: SleepScoreResult? = null,
        val coach: SleepCoach? = null,
        val recentSessions: List<SleepSessionEntity> = emptyList(),
    )

    private val _state = MutableStateFlow(SleepState())
    val state: StateFlow<SleepState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            db.sleepSessionDao().recentFlow(7).collect { sessions ->
                val last = sessions.firstOrNull()
                val blocks = if (last != null) db.sleepStageBlockDao().forSession(last.id) else emptyList()
                val scoreResult = if (last != null) SleepScore.calculate(last, blocks) else null
                val coachText = if (last != null && scoreResult != null) {
                    SleepInsights.dayCoach(last, blocks, null)
                } else if (last == null) {
                    SleepInsights.dayNoDataCoach
                } else null
                _state.update { it.copy(
                    lastNight = last,
                    lastNightBlocks = blocks,
                    score = scoreResult,
                    coach = coachText,
                    recentSessions = sessions,
                ) }
            }
        }
    }
}

/**
 * ActivityViewModel — reads Room data for the Activity screen.
 */
class ActivityViewModel(db: PulseLoopDatabase) : ViewModel() {
    data class ActivityState(
        val recentDays: List<ActivityDailyEntity> = emptyList(),
        val recentWorkouts: List<ActivitySessionEntity> = emptyList(),
    )

    private val _state = MutableStateFlow(ActivityState())
    val state: StateFlow<ActivityState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            db.activityDailyDao().recentFlow(7).collect { days ->
                _state.update { it.copy(recentDays = days) }
            }
        }
        viewModelScope.launch {
            db.activitySessionDao().recentFlow(5).collect { sessions ->
                _state.update { it.copy(recentWorkouts = sessions) }
            }
        }
    }
}

/**
 * VitalsViewModel — reads Room data for the Vitals screen.
 * Ported from MetricsService.metricRange in PulseServices.swift.
 * Uses reactive polling so data appears as soon as the ring syncs.
 */
class VitalsViewModel(db: PulseLoopDatabase) : ViewModel() {
    data class VitalsState(
        val hrSamples: List<Double> = emptyList(),
        val spo2Samples: List<Double> = emptyList(),
        val hrvSamples: List<Double> = emptyList(),
        val stressSamples: List<Double> = emptyList(),
        val fatigueSamples: List<Double> = emptyList(),
        val tempSamples: List<Double> = emptyList(),
        val latestHr: Int? = null,
        val latestSpo2: Int? = null,
        val latestHrv: Double? = null,
        val latestStress: Double? = null,
        val latestFatigue: Double? = null,
        val latestTemp: Double? = null,
        val bpSystolic: Int? = null,
        val bpDiastolic: Int? = null,
        val bloodSugar: Double? = null,
        val supportsHrv: Boolean = false,
        val supportsStress: Boolean = false,
        val supportsFatigue: Boolean = false,
        val supportsTemp: Boolean = false,
        val supportsBP: Boolean = false,
        val supportsGlucose: Boolean = false,
    )

    private val _state = MutableStateFlow(VitalsState())
    val state: StateFlow<VitalsState> = _state.asStateFlow()

    init {
        // Poll every 5 seconds so data appears as the ring syncs history
        viewModelScope.launch {
            while (true) {
                try { refresh(db) } catch (_: Exception) {}
                kotlinx.coroutines.delay(5000)
            }
        }
    }

    private suspend fun refresh(db: PulseLoopDatabase) {
        val now = System.currentTimeMillis()
        val twentyFourHoursAgo = now - 24 * 3600_000L
        val device = db.deviceDao().current()
        val caps = device?.capabilities ?: setOf(
            com.pulseloop.ring.WearableCapability.HEART_RATE,
            com.pulseloop.ring.WearableCapability.SPO2,
            com.pulseloop.ring.WearableCapability.STEPS,
            com.pulseloop.ring.WearableCapability.SLEEP,
            com.pulseloop.ring.WearableCapability.BATTERY,
        )

        val hr = db.measurementDao().range(MeasurementKind.HEART_RATE.name, twentyFourHoursAgo, now)
        val spo2 = db.measurementDao().range(MeasurementKind.SPO2.name, twentyFourHoursAgo, now)
        val hrv = if (caps.contains(WearableCapability.HRV)) db.measurementDao().range(MeasurementKind.HRV.name, twentyFourHoursAgo, now) else emptyList()
        val stress = if (caps.contains(WearableCapability.STRESS)) db.measurementDao().range(MeasurementKind.STRESS.name, twentyFourHoursAgo, now) else emptyList()
        val fatigue = if (caps.contains(WearableCapability.FATIGUE)) db.measurementDao().range(MeasurementKind.FATIGUE.name, twentyFourHoursAgo, now) else emptyList()
        val temp = if (caps.contains(WearableCapability.TEMPERATURE)) db.measurementDao().range(MeasurementKind.TEMPERATURE.name, twentyFourHoursAgo, now) else emptyList()

        _state.value = VitalsState(
            hrSamples = hr.map { it.value },
            spo2Samples = spo2.map { it.value },
            hrvSamples = hrv.map { it.value },
            stressSamples = stress.map { it.value },
            fatigueSamples = fatigue.map { it.value },
            tempSamples = temp.map { it.value },
            latestHr = hr.lastOrNull()?.value?.toInt(),
            latestSpo2 = spo2.lastOrNull()?.value?.toInt(),
            latestHrv = hrv.lastOrNull()?.value,
            latestStress = stress.lastOrNull()?.value,
            latestFatigue = fatigue.lastOrNull()?.value,
            latestTemp = temp.lastOrNull()?.value,
            bpSystolic = db.measurementDao().latest(MeasurementKind.BLOOD_PRESSURE_SYSTOLIC.name)?.toInt(),
            bpDiastolic = db.measurementDao().latest(MeasurementKind.BLOOD_PRESSURE_DIASTOLIC.name)?.toInt(),
            bloodSugar = db.measurementDao().latest(MeasurementKind.BLOOD_SUGAR.name),
            supportsHrv = caps.contains(WearableCapability.HRV),
            supportsStress = caps.contains(WearableCapability.STRESS),
            supportsFatigue = caps.contains(WearableCapability.FATIGUE),
            supportsTemp = caps.contains(WearableCapability.TEMPERATURE),
            supportsBP = caps.contains(WearableCapability.BLOOD_PRESSURE),
            supportsGlucose = caps.contains(WearableCapability.BLOOD_SUGAR),
        )
    }
}

/**
 * CoachViewModel — wires the coach orchestrator to the UI.
 */
class CoachViewModel(
    private val db: PulseLoopDatabase,
    private val orchestrator: com.pulseloop.coach.orchestration.CoachOrchestrator,
) : ViewModel() {
    data class CoachState(
        val messages: List<ChatMessage> = emptyList(),
        val isThinking: Boolean = false,
        val error: String? = null,
    )

    data class ChatMessage(val role: String, val text: String)

    private val _state = MutableStateFlow(CoachState(
        messages = listOf(ChatMessage("assistant",
            "Hi! I'm your PulseLoop coach. I can answer questions about your sleep, heart rate, activity, and recovery. What would you like to know?"))
    ))
    val state: StateFlow<CoachState> = _state.asStateFlow()

    fun sendMessage(userText: String) {
        _state.update { it.copy(
            messages = it.messages + ChatMessage("user", userText),
            isThinking = true, error = null,
        ) }
        viewModelScope.launch {
            try {
                val packet = com.pulseloop.coach.context.CoachContextBuilder.build(db)
                val priorMessages = _state.value.messages.dropLast(1).map {
                    com.pulseloop.coach.orchestration.CoachOrchestrator.PriorMessage(it.role, it.text)
                }
                val result = orchestrator.runTurn(userText, packet, priorMessages)
                _state.update { it.copy(
                    messages = it.messages + ChatMessage("assistant", result.assistant.plainText),
                    isThinking = false,
                ) }
            } catch (e: Exception) {
                _state.update { it.copy(
                    messages = it.messages + ChatMessage("assistant", "Sorry, something went wrong: ${e.message}"),
                    isThinking = false, error = e.message,
                ) }
            }
        }
    }
}
