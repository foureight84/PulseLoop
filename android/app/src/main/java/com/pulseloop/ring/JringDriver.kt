package com.pulseloop.ring

@OptIn(ExperimentalStdlibApi::class)

/**
 * Ported from [JringCoordinator] in JringCoordinator.swift.
 * Coordinator for the original "jring" (SMART_RING, service 000056ff…).
 */
object JringCoordinator : WearableCoordinator {
    override val deviceType = RingDeviceType.JRING
    private const val ADVERTISED_NAME = "SMART_RING"
    private const val MANUFACTURER_HEX_NEEDLE = "41422ec75b6a"

    override fun matches(name: String?, advertisement: AdvertisementInfo): Boolean {
        if (name == ADVERTISED_NAME) return true
        if (advertisement.serviceUUIDs.contains(RingUUIDs.SERVICE)) return true
        advertisement.manufacturerData?.let { mfg ->
            if (mfg.toHexString().contains(MANUFACTURER_HEX_NEEDLE)) return true
        }
        return false
    }

    override val capabilities = setOf(
        WearableCapability.HEART_RATE,
        WearableCapability.SPO2,
        WearableCapability.STEPS,
        WearableCapability.SLEEP,
        WearableCapability.BATTERY,
        WearableCapability.TEMPERATURE,
        WearableCapability.MANUAL_HEART_RATE,
        WearableCapability.MANUAL_SPO2,
        WearableCapability.REALTIME_HEART_RATE,
        WearableCapability.FIND_DEVICE,
    )

    override val iconSystemName = "circle.hexagongrid.circle.fill"

    override fun makeDriver(writer: RingCommandWriter): WearableDriver = JringDriver(writer)
}

/**
 * Ported from [JringDriver] in JringDriver.swift.
 * Thin wrapper over RingDecoder/RingEncoder for jring devices.
 */
class JringDriver(private val writer: RingCommandWriter) : WearableDriver {
    private val decoder = RingDecoder

    override val serviceUUIDs = listOf(RingUUIDs.SERVICE)
    override val writeUUID = RingUUIDs.WRITE
    override val notifyUUIDs = listOf(RingUUIDs.NOTIFY)
    override val batteryServiceUUID = "0000180f-0000-1000-8000-00805f9b34fb"
    override val batteryCharUUID = RingUUIDs.BATTERY

    override fun frame(command: ByteArray) = command  // jring: already 20 bytes, no checksum

    override fun ingest(data: ByteArray, from: String): List<RingDecodedEvent> =
        listOf(decoder.decode(data))

    override fun makeSyncEngine(): RingSyncEngine = JringSyncEngine(writer)
}

/**
 * Ported from [JringSyncEngine] in JringSyncEngine.swift.
 * Fire-and-forget sync engine for jring devices.
 */
class JringSyncEngine(private val writer: RingCommandWriter?) : RingSyncEngine {
    private val encoder = RingEncoder

    override fun runStartup() {
        writer?.enqueue(encoder.makeStatusCommand())
        writer?.enqueue(encoder.makeTimeSyncCommand())
        writer?.enqueue(encoder.makeLocaleCommand())
        writer?.enqueue(encoder.makeActivityQueryCommand())
        writer?.enqueue(encoder.makeHistoryQueryCommand())
        writer?.enqueue(encoder.makeHistoryMeasurementQueryCommand())
    }

    override fun handle(event: RingDecodedEvent) {}  // Fire-and-forget

    override fun startHeartRate() {
        writer?.enqueue(encoder.makeHeartRateStartCommand())
    }

    override fun stopHeartRate() {
        writer?.enqueue(encoder.makeHeartRateStopCommand())
        writer?.enqueue(encoder.makeAutomaticHeartRateCommand(enabled = true, cadenceMinutes = 30))
    }

    override fun startSpO2() {
        writer?.enqueue(encoder.makeSpO2StartCommand())
    }

    override fun stopSpO2() {
        writer?.enqueue(encoder.makeSpO2StopCommand())
    }

    override fun findDevice() {
        writer?.enqueue(encoder.makeFindRingCommand())
    }

    override fun setGoal(steps: Int) {
        writer?.enqueue(encoder.makeGoalCommand(steps))
    }

    // Jring has no power-off or factory-reset capabilities — no-ops
    override fun powerOff() {}
    override fun factoryReset() {}
}
