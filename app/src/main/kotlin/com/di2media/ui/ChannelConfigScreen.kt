package com.di2media.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.di2media.mapping.*
import com.di2media.service.PressType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelConfigScreen(
    channel: Int,
    currentMappings: ChannelMappings,
    onMappingChanged: (PressType, ButtonAction) -> Unit,
    tripleWindowMs: Int,
    onTripleWindowChanged: (Int) -> Unit,
    shortLongWindowMs: Int,
    onShortLongWindowChanged: (Int) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CH $channel Configuration") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            ActionSection(
                title = "Short Press",
                selected = currentMappings.short,
                options = InstantAction.entries,
                onSelected = { onMappingChanged(PressType.SHORT, it) }
            )

            ActionSection(
                title = "Long Press",
                selected = currentMappings.long,
                options = HoldAction.entries,
                onSelected = { onMappingChanged(PressType.LONG, it) }
            )

            ActionSection(
                title = "Double Press",
                selected = currentMappings.double,
                options = InstantAction.entries,
                onSelected = { onMappingChanged(PressType.DOUBLE, it) }
            )

            ActionSection(
                title = "Triple Press",
                selected = currentMappings.triple,
                options = InstantAction.entries,
                onSelected = { onMappingChanged(PressType.TRIPLE, it) }
            )

            ActionSection(
                title = "Short then Long Press",
                selected = currentMappings.shortLong,
                options = HoldAction.entries,
                onSelected = { onMappingChanged(PressType.SHORT_LONG, it) }
            )

            WindowSlider(
                title = "Triple press window",
                description = "Max time between clicks. Increase if triple press is detected as double + single.",
                initialMs = tripleWindowMs,
                range = 300f..1500f,
                steps = 11,
                onFinished = onTripleWindowChanged
            )

            WindowSlider(
                title = "Short then long window",
                description = "Max time from the short press until the long press starts. Increase if it is detected as short + long separately.",
                initialMs = shortLongWindowMs,
                range = 500f..2500f,
                steps = 19,
                onFinished = onShortLongWindowChanged
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun <T : ButtonAction> ActionSection(
    title: String,
    selected: ButtonAction,
    options: List<T>,
    onSelected: (T) -> Unit,
) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Column(Modifier.selectableGroup()) {
            options.forEach { action ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = action == selected,
                            onClick = { onSelected(action) },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 8.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = action == selected,
                        onClick = null
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(action.label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun WindowSlider(
    title: String,
    description: String,
    initialMs: Int,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onFinished: (Int) -> Unit,
) {
    var value by remember { mutableStateOf(initialMs.toFloat()) }
    Column {
        Text("$title: ${value.toInt()} ms", style = MaterialTheme.typography.titleMedium)
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onFinished(value.toInt()) },
            valueRange = range,
            steps = steps
        )
    }
}
