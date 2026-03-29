package com.di2media.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.di2media.service.PressType

@Composable
fun ButtonMonitorScreen(
    channelStates: Map<Int, PressType?>,
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

    Card(
        modifier = modifier.aspectRatio(0.8f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "CH $channel",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stateLabel,
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}
