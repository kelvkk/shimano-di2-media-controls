package com.di2media.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
