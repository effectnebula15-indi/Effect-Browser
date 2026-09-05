package io.effect.browser.gecko

import io.effect.browser.domain.model.Container
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings

/**
 * Keeps one live [GeckoSession] per open tab.
 *
 * The isolation guarantee lives on line `contextId(container.contextId)` below: two sessions
 * built with different contextIds get different cookie jars, caches and DOM storage, so the same
 * site can be logged into twice at once.
 */
class GeckoSessionPool(
    private val runtimeHolder: GeckoRuntimeHolder,
) {

    private val sessions = mutableMapOf<Long, GeckoSession>()

    fun acquire(tabId: Long, container: Container): GeckoSession =
        sessions.getOrPut(tabId) { createSession(container) }

    fun peek(tabId: Long): GeckoSession? = sessions[tabId]

    fun release(tabId: Long) {
        sessions.remove(tabId)?.close()
    }

    fun releaseAll() {
        sessions.values.forEach(GeckoSession::close)
        sessions.clear()
    }

    private fun createSession(container: Container): GeckoSession {
        val settings = GeckoSessionSettings.Builder()
            // This, and only this, is what separates one container's storage from another's.
            .contextId(container.contextId)
            .usePrivateMode(false)
            .useTrackingProtection(false)
            .build()

        return GeckoSession(settings).apply {
            open(runtimeHolder.runtime)
        }
    }
}
