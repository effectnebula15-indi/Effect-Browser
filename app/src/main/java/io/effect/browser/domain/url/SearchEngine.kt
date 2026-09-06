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
    /** Page a new tab opens on for a container using this engine. */
    val homeUrl: String,
) {
    GOOGLE("Google", "https://www.google.com/search?q=%s", "https://www.google.com/"),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s", "https://duckduckgo.com/"),

    /**
     * DuckDuckGo's v3 onion service. Reachable only over Tor; the query stays inside the
     * network and never transits an exit node.
     */
    DUCKDUCKGO_ONION(
        "DuckDuckGo (onion)",
        "https://duckduckgogg42xjoc72x3sjasowoarfbgcmvfimaftt6twagswzczad.onion/?q=%s",
        "https://duckduckgogg42xjoc72x3sjasowoarfbgcmvfimaftt6twagswzczad.onion/",
    ),
    ;

    fun urlFor(query: String): String = template.format(UrlEncoding.encodeQuery(query))

    companion object {
        fun defaultFor(mode: NetworkMode): SearchEngine = when (mode) {
            NetworkMode.DIRECT -> GOOGLE
            NetworkMode.TOR -> DUCKDUCKGO
        }

        /**
         * Where a new tab starts.
         *
         * Google for ordinary containers. Tor containers get DuckDuckGo instead for the reason
         * above — opening every tor tab on a page that reliably serves a CAPTCHA, or refuses
         * the exit node outright, would make the browser look broken on first launch.
         */
        fun homeUrlFor(mode: NetworkMode): String = defaultFor(mode).homeUrl
    }
}
