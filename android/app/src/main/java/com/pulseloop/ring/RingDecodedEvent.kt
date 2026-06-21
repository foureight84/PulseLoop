package com.pulseloop.ring

import java.time.Instant

/**
 * Ported from [MeasurementKind] in PulseModels.swift.
 */
enum class MeasurementKind(val key: String, val unit: String) {
    HEART_RATE("hr", "bpm"),
    SPO2("spo2", "%"),
    STRESS("stress", ""),
    HRV("hrv", "ms"),
    TEMPERATURE("temp", "°C");
}

/**
 * Ported from [SleepStage] in PulseModels.swift.
 */
enum class SleepStage {
    LIGHT, DEEP, AWAKE, UNKNOWN, REM;

    companion object {
        fun fromByte(byte: UByte): SleepStage = when (byte) {
            0x28u -> LIGHT
            0x63u -> DEEP
            0x00u -> AWAKE
            else -> UNKNOWN
        }
    }
}

/**
 * Ported from [DecodeConfidence] in PulseModels.swift.
 */
enum class DecodeConfidence(val debugLabel: String) {
    KNOWN("high"),
    PARTIAL("medium"),
    UNKNOWN("unknown");
}

/**
 * Ported from [RingDecodedEvent] in RingProtocol.swift.
 * A typed event decoded from a raw ring notification.
 */
sealed class RingDecodedEvent {
    abstract val kind: String
    abstract val confidence: DecodeConfidence
    abstract val debugJSON: String

    val timestamp: Instant get() = when (this) {
        is ActivityUpdate -> this._timestamp
        is ActivityBucket -> this._timestamp
        is HeartRateSample -> this._timestamp
        is HeartRateComplete -> this._timestamp
        is Spo2Progress -> this._timestamp
        is Spo2Result -> this._timestamp
        is Spo2Complete -> this._timestamp
        is SleepTimeline -> this._timestamp
        is HistoryMeasurement -> this._timestamp
        is StressSample -> this._timestamp
        is HrvSample -> this._timestamp
        is TemperatureSample -> this._timestamp
        is HistorySyncProgress -> Instant.EPOCH
        is HistorySyncFinished -> Instant.EPOCH
        is Battery -> Instant.EPOCH
        is Status -> Instant.EPOCH
        is TimeSyncAck -> this._timestamp
        is CommandAck -> Instant.EPOCH
        is Unknown -> Instant.EPOCH
    }

    data class ActivityUpdate(
        val _timestamp: Instant,
        val steps: Int,
        val distanceMeters: Double,
        val calories: Double
    ) : RingDecodedEvent() {
        override val kind = "activity"
        override val confidence = DecodeConfidence.KNOWN
        override val debugJSON = """{"steps":$steps,"distance_m":${distanceMeters.toInt()},"calories":${calories.toInt()}}"""
    }

    data class ActivityBucket(
        val _timestamp: Instant,
        val steps: Int,
        val distanceMeters: Double
    ) : RingDecodedEvent() {
        override val kind = "activity_bucket"
        override val confidence = DecodeConfidence.KNOWN
        override val debugJSON = """{"steps":$steps,"distance_m":${distanceMeters.toInt()}}"""
    }

    data class HeartRateSample(
        val bpm: Int,
        val _timestamp: Instant
    ) : RingDecodedEvent() {
        override val kind = "hr_sample"
        override val confidence = DecodeConfidence.KNOWN
        override val debugJSON = """{"bpm":$bpm}"""
    }

    data class HeartRateComplete(
        val _timestamp: Instant
    ) : RingDecodedEvent() {
        override val kind = "hr_complete"
        override val confidence = DecodeConfidence.PARTIAL
        override val debugJSON = "{}"
    }

    data class Spo2Progress(
        val percent: Int?,
        val _timestamp: Instant
    ) : RingDecodedEvent() {
        override val kind = "spo2_progress"
        override val confidence = DecodeConfidence.PARTIAL
        override val debugJSON = "{}"
    }

    data class Spo2Result(
        val value: Int,
        val _timestamp: Instant
    ) : RingDecodedEvent() {
        override val kind = "spo2_result"
        override val confidence = DecodeConfidence.KNOWN
        override val debugJSON = """{"spo2":$value}"""
    }

    data class Spo2Complete(
        val _timestamp: Instant
    ) : RingDecodedEvent() {
        override val kind = "spo2_complete"
        override val confidence = DecodeConfidence.PARTIAL
        override val debugJSON = "{}"
    }

    data class SleepTimeline(
        val _timestamp: Instant,
        val stages: List<SleepStage>
    ) : RingDecodedEvent() {
        override val kind = "sleep_timeline"
        override val confidence = DecodeConfidence.KNOWN
        override val debugJSON = "{}"
    }

    data class HistoryMeasurement(
        val kind_field: MeasurementKind,
        val value: Double,
        val _timestamp: Instant
    ) : RingDecodedEvent() {
        override val kind = "history_measurement"
        override val confidence = DecodeConfidence.KNOWN
        override val debugJSON = "{}"
    }

    data class StressSample(
        val value: Int,
        val _timestamp: Instant
    ) : RingDecodedEvent() {
        override val kind = "stress_sample"
        override val confidence = DecodeConfidence.KNOWN
        override val debugJSON = """{"stress":$value}"""
    }

    data class HrvSample(
        val value: Int,
        val _timestamp: Instant
    ) : RingDecodedEvent() {
        override val kind = "hrv_sample"
        override val confidence = DecodeConfidence.KNOWN
        override val debugJSON = """{"hrv_ms":$value}"""
    }

    data class TemperatureSample(
        val celsius: Double,
        val _timestamp: Instant
    ) : RingDecodedEvent() {
        override val kind = "temperature_sample"
        override val confidence = DecodeConfidence.KNOWN
        override val debugJSON = """{"temp_c":$celsius}"""
    }

    data class HistorySyncProgress(
        val stage: String
    ) : RingDecodedEvent() {
        override val kind = "history_sync_progress"
        override val confidence = DecodeConfidence.KNOWN
        override val debugJSON = """{"stage":"$stage"}"""
    }

    object HistorySyncFinished : RingDecodedEvent() {
        override val kind = "history_sync_finished"
        override val confidence = DecodeConfidence.KNOWN
        override val debugJSON = "{}"
    }

    data class Battery(
        val percent: Int
    ) : RingDecodedEvent() {
        override val kind = "battery"
        override val confidence = DecodeConfidence.KNOWN
        override val debugJSON = """{"percent":$percent}"""
    }

    data class Status(
        val address: String?
    ) : RingDecodedEvent() {
        override val kind = "status"
        override val confidence = DecodeConfidence.KNOWN
        override val debugJSON = """{"address":"${address ?: ""}"}"""
    }

    data class TimeSyncAck(
        val _timestamp: Instant
    ) : RingDecodedEvent() {
        override val kind = "time_sync_ack"
        override val confidence = DecodeConfidence.KNOWN
        override val debugJSON = "{}"
    }

    data class CommandAck(
        val commandId: UByte
    ) : RingDecodedEvent() {
        override val kind = "command_ack"
        override val confidence = DecodeConfidence.PARTIAL
        override val debugJSON = "{}"
    }

    data class Unknown(
        val commandId: UByte,
        val raw: ByteArray
    ) : RingDecodedEvent() {
        override val kind = "unknown"
        override val confidence = DecodeConfidence.UNKNOWN
        override val debugJSON = "{}"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Unknown) return false
            return commandId == other.commandId && raw.contentEquals(other.raw)
        }

        override fun hashCode(): Int = 31 * commandId.hashCode() + raw.contentHashCode()
    }
}
