package io.effect.browser.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.effect.browser.domain.model.Container

/**
 * Horizontal ribbon of coloured container chips — the primary way to change identity.
 *
 * Tor containers carry a lock glyph, because tapping one crosses into the other process and the
 * user should be able to tell before they tap.
 */
@Composable
fun ContainerChips(
    containers: List<Container>,
    activeContainerId: Long?,
    onSelect: (Container) -> Unit,
    onLongPress: (Container) -> Unit,
    onAddContainer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(containers, key = { it.id }) { container ->
            ContainerChip(
                container = container,
                selected = container.id == activeContainerId,
                onClick = { onSelect(container) },
                onLongClick = { onLongPress(container) },
            )
        }
        item {
            AddChip(onClick = onAddContainer)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContainerChip(
    container: Container,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val accent = Color(container.colorArgb)
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) accent.copy(alpha = 0.22f) else Color.Transparent)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) accent else accent.copy(alpha = 0.45f),
                shape = CircleShape,
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (container.isTor) {
            Icon(
                imageVector = TorGlyph,
                contentDescription = "Routed through Tor",
                tint = accent,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = container.name,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AddChip(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "+", style = MaterialTheme.typography.labelLarge)
    }
}
