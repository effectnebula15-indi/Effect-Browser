package io.effect.browser.tor

/**
 * What the embedded tor daemon is doing.
 *
 * Only [Ready] carries a SOCKS port, and only [Ready] permits a page load in the tor process.
 * The other states are all "do not send traffic".
 */
sealed interface TorStatus {

    data object Stopped : TorStatus

    /** Tor is launching or building circuits. [bootstrapPercent] is 0..100. */
    data class Starting(val bootstrapPercent: Int) : TorStatus

    /** Tor is bootstrapped and its SOCKS port is accepting connections. */
    data class Ready(val socksPort: Int) : TorStatus

    data class Failed(val message: String) : TorStatus

    val isReady: Boolean get() = this is Ready
}
