package com.di2media.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.di2media.R
import androidx.compose.ui.unit.dp
import com.di2media.service.ConnectionState
import com.di2media.service.DiscoveredDevice

@Composable
fun DeviceSetupScreen(
    connectionState: ConnectionState,
    devices: List<DiscoveredDevice>,
    onScanClick: () -> Unit,
    onDeviceClick: (String) -> Unit,
    onDisconnectClick: () -> Unit,
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
                Button(onClick = onScanClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Scan for Di2 Devices")
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
            }
            ConnectionState.CONNECTED -> {
                OutlinedButton(onClick = onDisconnectClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Disconnect")
                }
            }
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
