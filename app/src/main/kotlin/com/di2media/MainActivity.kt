package com.di2media

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import com.di2media.service.ConnectionState
import com.di2media.service.Di2BleService
import com.di2media.mapping.ButtonBinding
import com.di2media.service.PressType
import com.di2media.ui.ButtonMonitorScreen
import com.di2media.ui.ChannelConfigScreen
import com.di2media.ui.ChannelMappings
import com.di2media.ui.DeviceSetupScreen

class MainActivity : ComponentActivity() {

    private val bleService = mutableStateOf<Di2BleService?>(null)
    private val selectedChannel = mutableStateOf<Int?>(null)
    private val mappingVersion = mutableStateOf(0)
    private var freshLaunch = false

    // The service asks the UI to close when it stops searching and "close app" is enabled.
    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            finishAndRemoveTask()
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            startAndBindService()
        } else {
            Toast.makeText(this, "BLE permissions required", Toast.LENGTH_LONG).show()
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as Di2BleService.LocalBinder).getService()
            bleService.value = service
            service.uiVisible = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (freshLaunch) {
                freshLaunch = false
                service.autoConnectIfSaved()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bleService.value = null
        }
    }

    override fun onStart() {
        super.onStart()
        bleService.value?.uiVisible = true
        // The app became visible again: if the search was stopped by the timeout, search again.
        bleService.value?.let { if (it.searchGaveUp) it.autoConnectIfSaved() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        freshLaunch = savedInstanceState == null
        ContextCompat.registerReceiver(
            this, closeReceiver, IntentFilter(Di2BleService.ACTION_CLOSE_APP),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        requestPermissionsAndStart()

        setContent {
            MaterialTheme {
                val service = bleService.value ?: return@MaterialTheme

                val connectionState by service.connectionState.collectAsState()
                val channelStates by service.channelStates.collectAsState()
                val devices by service.discoveredDevices.collectAsState()

                // Read mappingVersion to trigger recomposition on config changes
                @Suppress("UNUSED_VARIABLE")
                val version = mappingVersion.value
                val channelMappings = (1..4).associateWith { ch ->
                    val config = service.mappingConfig
                    ChannelMappings(
                        short = config.getAction(ch, PressType.SHORT),
                        long = config.getAction(ch, PressType.LONG),
                        double = config.getAction(ch, PressType.DOUBLE),
                        triple = config.getAction(ch, PressType.TRIPLE),
                        shortLong = config.getAction(ch, PressType.SHORT_LONG),
                    )
                }

                val configChannel = selectedChannel.value
                when {
                    configChannel != null -> ChannelConfigScreen(
                        channel = configChannel,
                        currentMappings = channelMappings[configChannel] ?: return@MaterialTheme,
                        onMappingChanged = { pressType, action ->
                            service.mappingConfig.setMapping(
                                ButtonBinding(configChannel, pressType), action
                            )
                            mappingVersion.value++
                        },
                        tripleWindowMs = service.mappingConfig.getTripleWindowMs(),
                        onTripleWindowChanged = { service.mappingConfig.setTripleWindowMs(it) },
                        shortLongWindowMs = service.mappingConfig.getShortLongWindowMs(),
                        onShortLongWindowChanged = { service.mappingConfig.setShortLongWindowMs(it) },
                        onBack = { selectedChannel.value = null }
                    )
                    connectionState == ConnectionState.CONNECTED -> ButtonMonitorScreen(
                        channelStates = channelStates,
                        channelMappings = channelMappings,
                        onChannelClick = { selectedChannel.value = it },
                        onDisconnectClick = { service.disconnect() }
                    )
                    else -> DeviceSetupScreen(
                        connectionState = connectionState,
                        devices = devices,
                        onScanClick = { service.startScan() },
                        onDeviceClick = { address -> service.connectToDevice(address) },
                        onDisconnectClick = { service.disconnect() },
                        savedAddress = service.savedAddress,
                        onReconnectClick = { service.savedAddress?.let { service.connectToDevice(it) } },
                        reconnectTimeoutMin = service.getReconnectTimeoutMin(),
                        onReconnectTimeoutChanged = { service.setReconnectTimeoutMin(it) },
                        closeAppOnTimeout = service.getCloseAppOnTimeout(),
                        onCloseAppOnTimeoutChanged = { service.setCloseAppOnTimeout(it) },
                        onForgetClick = {
                            service.forgetDevice()
                            mappingVersion.value++
                        }
                    )
                }
            }
        }
    }

    override fun onStop() {
        bleService.value?.uiVisible = false
        super.onStop()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(closeReceiver)
        } catch (_: Exception) {
        }
        if (bleService.value != null) unbindService(serviceConnection)
        super.onDestroy()
    }

    private fun requestPermissionsAndStart() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms += Manifest.permission.BLUETOOTH_SCAN
            perms += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.POST_NOTIFICATIONS
        }

        val needed = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isEmpty()) {
            startAndBindService()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun startAndBindService() {
        val intent = Intent(this, Di2BleService::class.java)
        // Started (not only bound) so the connection survives when the UI is closed.
        startService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
}
