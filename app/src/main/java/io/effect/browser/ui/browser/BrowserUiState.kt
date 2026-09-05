package io.effect.browser.ui.browser

import io.effect.browser.domain.model.Container
import io.effect.browser.domain.model.NetworkMode
import io.effect.browser.domain.model.Tab
import io.effect.browser.tor.TorStatus

data class BrowserUiState(
    val networkMode: NetworkMode = NetworkMode.DIRECT,
    /** Every container, both modes — the chip row is how you cross between processes. */
    val containers: List<Container> = emptyList(),
    val activeContainer: Container? = null,
    val tabs: List<Tab> = emptyList(),
    val activeTabId: Long? = null,
    val addressText: String = "",
    val currentUrl: String = "",
    val pageTitle: String = "",
    val progress: Int = 0,
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isBookmarked: Boolean = false,
    val torStatus: TorStatus = TorStatus.Stopped,
    val readyToLoad: Boolean = true,
) {
    /**
     * In the tor process this is false until tor has bootstrapped and Gecko has been pointed at
     * its SOCKS port. Loading anything before then would either fail or, worse, leak.
     */
    val canNavigate: Boolean
        get() = readyToLoad && (networkMode == NetworkMode.DIRECT || torStatus.isReady)
}

/** One-shot things the screen has to do that state alone cannot express. */
sealed interface BrowserEffect {
    /**
     * The chosen container lives in the other process. The activity turns this into an Intent.
     */
    data class SwitchProcess(val containerId: Long, val target: NetworkMode) : BrowserEffect
}
