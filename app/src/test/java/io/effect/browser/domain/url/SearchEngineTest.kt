package io.effect.browser.domain.url

import io.effect.browser.domain.model.NetworkMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineTest {

    @Test
    fun `direct containers default to google`() {
        assertEquals(SearchEngine.GOOGLE, SearchEngine.defaultFor(NetworkMode.DIRECT))
    }

    @Test
    fun `tor containers do not default to google`() {
        // Google challenges or blocks exit-node traffic, which makes it a poor default over Tor.
        val engine = SearchEngine.defaultFor(NetworkMode.TOR)
        assertEquals(SearchEngine.DUCKDUCKGO, engine)
    }

    @Test
    fun `onion engine stays inside the tor network`() {
        val url = SearchEngine.DUCKDUCKGO_ONION.urlFor("test")
        assertTrue(url.contains(".onion/"))
    }

    @Test
    fun `queries are encoded for every engine`() {
        SearchEngine.entries.forEach { engine ->
            assertTrue(engine.name, engine.urlFor("a b").contains("a+b"))
        }
    }
}
