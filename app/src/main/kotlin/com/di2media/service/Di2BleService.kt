package com.di2media.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.util.Log
import androidx.core.content.ContextCompat
import com.di2media.MainActivity
import com.di2media.R
import com.di2media.mapping.ActionDispatcher
import com.di2media.mapping.ButtonMappingConfig
import com.di2media.mapping.ClickCounter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class Di2BleService : Service() {

    companion object {
        const val TAG = "Di2BleService"
        const val CHANNEL_ID = "di2_media_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_DISCONNECT = "com.di2media.ACTION_DISCONNECT"
        const val PREFS_NAME = "di2_device"
        const val KEY_SAVED_ADDRESS = "saved_address"
        const val RECONNECT_DELAY_MS = 1500L
        const val KEY_RECONNECT_TIMEOUT_MIN = "reconnect_timeout_min"
        const val DEFAULT_RECONNECT_TIMEOUT_MIN = 10
        const val KEY_CLOSE_APP_ON_TIMEOUT = "close_app_on_timeout"
        const val ACTION_CLOSE_APP = "com.di2media.ACTION_CLOSE_APP"

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

    // ── Remembered device / auto reconnect ──
    private val handler = Handler(Looper.getMainLooper())
    private val devicePrefs by lazy { getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private var userDisconnected = false
    private var reconnectRunnable: Runnable? = null
    private var giveUpRunnable: Runnable? = null

    /** True after the search for the remembered device was stopped by the timeout. */
    var searchGaveUp = false
        private set

    /** Set by the UI: true while the app is on screen. */
    @Volatile
    var uiVisible = false

    /** When searching stops by timeout, also close the whole app (unless it is on screen). */
    fun getCloseAppOnTimeout(): Boolean =
        devicePrefs.getBoolean(KEY_CLOSE_APP_ON_TIMEOUT, false)

    fun setCloseAppOnTimeout(enabled: Boolean) {
        devicePrefs.edit().putBoolean(KEY_CLOSE_APP_ON_TIMEOUT, enabled).apply()
    }

    /** Minutes without a connection before searching stops (0 = never stop). */
    fun getReconnectTimeoutMin(): Int =
        devicePrefs.getInt(KEY_RECONNECT_TIMEOUT_MIN, DEFAULT_RECONNECT_TIMEOUT_MIN)

    fun setReconnectTimeoutMin(minutes: Int) {
        devicePrefs.edit().putInt(KEY_RECONNECT_TIMEOUT_MIN, minutes).apply()
    }

    /** MAC address of the last device we connected to successfully, or null. */
    val savedAddress: String?
        get() = devicePrefs.getString(KEY_SAVED_ADDRESS, null)

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
    private lateinit var clickCounter: ClickCounter
    lateinit var mappingConfig: ButtonMappingConfig
        private set

    // Reconnect automatically when Bluetooth is switched back on.
    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_ON -> {
                    if (!userDisconnected && !searchGaveUp) autoConnectIfSaved()
                }
                BluetoothAdapter.STATE_TURNING_OFF -> {
                    cancelReconnect()
                    cancelSearchTimer()
                    bluetoothGatt?.close()
                    bluetoothGatt = null
                    releaseAllHolds()
                    initialized = false
                    lastChannelValues = null
                    _channelStates.value = emptyMap()
                    _connectionState.value = ConnectionState.DISCONNECTED
                }
            }
        }
    }

    // ── Lifecycle ───────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        dispatcher = ActionDispatcher(this)
        mappingConfig = ButtonMappingConfig(this)
        clickCounter = ClickCounter(
            windowMs = { mappingConfig.getTripleWindowMs().toLong() },
            shortLongWindowMs = { mappingConfig.getShortLongWindowMs().toLong() },
            isTripleEnabled = { ch -> mappingConfig.hasTripleAction(ch) },
            isShortLongEnabled = { ch -> mappingConfig.hasShortLongAction(ch) },
            onShort = { ch -> dispatcher.dispatch(mappingConfig.getInstantAction(ch, PressType.SHORT)) },
            onDouble = { ch -> dispatcher.dispatch(mappingConfig.getInstantAction(ch, PressType.DOUBLE)) },
            onTriple = { ch -> dispatcher.dispatch(mappingConfig.getInstantAction(ch, PressType.TRIPLE)) },
        )
        createNotificationChannel()
        ContextCompat.registerReceiver(
            this, bluetoothStateReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            shutdown()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        try {
            unregisterReceiver(bluetoothStateReceiver)
        } catch (_: Exception) {
        }
        cancelReconnect()
        cancelSearchTimer()
        stopScan()
        bluetoothGatt?.close()
        if (::clickCounter.isInitialized) clickCounter.cancelAll()
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
        startForeground(NOTIFICATION_ID, buildNotification("Scanning"))

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

    /**
     * Connects to a device. [autoConnect] = true lets Android keep trying in the background
     * until the device shows up (used for the remembered device).
     */
    fun connectToDevice(address: String, autoConnect: Boolean = false) {
        stopScan()
        cancelReconnect()
        userDisconnected = false
        searchGaveUp = false
        // The give-up timer runs from the first attempt until we connect (it is not restarted by retries).
        if (address == savedAddress) startSearchTimer() else cancelSearchTimer()
        val device = bluetoothAdapter?.getRemoteDevice(address) ?: return
        bluetoothGatt?.close()
        _connectionState.value = ConnectionState.CONNECTING
        initialized = false
        lastChannelValues = null
        lastPressTypes.clear()
        try {
            startForeground(NOTIFICATION_ID, buildNotification("Connecting"))
        } catch (e: Exception) {
            Log.w(TAG, "startForeground failed", e)
        }
        bluetoothGatt = device.connectGatt(this, autoConnect, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    /**
     * Called when the app is opened: connect to the remembered device without scanning.
     * Opening the app counts as "I want to connect", so an earlier manual Disconnect is ignored.
     */
    fun autoConnectIfSaved() {
        if (_connectionState.value != ConnectionState.DISCONNECTED) return
        val address = savedAddress ?: return
        if (bluetoothAdapter?.isEnabled != true) return
        Log.i(TAG, "Auto-connecting to saved device $address")
        connectToDevice(address)
    }

    fun forgetDevice() {
        devicePrefs.edit().remove(KEY_SAVED_ADDRESS).apply()
        disconnect()
    }

    fun disconnect() {
        userDisconnected = true
        cancelReconnect()
        cancelSearchTimer()
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        releaseAllHolds()
        _connectionState.value = ConnectionState.DISCONNECTED
        _channelStates.value = emptyMap()
        initialized = false
        lastChannelValues = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    fun shutdown() {
        stopScan()
        disconnect()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Stops running volume ramps and pending click detection (e.g. when the link drops mid-hold). */
    private fun releaseAllHolds() {
        if (::clickCounter.isInitialized) clickCounter.cancelAll()
        if (::dispatcher.isInitialized) {
            lastPressTypes.forEach { (channel, type) ->
                if (type == PressType.LONG) dispatcher.onHoldStop(channel)
            }
        }
        lastPressTypes.clear()
    }

    private fun scheduleReconnect() {
        cancelReconnect()
        val r = Runnable {
            reconnectRunnable = null
            val address = savedAddress
            if (!userDisconnected && address != null && bluetoothAdapter?.isEnabled == true) {
                Log.i(TAG, "Reconnecting to saved device $address")
                connectToDevice(address, autoConnect = true)
            } else {
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        }
        reconnectRunnable = r
        handler.postDelayed(r, RECONNECT_DELAY_MS)
    }

    private fun cancelReconnect() {
        reconnectRunnable?.let { handler.removeCallbacks(it) }
        reconnectRunnable = null
    }

    private fun startSearchTimer() {
        if (giveUpRunnable != null) return // already running, keep the original start time
        val minutes = getReconnectTimeoutMin()
        if (minutes <= 0) return // never stop
        val r = Runnable { giveUpSearch() }
        giveUpRunnable = r
        handler.postDelayed(r, minutes * 60_000L)
    }

    private fun cancelSearchTimer() {
        giveUpRunnable?.let { handler.removeCallbacks(it) }
        giveUpRunnable = null
    }

    /** Stops searching for the remembered device to save battery. Opening the app searches again. */
    private fun giveUpSearch() {
        giveUpRunnable = null
        if (_connectionState.value == ConnectionState.CONNECTED) return
        Log.i(TAG, "No connection for ${getReconnectTimeoutMin()} min, stopping search")
        searchGaveUp = true
        cancelReconnect()
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        releaseAllHolds()
        initialized = false
        lastChannelValues = null
        _channelStates.value = emptyMap()
        _connectionState.value = ConnectionState.DISCONNECTED
        stopForeground(STOP_FOREGROUND_REMOVE)

        if (getCloseAppOnTimeout() && !uiVisible) closeAppCompletely()
    }

    /** Stops the service, closes the UI task and ends the process so nothing keeps running. */
    private fun closeAppCompletely() {
        Log.i(TAG, "Search stopped by timeout, closing the app")
        sendBroadcast(Intent(ACTION_CLOSE_APP).setPackage(packageName))
        shutdown()
        handler.postDelayed({ Process.killProcess(Process.myPid()) }, 800)
    }

    @Suppress("DEPRECATION")
    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "Connected to Di2")
                    devicePrefs.edit().putString(KEY_SAVED_ADDRESS, gatt.device.address).apply()
                    cancelSearchTimer()
                    _connectionState.value = ConnectionState.CONNECTED
                    gatt.discoverServices()
                    updateNotification("Connected")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "Disconnected from Di2")
                    releaseAllHolds()
                    _channelStates.value = emptyMap()
                    initialized = false
                    lastChannelValues = null
                    gatt.close()
                    if (bluetoothGatt === gatt) bluetoothGatt = null

                    // Link dropped (bike off, out of range...): keep trying to reach the remembered device.
                    if (!userDisconnected && gatt.device.address == savedAddress) {
                        _connectionState.value = ConnectionState.CONNECTING
                        updateNotification("Reconnecting")
                        scheduleReconnect()
                    } else {
                        _connectionState.value = ConnectionState.DISCONNECTED
                        updateNotification("Disconnected")
                    }
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

                // Leaving a hold (button released, or the unit reports another event): stop it.
                if (prevPressType == PressType.LONG && pressType != PressType.LONG) {
                    dispatcher.onHoldStop(channel)
                }

                when {
                    pressType == PressType.SHORT -> clickCounter.onShortPress(channel)
                    pressType == PressType.DOUBLE -> clickCounter.onDoublePress(channel)
                    pressType == PressType.LONG -> {
                        // The unit can send several frames while the button stays held.
                        // Only the first one starts the hold; later ones must not restart it
                        // (restarting would drop the short-then-long action for the plain long action).
                        if (prevPressType != PressType.LONG) {
                            val isShortLong = clickCounter.onLongPress(channel)
                            val holdType = if (isShortLong) PressType.SHORT_LONG else PressType.LONG
                            dispatcher.onHoldStart(channel, mappingConfig.getHoldAction(channel, holdType))
                        } else {
                            Log.i(TAG, "CH$channel: repeated long frame ignored (hold in progress)")
                        }
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
            CHANNEL_ID, getString(R.string.app_name),
            NotificationManager.IMPORTANCE_LOW
        )
        (getSystemService(NotificationManager::class.java)).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = Intent(this, Di2BleService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this, 1, disconnectIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(contentPendingIntent)
            .addAction(Notification.Action.Builder(null, "Disconnect", disconnectPendingIntent).build())
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }
}

enum class ConnectionState { DISCONNECTED, SCANNING, CONNECTING, CONNECTED }
// TRIPLE and SHORT_LONG are synthesized in software (ClickCounter); the Di2 unit never reports them.
enum class PressType { SHORT, LONG, DOUBLE, TRIPLE, SHORT_LONG }
data class DiscoveredDevice(val name: String, val address: String, val rssi: Int)
