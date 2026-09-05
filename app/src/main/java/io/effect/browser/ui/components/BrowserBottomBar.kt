package io.effect.browser.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Primary controls, pinned to the bottom of the screen so they stay inside thumb reach on a
 * phone held in one hand.
 */
@Composable
fun BrowserBottomBar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    isLoading: Boolean,
    isBookmarked: Boolean,
    tabCount: Int,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReloadOrStop: () -> Unit,
    onToggleBookmark: () -> Unit,
    onShowTabs: () -> Unit,
    onShowBookmarks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, enabled = canGoBack) {
            Icon(BackGlyph, contentDescription = "Back", modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = onForward, enabled = canGoForward) {
            Icon(ForwardGlyph, contentDescription = "Forward", modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = onReloadOrStop) {
            Icon(
                imageVector = if (isLoading) StopGlyph else ReloadGlyph,
                contentDescription = if (isLoading) "Stop" else "Reload",
                modifier = Modifier.size(22.dp),
            )
        }
        IconButton(onClick = onToggleBookmark) {
            Icon(
                imageVector = StarGlyph,
                contentDescription = if (isBookmarked) "Remove bookmark" else "Add bookmark",
                tint = if (isBookmarked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(22.dp),
            )
        }
        IconButton(onClick = onShowBookmarks) {
            Icon(ListGlyph, contentDescription = "Bookmarks", modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = onShowTabs) {
            Icon(TabsGlyph, contentDescription = "Tabs ($tabCount)", modifier = Modifier.size(22.dp))
        }
    }
}
