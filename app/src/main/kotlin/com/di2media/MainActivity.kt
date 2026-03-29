package com.di2media

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
import com.di2media.service.ConnectionState
import com.di2media.service.Di2BleService
import com.di2media.ui.ButtonMonitorScreen
import com.di2media.ui.DeviceSetupScreen

class MainActivity : ComponentActivity() {

    private val bleService = mutableStateOf<Di2BleService?>(null)

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
            bleService.value = (binder as Di2BleService.LocalBinder).getService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bleService.value = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissionsAndStart()

        setContent {
            MaterialTheme {
                val service = bleService.value ?: return@MaterialTheme

                val connectionState by service.connectionState.collectAsState()
                val channelStates by service.channelStates.collectAsState()
                val devices by service.discoveredDevices.collectAsState()

                when (connectionState) {
                    ConnectionState.CONNECTED -> ButtonMonitorScreen(
                        channelStates = channelStates,
                        onDisconnectClick = {
                            service.shutdown()
                            finish()
                        }
                    )
                    else -> DeviceSetupScreen(
                        connectionState = connectionState,
                        devices = devices,
                        onScanClick = { service.startScan() },
                        onDeviceClick = { address -> service.connectToDevice(address) },
                        onDisconnectClick = {
                            service.shutdown()
                            finish()
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
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
        startForegroundService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
}
