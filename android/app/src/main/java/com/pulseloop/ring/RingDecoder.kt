package com.pulseloop.ring

import java.time.Instant
import java.time.ZoneOffset
import java.util.TimeZone

/**
 * Ported from [RingDecoder] in RingProtocol.swift.
 * Decodes 20-byte raw BLE notification data into typed [RingDecodedEvent]s.
 */
object RingDecoder {

    /**
     * Decode a 20-byte raw notification into a typed event.
     * Falls back to [RingDecodedEvent.Unknown] if the packet is malformed
     * or the command ID is unrecognized.
     */
    fun decode(data: ByteArray): RingDecodedEvent {
        val packet = RingPacket.fromData(data).getOrElse {
            return RingDecodedEvent.Unknown(
                commandId = if (data.isNotEmpty()) data[0].toUByte() else 0u,
                raw = data
            )
        }

        val bytes = packet.raw
        val now = Instant.now()

        return when (packet.commandId.toInt()) {
            0x01 -> {
                if (bytes.size >= 6) {
                    RingDecodedEvent.TimeSyncAck(
                        _timestamp = Instant.ofEpochSecond(u32le(bytes, 1).toLong())
                    )
                } else {
                    RingDecodedEvent.Unknown(commandId = packet.commandId, raw = bytes)
                }
            }
            0x02 -> RingDecodedEvent.CommandAck(commandId = packet.commandId)
            0x03 -> {
                if (bytes.size >= 17) {
                    RingDecodedEvent.ActivityUpdate(
                        _timestamp = Instant.ofEpochSecond(u32le(bytes, 1).toLong()),
                        steps = u32le(bytes, 5).toInt(),
                        distanceMeters = u32le(bytes, 9).toDouble(),
                        calories = u32le(bytes, 13).toDouble()
                    )
                } else {
                    RingDecodedEvent.Unknown(commandId = packet.commandId, raw = bytes)
                }
            }
            0x0B -> {
                if (bytes.size >= 2) {
                    RingDecodedEvent.Battery(percent = bytes[1].toInt() and 0xFF)
                } else {
                    RingDecodedEvent.Unknown(commandId = packet.commandId, raw = bytes)
                }
            }
            0x0C -> {
                val address = if (bytes.size >= 9) {
                    bytes.slice(3..8).joinToString(":") { String.format("%02x", it) }
                } else null
                // Firmware version is at bytes[9..12] as two LE 16-bit values.
                // Example: bytes 3a 00 2a 00 → "003A002A" (matches official app)
                val fwBytes = if (bytes.size >= 13) {
                    val a = ((bytes[10].toInt() and 0xFF) shl 8) or (bytes[9].toInt() and 0xFF)
                    val b = ((bytes[12].toInt() and 0xFF) shl 8) or (bytes[11].toInt() and 0xFF)
                    String.format("%04X%04X", a, b)
                } else null
                RingDecodedEvent.Status(address = address, firmware = fwBytes)
            }
            0x11 -> {
                if (bytes.size >= 20) {
                    RingDecodedEvent.SleepTimeline(
                        _timestamp = Instant.ofEpochSecond(u32le(bytes, 1).toLong()),
                        stages = bytes.slice(5..19).map { SleepStage.fromByte(it.toUByte()) }
                    )
                } else {
                    RingDecodedEvent.Unknown(commandId = packet.commandId, raw = bytes)
                }
            }
            0x14 -> {
                if (bytes.size >= 6) {
                    RingDecodedEvent.HeartRateSample(
                        bpm = bytes[5].toInt() and 0xFF,
                        _timestamp = now
                    )
                } else {
                    RingDecodedEvent.Unknown(commandId = packet.commandId, raw = bytes)
                }
            }
            0x16 -> {
                if (bytes.size >= 9) {
                    val offset = if (bytes[1].toInt() and 0xFF == 0xAA) 3 else 2
                    val timestamp = Instant.ofEpochSecond(u32le(bytes, offset).toLong())
                    val values = bytes.drop(8).filter { it != 0.toByte() }
                    if (values.isNotEmpty()) {
                        RingDecodedEvent.HistoryMeasurement(
                            kind_field = MeasurementKind.HEART_RATE,
                            value = values.first().toInt().and(0xFF).toDouble(),
                            _timestamp = timestamp
                        )
                    } else {
                        RingDecodedEvent.CommandAck(commandId = packet.commandId)
                    }
                } else {
                    RingDecodedEvent.Unknown(commandId = packet.commandId, raw = bytes)
                }
            }
            0x24 -> {
                if (bytes.size >= 5) {
                    val value = bytes[4].toInt() and 0xFF
                    if (value in 80..100) {
                        RingDecodedEvent.Spo2Result(value = value, _timestamp = now)
                    } else {
                        RingDecodedEvent.Spo2Progress(percent = null, _timestamp = now)
                    }
                } else {
                    RingDecodedEvent.Spo2Progress(percent = null, _timestamp = now)
                }
            }
            0x27 -> RingDecodedEvent.HeartRateComplete(_timestamp = now)
            0x28 -> RingDecodedEvent.Spo2Complete(_timestamp = now)
            0xF6 -> {
                // Version variant: bytes [4-5] = LE u16 version number
                // Example: 8a 00 → 138 → "V138" (matches official app)
                val version = if (bytes.size >= 6) {
                    ((bytes[5].toInt() and 0xFF) shl 8) or (bytes[4].toInt() and 0xFF)
                } else null
                RingDecodedEvent.FirmwareVersion(version = version)
            }
            else -> RingDecodedEvent.Unknown(commandId = packet.commandId, raw = bytes)
        }
    }

    /**
     * Read a little-endian u32 from [bytes] starting at [offset].
     * Returns 0 if insufficient bytes available.
     */
    private fun u32le(bytes: ByteArray, offset: Int): UInt {
        if (bytes.size < offset + 4) return 0u
        return (bytes[offset].toUByte().toUInt() or
                (bytes[offset + 1].toUByte().toUInt() shl 8) or
                (bytes[offset + 2].toUByte().toUInt() shl 16) or
                (bytes[offset + 3].toUByte().toUInt() shl 24))
    }
}
