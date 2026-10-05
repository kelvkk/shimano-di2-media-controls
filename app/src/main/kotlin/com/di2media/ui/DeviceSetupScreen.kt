package com.di2media.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.di2media.R
import androidx.compose.ui.unit.dp
import com.di2media.service.ConnectionState
import com.di2media.service.DiscoveredDevice
import kotlin.math.roundToInt

@Composable
fun DeviceSetupScreen(
    connectionState: ConnectionState,
    devices: List<DiscoveredDevice>,
    onScanClick: () -> Unit,
    onDeviceClick: (String) -> Unit,
    onDisconnectClick: () -> Unit,
    savedAddress: String? = null,
    onReconnectClick: () -> Unit = {},
    onForgetClick: () -> Unit = {},
    reconnectTimeoutMin: Int = 10,
    onReconnectTimeoutChanged: (Int) -> Unit = {},
    closeAppOnTimeout: Boolean = false,
    onCloseAppOnTimeoutChanged: (Boolean) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)

        StatusIndicator(connectionState)

        Spacer(Modifier.height(8.dp))

        when (connectionState) {
            ConnectionState.DISCONNECTED -> {
                if (savedAddress != null) {
                    Button(onClick = onReconnectClick, modifier = Modifier.fillMaxWidth()) {
                        Text("Reconnect to saved device")
                    }
                    Text(
                        savedAddress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = onScanClick, modifier = Modifier.fillMaxWidth()) {
                        Text("Scan for a different device")
                    }
                    TextButton(onClick = onForgetClick) {
                        Text("Forget saved device")
                    }
                } else {
                    Button(onClick = onScanClick, modifier = Modifier.fillMaxWidth()) {
                        Text("Scan for Di2 Devices")
                    }
                }
            }
            ConnectionState.SCANNING -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text("Scanning...")
                }
            }
            ConnectionState.CONNECTING -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text("Connecting...")
                }
                OutlinedButton(onClick = onDisconnectClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            }
            ConnectionState.CONNECTED -> {
                OutlinedButton(onClick = onDisconnectClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Disconnect")
                }
            }
        }

        if (savedAddress != null &&
            (connectionState == ConnectionState.DISCONNECTED || connectionState == ConnectionState.CONNECTING)
        ) {
            ReconnectTimeoutSetting(
                reconnectTimeoutMin, onReconnectTimeoutChanged,
                closeAppOnTimeout, onCloseAppOnTimeoutChanged
            )
        }

        if (connectionState == ConnectionState.SCANNING && devices.isNotEmpty()) {
            Text(
                "Tap a device to connect",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(devices) { device ->
                    DeviceCard(device) { onDeviceClick(device.address) }
                }
            }
        }
    }
}

@Composable
private fun StatusIndicator(state: ConnectionState) {
    val (text, color) = when (state) {
        ConnectionState.CONNECTED -> "Connected" to MaterialTheme.colorScheme.primary
        ConnectionState.SCANNING -> "Scanning" to MaterialTheme.colorScheme.tertiary
        ConnectionState.CONNECTING -> "Connecting" to MaterialTheme.colorScheme.tertiary
        ConnectionState.DISCONNECTED -> "Disconnected" to MaterialTheme.colorScheme.error
    }
    Text(text, style = MaterialTheme.typography.titleMedium, color = color)
}

@Composable
private fun DeviceCard(device: DiscoveredDevice, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(device.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "${device.rssi} dBm",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private val TIMEOUT_OPTIONS = listOf(0, 1, 2, 5, 10, 15, 30, 60)

@Composable
private fun ReconnectTimeoutSetting(
    minutes: Int,
    onChanged: (Int) -> Unit,
    closeApp: Boolean,
    onCloseAppChanged: (Boolean) -> Unit,
) {
    var index by remember {
        mutableStateOf(TIMEOUT_OPTIONS.indexOf(minutes).let { if (it < 0) 4 else it }.toFloat())
    }
    val current = TIMEOUT_OPTIONS[index.roundToInt()]
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            if (current == 0) "Keep searching: never stop" else "Stop searching after $current min",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            "Without a connection for this long, the app stops searching to save battery. Opening the app searches again.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = index,
            onValueChange = { index = it },
            onValueChangeFinished = { onChanged(TIMEOUT_OPTIONS[index.roundToInt()]) },
            valueRange = 0f..(TIMEOUT_OPTIONS.size - 1).toFloat(),
            steps = TIMEOUT_OPTIONS.size - 2
        )

        var closeChecked by remember { mutableStateOf(closeApp) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Close the app when searching stops", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Fully exits the app after the time above. If the app is on screen at that moment, it stays open.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = closeChecked,
                enabled = current != 0,
                onCheckedChange = {
                    closeChecked = it
                    onCloseAppChanged(it)
                }
            )
        }
    }
}
