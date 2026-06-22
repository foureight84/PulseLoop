package com.pulseloop.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulseloop.data.PulseLoopDatabase
import com.pulseloop.data.entity.*
import com.pulseloop.ring.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * TodayViewModel — reads Room data for the Today dashboard.
 * Ported from MetricsService.buildTodaySummary in PulseServices.swift.
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
        val batteryPercent: Int = 0,
        val deviceState: String = "idle",
        val isConnected: Boolean = false,
        val sleepMinutes: Int? = null,
        val sleepScore: Int? = null,
    )

    private val _state = MutableStateFlow(TodayState())
    val state: StateFlow<TodayState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Observe activity for today
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
                ) }
            }
        }
        viewModelScope.launch {
            // Latest HR
            kotlinx.coroutines.delay(500)
            val hr = db.measurementDao().latest(MeasurementKind.HEART_RATE.name)
            _state.update { it.copy(heartRate = hr?.toInt()) }
        }
        viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            val spo2 = db.measurementDao().latest(MeasurementKind.SPO2.name)
            _state.update { it.copy(spo2 = spo2?.toInt()) }
        }
    }
}

/**
 * SleepViewModel — reads Room data for the Sleep screen.
 */
class SleepViewModel(db: PulseLoopDatabase) : ViewModel() {
    data class SleepState(
        val lastNight: SleepSessionEntity? = null,
        val recentSessions: List<SleepSessionEntity> = emptyList(),
    )

    private val _state = MutableStateFlow(SleepState())
    val state: StateFlow<SleepState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            db.sleepSessionDao().recentFlow(7).collect { sessions ->
                _state.update { it.copy(
                    lastNight = sessions.firstOrNull(),
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
 */
class VitalsViewModel(db: PulseLoopDatabase) : ViewModel() {
    private val now = System.currentTimeMillis()
    private val twentyFourHoursAgo = now - 24 * 3600_000L

    data class VitalsState(
        val hrSamples: List<Double> = emptyList(),
        val spo2Samples: List<Double> = emptyList(),
        val hrvSamples: List<Double> = emptyList(),
        val stressSamples: List<Double> = emptyList(),
        val tempSamples: List<Double> = emptyList(),
        val latestHr: Int? = null,
        val latestSpo2: Int? = null,
        val latestHrv: Double? = null,
        val latestStress: Double? = null,
        val latestTemp: Double? = null,
        val supportsHrv: Boolean = false,
        val supportsStress: Boolean = false,
        val supportsTemp: Boolean = false,
    )

    private val _state = MutableStateFlow(VitalsState())
    val state: StateFlow<VitalsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val device = db.deviceDao().current()
            val caps = device?.capabilities ?: setOf(
                com.pulseloop.ring.WearableCapability.HEART_RATE,
                com.pulseloop.ring.WearableCapability.SPO2,
                com.pulseloop.ring.WearableCapability.STEPS,
                com.pulseloop.ring.WearableCapability.SLEEP,
                com.pulseloop.ring.WearableCapability.BATTERY,
            )

            // HR
            val hr = db.measurementDao().range(MeasurementKind.HEART_RATE.name, twentyFourHoursAgo, now)
            val hrVals = hr.map { it.value }
            val latestHr = hr.lastOrNull()?.value?.toInt()

            // SpO2
            val spo2 = db.measurementDao().range(MeasurementKind.SPO2.name, twentyFourHoursAgo, now)
            val spo2Vals = spo2.map { it.value }
            val latestSpo2 = spo2.lastOrNull()?.value?.toInt()

            // HRV
            val hrv = if (caps.contains(com.pulseloop.ring.WearableCapability.HRV) || caps.isEmpty()) {
                db.measurementDao().range(MeasurementKind.HRV.name, twentyFourHoursAgo, now)
            } else emptyList()
            val hrvVals = hrv.map { it.value }

            // Stress
            val stress = if (caps.contains(com.pulseloop.ring.WearableCapability.STRESS) || caps.isEmpty()) {
                db.measurementDao().range(MeasurementKind.STRESS.name, twentyFourHoursAgo, now)
            } else emptyList()
            val stressVals = stress.map { it.value }

            // Temperature
            val temp = if (caps.contains(com.pulseloop.ring.WearableCapability.TEMPERATURE) || caps.isEmpty()) {
                db.measurementDao().range(MeasurementKind.TEMPERATURE.name, twentyFourHoursAgo, now)
            } else emptyList()
            val tempVals = temp.map { it.value }

            _state.value = VitalsState(
                hrSamples = hrVals,
                spo2Samples = spo2Vals,
                hrvSamples = hrvVals,
                stressSamples = stressVals,
                tempSamples = tempVals,
                latestHr = latestHr,
                latestSpo2 = latestSpo2,
                latestHrv = hrv.lastOrNull()?.value,
                latestStress = stress.lastOrNull()?.value,
                latestTemp = temp.lastOrNull()?.value,
                supportsHrv = caps.isEmpty() || caps.contains(com.pulseloop.ring.WearableCapability.HRV),
                supportsStress = caps.isEmpty() || caps.contains(com.pulseloop.ring.WearableCapability.STRESS),
                supportsTemp = caps.isEmpty() || caps.contains(com.pulseloop.ring.WearableCapability.TEMPERATURE),
            )
        }
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
