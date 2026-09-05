package io.effect.browser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import io.effect.browser.domain.model.Container
import io.effect.browser.domain.model.NetworkMode
import io.effect.browser.ui.theme.ContainerPalette

/**
 * Create or edit a container: name, colour, and the network it uses.
 *
 * The network toggle is the consequential control here — flipping it moves the container's tabs
 * into the other process the next time they are opened.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContainerEditorSheet(
    existing: Container?,
    onDismiss: () -> Unit,
    onSave: (name: String, colorArgb: Int, mode: NetworkMode) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var color by remember {
        mutableStateOf(existing?.let { Color(it.colorArgb) } ?: ContainerPalette.first())
    }
    var mode by remember { mutableStateOf(existing?.networkMode ?: NetworkMode.DIRECT) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = if (existing == null) "New container" else "Edit container",
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ContainerPalette.forEach { swatch ->
                    Row(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(swatch)
                            .border(
                                width = if (swatch == color) 3.dp else 0.dp,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                                shape = CircleShape,
                            )
                            .clickable { color = swatch },
                    ) {}
                }
            }

            Text("Network", style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode == NetworkMode.DIRECT,
                    onClick = { mode = NetworkMode.DIRECT },
                    label = { Text("Direct") },
                )
                FilterChip(
                    selected = mode == NetworkMode.TOR,
                    onClick = { mode = NetworkMode.TOR },
                    label = { Text("Through Tor") },
                )
            }

            if (mode == NetworkMode.TOR) {
                Text(
                    text = "Tabs in this container open in a separate, Tor-only process. " +
                        "The first connection can take a while to bootstrap.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { onSave(name.trim(), color.toArgb(), mode) },
                    enabled = name.isNotBlank(),
                ) { Text("Save") }

                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete") }
                }
            }
        }
    }
}
