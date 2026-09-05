package io.effect.browser.domain.model

/**
 * How a container reaches the network.
 *
 * This is not a cosmetic flag: it decides which OS process — and therefore which
 * [org.mozilla.geckoview.GeckoRuntime] — a container's tabs are rendered in.
 * See `docs/ARCHITECTURE.md` for why per-container proxying cannot be done
 * inside a single runtime.
 */
enum class NetworkMode {
    /** Direct connection, no proxy. */
    DIRECT,

    /** Every request routed through the app's embedded tor SOCKS port. */
    TOR,
}
