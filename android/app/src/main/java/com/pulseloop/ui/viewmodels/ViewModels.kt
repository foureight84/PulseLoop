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
                val packet = com.pulseloop.coach.context.CoachContextPacket(
                    today = java.time.LocalDate.now().toString(),
                    timezone = java.time.ZoneId.systemDefault().id,
                )
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
