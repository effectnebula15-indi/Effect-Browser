package io.effect.browser.ui.browser

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/**
 * Hosts the native [GeckoView] inside Compose.
 *
 * A [GeckoSession] may be attached to at most one view at a time, so the view is released before
 * a different session is bound and again when the composable leaves the tree — otherwise
 * switching tabs throws.
 */
@Composable
fun GeckoViewHost(
    session: GeckoSession,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context -> GeckoView(context) },
        update = { view ->
            if (view.session !== session) {
                view.releaseSession()
                view.setSession(session)
            }
        },
        onRelease = { view -> view.releaseSession() },
    )
}
