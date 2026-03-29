package com.di2media.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.di2media.mapping.ButtonAction
import com.di2media.mapping.ButtonBinding
import com.di2media.service.PressType

data class ChannelMappings(
    val short: ButtonAction,
    val long: ButtonAction,
    val double: ButtonAction,
)

@Composable
fun ButtonMonitorScreen(
    channelStates: Map<Int, PressType?>,
    channelMappings: Map<Int, ChannelMappings>,
    onChannelClick: (Int) -> Unit,
    onDisconnectClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Di2 Media Control", style = MaterialTheme.typography.headlineMedium)

        Text(
            "Connected",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(16.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            (1..4).chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { ch ->
                        ChannelIndicator(
                            channel = ch,
                            pressType = channelStates[ch],
                            mappings = channelMappings[ch],
                            onClick = { onChannelClick(ch) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        OutlinedButton(onClick = onDisconnectClick, modifier = Modifier.fillMaxWidth()) {
            Text("Disconnect")
        }
    }
}

@Composable
private fun ChannelIndicator(
    channel: Int,
    pressType: PressType?,
    mappings: ChannelMappings?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor by animateColorAsState(
        targetValue = when (pressType) {
            PressType.SHORT -> Color(0xFF2196F3)  // blue
            PressType.LONG -> Color(0xFFFF9800)    // orange
            PressType.DOUBLE -> Color(0xFF4CAF50)  // green
            null -> Color(0xFF424242)              // gray
        },
        animationSpec = tween(durationMillis = 150),
        label = "channelColor"
    )

    val stateLabel = when (pressType) {
        PressType.SHORT -> "Short Press"
        PressType.LONG -> "Long Press"
        PressType.DOUBLE -> "Double Press"
        null -> "Idle"
    }

    Column(modifier = modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Card(
            modifier = Modifier.fillMaxWidth().aspectRatio(0.85f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = backgroundColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "CH $channel",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stateLabel,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }

        if (mappings != null) {
            Spacer(Modifier.height(4.dp))
            MappingLabel("S", mappings.short.label)
            MappingLabel("L", mappings.long.label)
            MappingLabel("D", mappings.double.label)
        }
    }
}

@Composable
private fun MappingLabel(prefix: String, label: String) {
    if (label == "None") return
    Text(
        "$prefix: $label",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}
