package com.pulseloop.ring

/**
 * BLE UUIDs for the 56ff ring service.
 * Ported from [RingUUIDs] in RingProtocol.swift.
 */
object RingUUIDs {
    const val SERVICE = "000056ff-0000-1000-8000-00805f9b34fb"
    const val WRITE = "000033f3-0000-1000-8000-00805f9b34fb"
    const val NOTIFY = "000033f4-0000-1000-8000-00805f9b34fb"
    const val BATTERY = "00002a19-0000-1000-8000-00805f9b34fb"
}

/**
 * Ring command identifiers for the 56ff protocol.
 * Ported from [RingCommandID] in RingProtocol.swift.
 */
enum class RingCommandID(val code: UByte) {
    TIME_SYNC(0x01u),
    ACTIVITY_QUERY_ACK(0x02u),
    CURRENT_ACTIVITY(0x03u),
    FIND_RING_CANDIDATE(0x04u),
    PERCENT_STATUS(0x0Bu),
    STATUS(0x0Cu),
    HISTORY_SUMMARY(0x10u),
    SLEEP_TIMELINE(0x11u),
    ACTIVITY_SUMMARY(0x13u),
    HEART_RATE_SAMPLE_OR_START(0x14u),
    HEART_RATE_STOP(0x15u),
    HISTORY_MEASUREMENT_STREAM(0x16u),
    GOAL_OR_CONFIG(0x1Au),
    DEVICE_TIME_OR_CONFIG(0x20u),
    LOCALE(0x21u),
    SPO2_START_STOP(0x23u),
    SPO2_RESULT_PROGRESS(0x24u),
    HEART_RATE_COMPLETE(0x27u),
    SPO2_COMPLETE(0x28u),
    APP_IDENTIFIER(0x48u),
    MODE(0x52u);

    companion object {
        fun fromCode(code: UByte): RingCommandID? = entries.find { it.code == code }
    }
}

/**
 * Ported from [RingPacket] in RingProtocol.swift.
 * Fixed 20-byte packet: command ID byte + 19 payload bytes.
 */
data class RingPacket(
    val commandId: UByte,
    val payload: ByteArray,
    val raw: ByteArray
) {
    companion object {
        const val PACKET_SIZE = 20

        fun fromData(data: ByteArray): Result<RingPacket> {
            if (data.size != PACKET_SIZE) {
                return Result.failure(
                    RingProtocolError.InvalidLength(data.size)
                )
            }
            val cmdId = data[0].toUByte()
            val payload = data.copyOfRange(1, data.size)
            return Result.success(
                RingPacket(commandId = cmdId, payload = payload, raw = data)
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RingPacket) return false
        return raw.contentEquals(other.raw)
    }

    override fun hashCode(): Int = raw.contentHashCode()
}

/**
 * Ported from [RingProtocolError] in RingProtocol.swift.
 */
sealed class RingProtocolError(message: String) : Exception(message) {
    class InvalidLength(length: Int) :
        RingProtocolError("Invalid packet length: $length (expected ${RingPacket.PACKET_SIZE})")
    class InvalidHex(hex: String) :
        RingProtocolError("Invalid hex string: $hex")
}
