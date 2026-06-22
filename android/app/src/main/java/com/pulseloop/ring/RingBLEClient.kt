package com.pulseloop.ring

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Ported from [RingBLEClient] in RingBLEClient.swift.
 * Device-agnostic Android BLE client for any supported wearable.
 *
 * The client owns BluetoothLeScanner + BluetoothGatt plumbing — scanning, connecting,
 * discovering services/characteristics, serializing writes, and fanning out notifications.
 * Protocol-specific decisions are delegated to [WearableDriver] / [RingSyncEngine].
 *
 * Registry: walk [coordinators]; first whose [matches] claims a peripheral wins.
 * Adding a new wearable = append one entry.
 */
@SuppressLint("MissingPermission")
class RingBLEClient(private val context: Context) {

    /** Registry of supported wearables. Adding a wearable = append one entry. */
    private val coordinators: List<WearableCoordinator> = listOf(
        JringCoordinator,
        ColmiCoordinator,
    )

    // MARK: Observable state

    data class BLEState(
        val connectionState: RingConnectionState = RingConnectionState.IDLE,
        val discovered: List<DiscoveredRing> = emptyList(),
        val batteryPercent: Int? = null,
        val isBluetoothReady: Boolean = false,
        val lastError: String? = null,
        val activeDeviceType: RingDeviceType? = null,
        val activeCapabilities: Set<WearableCapability> = emptySet(),
        val firmwareVersion: String? = null,
    )

    data class DiscoveredRing(
        val id: String,
        val name: String,
        val rssi: Int,
        val isLikelyRing: Boolean,
        val deviceType: RingDeviceType?,
    )

    private val _state = MutableStateFlow(BLEState())
    val state: StateFlow<BLEState> = _state.asStateFlow()

    var onConnected: (suspend () -> Unit)? = null
    var onFirmwareRead: ((String) -> Unit)? = null
    val syncEngine: RingSyncEngine? get() = activeSyncEngine
    private var keepaliveJob: Job? = null

    // MARK: Bluetooth infrastructure

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter = bluetoothManager.adapter
    private val scanner: BluetoothLeScanner? = bluetoothAdapter.bluetoothLeScanner

    private var bluetoothGatt: BluetoothGatt? = null
    private var discoveredPeripherals: MutableMap<String, BluetoothDevice> = mutableMapOf()

    // Characteristics
    private var writeChar: BluetoothGattCharacteristic? = null
    private var commandChar: BluetoothGattCharacteristic? = null
    private var notifyChars: MutableMap<UUID, BluetoothGattCharacteristic> = mutableMapOf()
    private var batteryChar: BluetoothGattCharacteristic? = null

    // MARK: Active driver/engine

    private var activeCoordinator: WearableCoordinator? = null
    private var activeDriver: WearableDriver? = null
    private var activeSyncEngine: RingSyncEngine? = null

    // MARK: Write serialization

    private data class QueuedWrite(val data: ByteArray, val useCommandChannel: Boolean)
    private val writeQueue = mutableListOf<QueuedWrite>()
    private var writeInFlight = false

    // MARK: Connection state

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val prefs: SharedPreferences =
        context.getSharedPreferences("ring_ble", Context.MODE_PRIVATE)

    // MARK: Public API

    /** Check if required BLE permissions are granted. */
    fun hasPermissions(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    fun startScanning() {
        if (!hasPermissions()) {
            updateState { copy(lastError = "BLE permissions not granted") }
            return
        }
        if (!bluetoothAdapter.isEnabled) {
            updateState { copy(lastError = "Bluetooth is not powered on") }
            return
        }
        updateState {
            copy(
                connectionState = RingConnectionState.SCANNING,
                discovered = emptyList(),
                lastError = null,
            )
        }
        discoveredPeripherals.clear()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner?.startScan(null, scanSettings, scanCallback)
    }

    fun stopScanning() {
        scanner?.stopScan(scanCallback)
        if (_state.value.connectionState == RingConnectionState.SCANNING) {
            updateState { copy(connectionState = RingConnectionState.IDLE) }
        }
    }

    fun connectTo(id: String) {
        val target = discoveredPeripherals[id] ?: run {
            try { bluetoothAdapter.getRemoteDevice(id) } catch (_: Exception) { null }
        } ?: run {
            updateState { copy(lastError = "Ring no longer available; scan again.") }
            return
        }
        val matchedType = _state.value.discovered.firstOrNull { it.id == id }?.deviceType
        beginConnect(target, matchedType)
    }

    fun connectLastKnown() {
        if (!bluetoothAdapter.isEnabled) return
        val lastId = lastKnownIdentifier ?: return
        val device = try {
            bluetoothAdapter.getRemoteDevice(lastId)
        } catch (_: Exception) { null }
        if (device != null) {
            beginConnect(device, lastKnownDeviceType)
        } else {
            startScanning()
        }
    }

    fun disconnect() {
        stopKeepalive()
        scanner?.stopScan(scanCallback)
        // Clear Android's GATT cache and remove OS-level bond so ring
        // is immediately discoverable after disconnect (no reboot needed)
        bluetoothGatt?.let { gatt ->
            try { gatt::class.java.getMethod("refresh").invoke(gatt) } catch (_: Exception) {}
            try { gatt.device::class.java.getMethod("removeBond").invoke(gatt.device) } catch (_: Exception) {}
            gatt.disconnect()
            gatt.close()
        }
        bluetoothGatt = null
        updateState { copy(connectionState = RingConnectionState.IDLE) }
    }

    fun forget() {
        disconnect()
        prefs.edit().remove(LAST_PERIPHERAL_KEY).remove(LAST_DEVICE_TYPE_KEY).apply()
        updateState { copy(activeDeviceType = null, activeCapabilities = emptySet()) }
    }

    fun enqueueWrite(data: ByteArray) {
        val framed = activeDriver?.frame(data) ?: data
        val useCommand = activeDriver?.usesCommandChannel(framed) ?: false
        writeQueue.add(QueuedWrite(framed, useCommand))
        pumpWrites()
    }

    fun readBattery() {
        val gatt = bluetoothGatt ?: return
        val ch = batteryChar ?: return
        gatt.readCharacteristic(ch)
    }

    private val lastKnownIdentifier: String?
        get() = prefs.getString(LAST_PERIPHERAL_KEY, null)
    private val lastKnownDeviceType: RingDeviceType?
        get() = prefs.getString(LAST_DEVICE_TYPE_KEY, null)?.let { type ->
            try { RingDeviceType.valueOf(type) } catch (_: Exception) { null }
        }

    // MARK: Internal

    private fun beginConnect(target: BluetoothDevice, deviceType: RingDeviceType?) {
        scanner?.stopScan(scanCallback)
        val coordinator = coordinators.firstOrNull { it.deviceType == deviceType } ?: JringCoordinator
        installDriver(coordinator)
        updateState { copy(connectionState = RingConnectionState.CONNECTING) }

        bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // autoConnect=true matches the official app — connects silently
            // in the background without showing the Bluetooth status bar icon
            target.connectGatt(context, true, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            @Suppress("DEPRECATION")
            target.connectGatt(context, true, gattCallback)
        }
    }

    /** Send a keepalive ping every 15s to prevent the ring's idle timeout. */
    private fun startKeepalive() {
        keepaliveJob?.cancel()
        keepaliveJob = scope.launch {
            while (isActive) {
                delay(15_000)
                // Send status query (0x0C) as keepalive
                enqueueWrite(RingEncoder.hexToBytes("0c00000000000000000000000000000000000000"))
            }
        }
    }

    private fun stopKeepalive() {
        keepaliveJob?.cancel()
        keepaliveJob = null
    }

    private fun installDriver(coordinator: WearableCoordinator) {
        val driver = coordinator.makeDriver { enqueueWrite(it) }
        activeCoordinator = coordinator
        activeDriver = driver
        activeSyncEngine = driver.makeSyncEngine()
        updateState {
            copy(
                activeDeviceType = coordinator.deviceType,
                activeCapabilities = coordinator.capabilities,
            )
        }
    }

    private fun pumpWrites() {
        val gatt = bluetoothGatt ?: return
        val wChar = writeChar ?: return
        if (writeInFlight || writeQueue.isEmpty()) return

        val item = writeQueue.removeFirst()
        val target = if (item.useCommandChannel) commandChar ?: wChar else wChar
        target.value = item.data
        writeInFlight = true

        PulseEventBus.publishBlocking(
            PulseEvent.RawPacket(PacketDirection.OUTGOING, item.data,
                RingDecodedEvent.CommandAck(commandId = if (item.data.isNotEmpty()) item.data[0].toUByte() else 0u))
        )
        gatt.writeCharacteristic(target)
    }

    private fun matchDeviceType(name: String?, scanRecord: ScanRecord?): RingDeviceType? {
        val serviceUUIDs = scanRecord?.serviceUuids?.map { it.uuid.toString() } ?: emptyList()
        // Iterate all manufacturer-specific data entries to find a match
        var mfg: ByteArray? = null
        scanRecord?.manufacturerSpecificData?.let { data ->
            if (data.size() > 0) mfg = data.valueAt(0)
        }
        val info = AdvertisementInfo(serviceUUIDs, mfg)
        return coordinators.firstOrNull { it.matches(name, info) }?.deviceType
    }

    private inline fun updateState(crossinline update: BLEState.() -> BLEState) {
        _state.value = _state.value.update()
    }

    // MARK: Scan callback

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val scanRecord = result.scanRecord ?: return
            val name = scanRecord.deviceName ?: device.name ?: return
            if (name.isEmpty()) return

            val matchedType = matchDeviceType(name, scanRecord)
            discoveredPeripherals[device.address] = device

            val ring = DiscoveredRing(
                id = device.address,
                name = name,
                rssi = result.rssi,
                isLikelyRing = matchedType != null,
                deviceType = matchedType,
            )

            updateState {
                val updated = discovered.toMutableList()
                val idx = updated.indexOfFirst { it.id == ring.id }
                if (idx >= 0) updated[idx] = ring else updated.add(ring)
                updated.sortWith(compareByDescending<DiscoveredRing> { it.isLikelyRing }.thenByDescending { it.rssi })
                copy(discovered = updated)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            updateState { copy(lastError = "Scan failed: $errorCode") }
        }
    }

    // MARK: GATT callback

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        bluetoothGatt = gatt
                        // Only trigger bonding if not already bonded.
                        // On reconnect (e.g. after phone idle/sleep), the bond
                        // persists — no need to show the pairing dialog again.
                        if (gatt.device.bondState != BluetoothDevice.BOND_BONDED) {
                            try { gatt.device.createBond() } catch (_: Exception) {}
                        }
                        gatt.requestMtu(512)
                        gatt.discoverServices()
                    } else {
                        updateState { copy(lastError = "GATT connect failed: $status") }
                        handleDisconnect(gatt)
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    handleDisconnect(gatt)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return

            // Log all discovered service UUIDs for diagnostics
            val serviceUuids = gatt.services.map { it.uuid.toString() }
            android.util.Log.i("RingBLEClient", "Services: ${serviceUuids.joinToString(", ")}")

            // Store service list in a log event for export
            scope.launch(Dispatchers.IO) {
                try {
                    val db = com.pulseloop.data.PulseLoopDatabase.getInstance(context.applicationContext)
                    val device = db.deviceDao().current()
                    if (device != null) {
                        db.deviceDao().upsert(device.copy(
                            capabilitiesRaw = device.capabilitiesRaw + "|services:" + serviceUuids.joinToString(","),
                            updatedAt = System.currentTimeMillis()
                        ))
                    }
                } catch (_: Exception) {}
            }

            val driver = activeDriver ?: return

            // Standard BLE health services — blood pressure (0x1810) + glucose (0x1808)
            val bpServiceUuid = java.util.UUID.fromString("00001810-0000-1000-8000-00805f9b34fb")
            val bpMeasureUuid = java.util.UUID.fromString("00002a35-0000-1000-8000-00805f9b34fb")
            val glucoseServiceUuid = java.util.UUID.fromString("00001808-0000-1000-8000-00805f9b34fb")
            val glucoseMeasureUuid = java.util.UUID.fromString("00002a18-0000-1000-8000-00805f9b34fb")
            for (service in gatt.services) {
                when (service.uuid) {
                    bpServiceUuid -> {
                        service.getCharacteristic(bpMeasureUuid)?.let {
                            gatt.setCharacteristicNotification(it, true)
                            it.getDescriptor(CCCD_UUID)?.let { desc ->
                                desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                gatt.writeDescriptor(desc)
                            }
                        }
                    }
                    glucoseServiceUuid -> {
                        service.getCharacteristic(glucoseMeasureUuid)?.let {
                            gatt.setCharacteristicNotification(it, true)
                            it.getDescriptor(CCCD_UUID)?.let { desc ->
                                desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                gatt.writeDescriptor(desc)
                            }
                        }
                    }
                }
            }

            // Read firmware: scan ALL services for 0x2A26/0x2A28.
            // The 56ff ring exposes these even without advertising 0x180A DIS.
            val fwUuid = java.util.UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")
            val swUuid = java.util.UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb")
            var fwRead = false
            for (service in gatt.services) {
                if (service.uuid == DIS_SERVICE_UUID) {
                    service.getCharacteristic(FW_REV_UUID)?.let { gatt.readCharacteristic(it); fwRead = true }
                }
                service.getCharacteristic(fwUuid)?.let { gatt.readCharacteristic(it); fwRead = true }
                service.getCharacteristic(swUuid)?.let { gatt.readCharacteristic(it) }
            }

            for (service in gatt.services) {
                val svcUuid = service.uuid.toString()
                val isRingSvc = driver.serviceUUIDs.any { it == svcUuid }
                val isBatterySvc = driver.batteryServiceUUID != null && svcUuid == driver.batteryServiceUUID
                if (!isRingSvc && !isBatterySvc) continue

                for (ch in service.characteristics) {
                    val uuid = ch.uuid.toString()
                    when {
                        uuid == driver.writeUUID -> writeChar = ch
                        uuid == driver.commandUUID -> commandChar = ch
                        driver.notifyUUIDs.any { it == uuid } -> {
                            notifyChars[ch.uuid] = ch
                            gatt.setCharacteristicNotification(ch, true)
                            ch.getDescriptor(CCCD_UUID)?.let { desc ->
                                desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                gatt.writeDescriptor(desc)
                            }
                        }
                        uuid == driver.batteryCharUUID -> {
                            batteryChar = ch
                            gatt.readCharacteristic(ch)
                        }
                    }
                }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            if (characteristic.uuid.toString() == activeDriver?.batteryCharUUID) {
                val value = characteristic.value
                if (value != null && value.isNotEmpty()) {
                    val pct = value[0].toInt() and 0xFF
                    updateState { copy(batteryPercent = pct) }
                    PulseEventBus.publishBlocking(PulseEvent.BatteryLevel(pct))
                }
            } else if (characteristic.uuid == FW_REV_UUID ||
                       characteristic.uuid.toString().startsWith("00002a26") ||
                       characteristic.uuid.toString().startsWith("00002a28")) {
                val fw = characteristic.value?.let { String(it) }?.trim()
                if (fw != null && fw.isNotEmpty()) {
                    updateState { copy(firmwareVersion = fw) }
                    onFirmwareRead?.invoke(fw)
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int
        ) {
            writeInFlight = false
            pumpWrites()
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic
        ) {
            val value = characteristic.value ?: return
            val uuid = characteristic.uuid.toString()

            // Standard BLE health services — read before the ring-service guard
            if (uuid.startsWith("00002a35")) {
                // Blood Pressure Measurement — IEEE 11073 SFLOAT
                if (value.size >= 7) {
                    val systolic = decodeSFLOAT(value[1], value[2])
                    val diastolic = decodeSFLOAT(value[3], value[4])
                    PulseEventBus.publishBlocking(PulseEvent.HistoryMeasurement(MeasurementKind.BLOOD_PRESSURE_SYSTOLIC, systolic, java.time.Instant.now()))
                    PulseEventBus.publishBlocking(PulseEvent.HistoryMeasurement(MeasurementKind.BLOOD_PRESSURE_DIASTOLIC, diastolic, java.time.Instant.now()))
                }
                return
            }
            if (uuid.startsWith("00002a18")) {
                // Glucose Measurement — IEEE 11073 SFLOAT in kg/L → mg/dL
                if (value.size >= 12) {
                    val glucoseKgL = decodeSFLOAT(value[10], value[11])
                    PulseEventBus.publishBlocking(PulseEvent.HistoryMeasurement(MeasurementKind.BLOOD_SUGAR, glucoseKgL * 100000.0, java.time.Instant.now()))
                }
                return
            }

            val driver = activeDriver ?: return
            if (!driver.notifyUUIDs.any { it == characteristic.uuid.toString() }) return

            for (decoded in driver.ingest(value, characteristic.uuid.toString())) {
                PulseEventBus.publishBlocking(
                    PulseEvent.RawPacket(PacketDirection.INCOMING, value, decoded)
                )
                for (event in RingEventBridge.eventsFor(decoded)) {
                    PulseEventBus.publishBlocking(event)
                }
                activeSyncEngine?.handle(decoded)
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int
        ) {
            // Notification enabled — fire onConnected once at least one notify is live
            val driver = activeDriver ?: return
            val ch = descriptor.characteristic
            if (!driver.notifyUUIDs.any { it == ch.uuid.toString() }) return
            if (_state.value.connectionState == RingConnectionState.CONNECTED) return

            updateState { copy(connectionState = RingConnectionState.CONNECTED) }
            startKeepalive()  // ping ring every 15s to prevent idle disconnect
            val device = gatt.device
            prefs.edit()
                .putString(LAST_PERIPHERAL_KEY, device.address)
                .putString(LAST_DEVICE_TYPE_KEY, activeCoordinator?.deviceType?.name)
                .apply()

            PulseEventBus.publishBlocking(
                PulseEvent.DeviceStateChanged(RingConnectionState.CONNECTED, device.address)
            )
            activeCoordinator?.let { coord ->
                PulseEventBus.publishBlocking(
                    PulseEvent.DeviceIdentified(coord.deviceType, coord.capabilities)
                )
            }
            readBattery()

            scope.launch { onConnected?.invoke() }
            pumpWrites()
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            // No-op for Phase 2; MTU negotiation may be added later
        }
    }

    private fun handleDisconnect(gatt: BluetoothGatt) {
        writeChar = null; commandChar = null; notifyChars.clear(); batteryChar = null
        writeInFlight = false; writeQueue.clear()
        stopKeepalive()

        PulseEventBus.publishBlocking(
            PulseEvent.DeviceStateChanged(RingConnectionState.DISCONNECTED, null)
        )

        // autoConnect=true handles reconnection automatically — no need for gatt.connect()
        updateState { copy(connectionState = RingConnectionState.DISCONNECTED) }
        gatt.close()
        bluetoothGatt = null
    }

    fun destroy() {
        scope.cancel()
        disconnect()
    }

    companion object {
        private const val LAST_PERIPHERAL_KEY = "ring.lastPeripheralIdentifier"
        private const val LAST_DEVICE_TYPE_KEY = "ring.lastDeviceType"
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val DIS_SERVICE_UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
        private val FW_REV_UUID = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")

        /** Decode IEEE 11073 SFLOAT: exponent (4-bit signed) + mantissa (12-bit signed). */
        fun decodeSFLOAT(b0: Byte, b1: Byte): Double {
            val raw = ((b1.toInt() and 0xFF) shl 8) or (b0.toInt() and 0xFF)
            val exponent = ((raw shr 12) and 0x0F).let { if (it >= 8) it - 16 else it }
            val mantissa = (raw and 0x0FFF).let { if (it >= 0x0800) it - 0x1000 else it }
            return mantissa * Math.pow(10.0, exponent.toDouble())
        }
    }
}
