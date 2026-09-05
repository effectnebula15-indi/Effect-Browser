package io.effect.browser.gecko

import android.util.Log
import org.mozilla.geckoview.GeckoPreferenceController

/**
 * Applies tor proxying to a [org.mozilla.geckoview.GeckoRuntime] via Gecko preferences.
 *
 * ## Why preferences and not a session API
 *
 * GeckoView exposes no per-session proxy setting. `GeckoSessionSettings.Builder.contextId()`
 * partitions *storage* (it writes Gecko's `geckoViewSessionContextId` origin attribute) but says
 * nothing about *routing*. Routing is controlled by the `network.proxy.*` preference branch,
 * which is global to the Gecko process. That is precisely why tor containers get their own OS
 * process — see `docs/ARCHITECTURE.md`.
 *
 * ## Fail closed
 *
 * [applyBlackhole] is called before the tor process ever renders anything, and points Gecko at a
 * port with nothing behind it. Combined with `network.proxy.failover_direct = false`, a request
 * made before tor is up cannot silently fall back to a direct connection — it fails. The port is
 * only swapped for tor's real SOCKS port once tor reports it is listening.
 */
object GeckoProxyPrefs {

    private const val TAG = "GeckoProxyPrefs"

    private const val PROXY_TYPE_DIRECT = 0
    private const val PROXY_TYPE_MANUAL = 1

    /** Nothing listens here. Used until tor's real port is known. */
    private const val BLACKHOLE_PORT = 1

    private const val LOOPBACK = "127.0.0.1"

    /**
     * Locks the runtime into "SOCKS or nothing" and points it at a dead port.
     *
     * Must run before the first page load in the tor process.
     */
    suspend fun applyBlackhole() {
        applyHardening()
        applySocks(port = BLACKHOLE_PORT)
    }

    /** Points the runtime at the live tor SOCKS port. */
    suspend fun applySocks(port: Int) {
        setInt("network.proxy.type", PROXY_TYPE_MANUAL)
        setString("network.proxy.socks", LOOPBACK)
        setInt("network.proxy.socks_port", port)
        setInt("network.proxy.socks_version", 5)
        // Resolve hostnames through tor rather than the device resolver. Without this every
        // navigation leaks the hostname to the local DNS server before tor is ever consulted.
        setBool("network.proxy.socks_remote_dns", true)
    }

    /**
     * The settings that make the proxy meaningful. Each one closes a path that would otherwise
     * carry traffic around the SOCKS port.
     */
    private suspend fun applyHardening() {
        // The single most important one: never substitute a direct connection when the proxy
        // is unreachable. Default is true, which would turn "tor is down" into "browsing in
        // the clear without telling the user".
        setBool("network.proxy.failover_direct", false)

        // Do not exempt anything from the proxy, including RFC1918 and localhost.
        setString("network.proxy.no_proxies_on", "")
        setBool("network.proxy.allow_hijacking_localhost", true)

        // WebRTC discovers and reports local/public IPs over UDP, outside the SOCKS path.
        setBool("media.peerconnection.enabled", false)

        // Speculative machinery opens sockets for URLs the user has not navigated to.
        setBool("network.dns.disablePrefetch", true)
        setBool("network.predictor.enabled", false)
        setBool("network.prefetch-next", false)
        setInt("network.http.speculative-parallel-limit", 0)

        // Safe Browsing would send URL hashes to Google from a tor-only process.
        setBool("browser.safebrowsing.malware.enabled", false)
        setBool("browser.safebrowsing.phishing.enabled", false)

        // Captive portal and connectivity probes phone home to detectportal.firefox.com.
        setBool("network.captive-portal-service.enabled", false)
        setBool("network.connectivity-service.enabled", false)
    }

    private suspend fun setInt(name: String, value: Int) = set(name) {
        GeckoPreferenceController.setGeckoPref(name, value, BRANCH)
    }

    private suspend fun setBool(name: String, value: Boolean) = set(name) {
        GeckoPreferenceController.setGeckoPref(name, value, BRANCH)
    }

    private suspend fun setString(name: String, value: String) = set(name) {
        GeckoPreferenceController.setGeckoPref(name, value, BRANCH)
    }

    private val BRANCH get() = GeckoPreferenceController.PREF_BRANCH_USER

    private suspend inline fun set(
        name: String,
        block: () -> org.mozilla.geckoview.GeckoResult<Void>,
    ) {
        try {
            block().await()
        } catch (t: Throwable) {
            // A pref that Gecko dropped between versions must not take the process down, but it
            // must be loud: some of these are load-bearing for the no-leak guarantee.
            Log.e(TAG, "Failed to set $name", t)
            throw GeckoProxyPrefException(name, t)
        }
    }
}

/** Raised when a proxy-related preference could not be applied. */
class GeckoProxyPrefException(
    prefName: String,
    cause: Throwable,
) : IllegalStateException("Could not apply proxy preference '$prefName'", cause)
