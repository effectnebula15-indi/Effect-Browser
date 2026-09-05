package io.effect.browser.domain.model

/**
 * An isolated browsing identity.
 *
 * [contextId] is handed to `GeckoSessionSettings.Builder.contextId()`, which stamps it into
 * Gecko's `geckoViewSessionContextId` origin attribute. Cookies, localStorage, sessionStorage,
 * IndexedDB and the HTTP cache are all partitioned by that attribute, so two containers can hold
 * two live logins to the same site at once.
 *
 * [contextId] is generated once and then persisted forever. It must never be reused for a
 * different identity — doing so would hand the new container the old one's cookie jar.
 */
data class Container(
    val id: Long,
    val name: String,
    val colorArgb: Int,
    val networkMode: NetworkMode,
    val contextId: String,
    val createdAt: Long,
) {
    val isTor: Boolean get() = networkMode == NetworkMode.TOR
}
