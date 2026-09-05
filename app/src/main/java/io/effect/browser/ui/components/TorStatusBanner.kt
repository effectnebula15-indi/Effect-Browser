package io.effect.browser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.effect.browser.tor.TorStatus

/**
 * Connection state for the embedded tor daemon.
 *
 * Shown whenever tor is not yet usable. The first bootstrap on a cold install regularly takes
 * tens of seconds, and without this the browser would just look broken.
 */
@Composable
fun TorStatusBanner(status: TorStatus, modifier: Modifier = Modifier) {
    val (message, tint) = when (status) {
        is TorStatus.Stopped -> "Tor is not running" to Color(0xFF7D8894)
        is TorStatus.Starting -> "Connecting to Tor… ${status.bootstrapPercent}%" to Color(0xFFE0A020)
        is TorStatus.Ready -> return
        is TorStatus.Failed -> "Tor failed: ${status.message}" to Color(0xFFD4573F)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(tint.copy(alpha = 0.15f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (status is TorStatus.Starting) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = tint)
        }
        Text(text = message, style = MaterialTheme.typography.bodySmall, color = tint)
    }
}
