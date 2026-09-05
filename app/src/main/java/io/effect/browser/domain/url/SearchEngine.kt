package io.effect.browser.domain.url

import io.effect.browser.domain.model.NetworkMode

/**
 * Search providers the address bar can fall back to when input isn't a URL.
 *
 * Tor containers deliberately do not default to Google: Google aggressively CAPTCHAs and
 * often outright blocks exit-node traffic, which makes it useless as a default over Tor.
 * DuckDuckGo serves Tor traffic without challenge and also publishes an onion service, so
 * the query never has to leave the Tor network at all.
 */
enum class SearchEngine(
    val displayName: String,
    private val template: String,
) {
    GOOGLE("Google", "https://www.google.com/search?q=%s"),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s"),

    /**
     * DuckDuckGo's v3 onion service. Reachable only over Tor; the query stays inside the
     * network and never transits an exit node.
     */
    DUCKDUCKGO_ONION(
        "DuckDuckGo (onion)",
        "https://duckduckgogg42xjoc72x3sjasowoarfbgcmvfimaftt6twagswzczad.onion/?q=%s",
    ),
    ;

    fun urlFor(query: String): String = template.format(UrlEncoding.encodeQuery(query))

    companion object {
        fun defaultFor(mode: NetworkMode): SearchEngine = when (mode) {
            NetworkMode.DIRECT -> GOOGLE
            NetworkMode.TOR -> DUCKDUCKGO
        }
    }
}
