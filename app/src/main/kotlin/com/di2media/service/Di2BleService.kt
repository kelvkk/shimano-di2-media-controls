package com.di2media.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.di2media.mapping.ActionDispatcher
import com.di2media.mapping.ButtonMappingConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class Di2BleService : Service() {

    companion object {
        const val TAG = "Di2BleService"
        const val CHANNEL_ID = "di2_media_channel"
        const val NOTIFICATION_ID = 1

        val DI2_SERVICE_UUID: UUID = UUID.fromString("000018ef-5348-494d-414e-4f5f424c4500")
        val DI2_BUTTON_CHAR_UUID: UUID = UUID.fromString("00002ac2-5348-494d-414e-4f5f424c4500")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        const val MASK_SHORT = 0x10
        const val MASK_LONG = 0x20
        const val MASK_DOUBLE = 0x40
    }

    private val binder = LocalBinder()
    private var bluetoothGatt: BluetoothGatt? = null
    private var scanning = false

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    private val scanner: BluetoothLeScanner? by lazy {
        bluetoothAdapter?.bluetoothLeScanner
    }

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _channelStates = MutableStateFlow<Map<Int, PressType?>>(emptyMap())
    val channelStates: StateFlow<Map<Int, PressType?>> = _channelStates.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private var lastChannelValues: IntArray? = null
    private var initialized = false
    private var lastPressTypes = mutableMapOf<Int, PressType?>()

    private lateinit var dispatcher: ActionDispatcher
    lateinit var mappingConfig: ButtonMappingConfig
        private set

    // ── Lifecycle ───────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        dispatcher = ActionDispatcher(this)
        mappingConfig = ButtonMappingConfig(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Di2 Media: Ready"))
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        stopScan()
        bluetoothGatt?.close()
        if (::dispatcher.isInitialized) dispatcher.destroy()
        super.onDestroy()
    }

    inner class LocalBinder : Binder() {
        fun getService(): Di2BleService = this@Di2BleService
    }

    // ── Scanning ────────────────────────────────────────────────

    fun startScan() {
        if (scanning) return
        scanning = true
        _discoveredDevices.value = emptyList()
        _connectionState.value = ConnectionState.SCANNING

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner?.startScan(null, settings, scanCallback)
        Log.i(TAG, "BLE scan started")
    }

    fun stopScan() {
        if (!scanning) return
        scanning = false
        try {
            scanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Stop scan failed", e)
        }
        Log.i(TAG, "BLE scan stopped")
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name ?: result.scanRecord?.deviceName

            if (name == null) {
                // Check if the device advertises the Shimano service UUID
                val hasShimanoService = result.scanRecord?.serviceUuids?.any {
                    it.uuid == DI2_SERVICE_UUID
                } == true
                if (!hasShimanoService) return
            }

            val deviceName = name ?: "Shimano Di2"

            if (deviceName.contains("SHIMANO", ignoreCase = true) ||
                deviceName.contains("UWUBIKE", ignoreCase = true) ||
                deviceName.contains("DI2", ignoreCase = true) ||
                result.scanRecord?.serviceUuids?.any { it.uuid == DI2_SERVICE_UUID } == true
            ) {
                val discovered = DiscoveredDevice(deviceName, device.address, result.rssi)
                val current = _discoveredDevices.value.toMutableList()
                if (current.none { it.address == device.address }) {
                    current.add(discovered)
                    _discoveredDevices.value = current
                    Log.i(TAG, "Found Di2 device: $deviceName (${device.address})")
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed: $errorCode")
            scanning = false
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    // ── GATT Connection ─────────────────────────────────────────

    fun connectToDevice(address: String) {
        stopScan()
        val device = bluetoothAdapter?.getRemoteDevice(address) ?: return
        _connectionState.value = ConnectionState.CONNECTING
        initialized = false
        lastChannelValues = null
        bluetoothGatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _channelStates.value = emptyMap()
        initialized = false
        lastChannelValues = null
    }

    fun shutdown() {
        stopScan()
        disconnect()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    @Suppress("DEPRECATION")
    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "Connected to Di2")
                    _connectionState.value = ConnectionState.CONNECTED
                    gatt.discoverServices()
                    updateNotification("Di2 Media: Connected")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "Disconnected from Di2")
                    _connectionState.value = ConnectionState.DISCONNECTED
                    _channelStates.value = emptyMap()
                    updateNotification("Di2 Media: Disconnected")
                    gatt.close()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed: $status")
                return
            }

            gatt.services.forEach { service ->
                Log.i(TAG, "Service: ${service.uuid}")
                service.characteristics.forEach { char ->
                    Log.i(TAG, "  Char: ${char.uuid} props=${char.properties}")
                }
            }

            val service = gatt.getService(DI2_SERVICE_UUID)
            if (service == null) {
                Log.e(TAG, "Di2 service $DI2_SERVICE_UUID not found")
                return
            }

            val buttonChar = service.getCharacteristic(DI2_BUTTON_CHAR_UUID)
            if (buttonChar == null) {
                Log.e(TAG, "Button characteristic not found")
                return
            }

            // Enable indications (2ac2 uses indicate, not notify)
            gatt.setCharacteristicNotification(buttonChar, true)
            val descriptor = buttonChar.getDescriptor(CCCD_UUID)
            descriptor?.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            gatt.writeDescriptor(descriptor)
            Log.i(TAG, "Subscribed to button indications on 2ac2")
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == DI2_BUTTON_CHAR_UUID) {
                handleButtonData(characteristic.value)
            }
        }
    }

    // ── Button decoding ─────────────────────────────────────────

    private fun handleButtonData(data: ByteArray) {
        // Format: [counter, ch1, ch2, ch3, ch4]
        if (data.size < 2) return

        val channels = data.drop(1).map { it.toInt() and 0xFF }
        Log.i(TAG, "Button raw: ${data.joinToString(" ") { "%02x".format(it) }}")

        if (!initialized) {
            lastChannelValues = channels.toIntArray()
            initialized = true
            // Set initial idle state for active channels
            val initialStates = mutableMapOf<Int, PressType?>()
            channels.forEachIndexed { index, value ->
                if (value != 0xF0) initialStates[index + 1] = null
            }
            _channelStates.value = initialStates
            return
        }

        val prev = lastChannelValues ?: return
        val newStates = _channelStates.value.toMutableMap()

        channels.forEachIndexed { index, value ->
            val channel = index + 1
            if (value == 0xF0) return@forEachIndexed // unmapped channel

            if (index < prev.size && value != prev[index]) {
                val pressType = when {
                    value and MASK_DOUBLE != 0 -> PressType.DOUBLE
                    value and MASK_LONG != 0 -> PressType.LONG
                    value and MASK_SHORT != 0 -> PressType.SHORT
                    else -> null // released
                }
                newStates[channel] = pressType
                Log.i(TAG, "CH$channel: ${pressType?.name ?: "RELEASED"}")

                val prevPressType = lastPressTypes[channel]
                when {
                    pressType == PressType.SHORT || pressType == PressType.DOUBLE -> {
                        dispatcher.dispatch(mappingConfig.getInstantAction(channel, pressType))
                    }
                    pressType == PressType.LONG -> {
                        dispatcher.onHoldStart(channel, mappingConfig.getHoldAction(channel))
                    }
                    pressType == null && prevPressType == PressType.LONG -> {
                        dispatcher.onHoldStop(channel)
                    }
                }
                lastPressTypes[channel] = pressType
            }

            // Ensure channel is tracked
            if (channel !in newStates) newStates[channel] = null
        }

        _channelStates.value = newStates
        lastChannelValues = channels.toIntArray()
    }

    // ── Notification ────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Di2 Media Control",
            NotificationManager.IMPORTANCE_LOW
        )
        (getSystemService(NotificationManager::class.java)).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Di2 Media")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }
}

enum class ConnectionState { DISCONNECTED, SCANNING, CONNECTING, CONNECTED }
enum class PressType { SHORT, LONG, DOUBLE }
data class DiscoveredDevice(val name: String, val address: String, val rssi: Int)
