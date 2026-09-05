package io.effect.browser.domain.url

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The address bar has one job with two outcomes, and the expensive mistake is treating a search
 * phrase as a URL (or vice versa). These cases pin the boundary.
 */
class AddressResolverTest {

    private val google = SearchEngine.GOOGLE

    // ---------------------------------------------------------------- navigates

    @Test
    fun `keeps an explicit https url untouched`() {
        assertEquals(
            "https://example.com/path?q=1",
            AddressResolver.resolve("https://example.com/path?q=1", google),
        )
    }

    @Test
    fun `keeps an explicit http url untouched`() {
        assertEquals("http://example.com", AddressResolver.resolve("http://example.com", google))
    }

    @Test
    fun `allows about pages`() {
        assertEquals("about:blank", AddressResolver.resolve("about:blank", google))
    }

    @Test
    fun `upgrades a bare domain to https`() {
        assertEquals("https://example.com", AddressResolver.resolve("example.com", google))
    }

    @Test
    fun `upgrades a domain with a path`() {
        assertEquals("https://example.com/a/b", AddressResolver.resolve("example.com/a/b", google))
    }

    @Test
    fun `upgrades a multi-label domain`() {
        assertEquals("https://foo.co.uk", AddressResolver.resolve("foo.co.uk", google))
    }

    @Test
    fun `treats onion addresses as urls`() {
        val onion = "duckduckgogg42xjoc72x3sjasowoarfbgcmvfimaftt6twagswzczad.onion"
        assertEquals("https://$onion", AddressResolver.resolve(onion, google))
    }

    @Test
    fun `treats an ipv4 literal as a url`() {
        assertEquals("https://127.0.0.1:8080", AddressResolver.resolve("127.0.0.1:8080", google))
    }

    @Test
    fun `treats localhost with a port as a url`() {
        assertEquals("https://localhost:3000", AddressResolver.resolve("localhost:3000", google))
    }

    @Test
    fun `trims surrounding whitespace`() {
        assertEquals("https://example.com", AddressResolver.resolve("  example.com  ", google))
    }

    // ---------------------------------------------------------------- searches

    @Test
    fun `searches a plain phrase`() {
        val result = AddressResolver.resolve("kotlin coroutines", google)
        assertEquals("https://www.google.com/search?q=kotlin+coroutines", result)
    }

    @Test
    fun `searches a phrase that contains a dot`() {
        // Spaces mean this is prose, not a host, even though "vs." looks domain-ish.
        val result = AddressResolver.resolve("gradle vs. maven", google)
        assertTrue(result!!.startsWith("https://www.google.com/search?q="))
    }

    @Test
    fun `searches a decimal number rather than treating it as a host`() {
        val result = AddressResolver.resolve("3.14", google)
        assertTrue(result!!.startsWith("https://www.google.com/search?q="))
    }

    @Test
    fun `searches a single word with no dot`() {
        val result = AddressResolver.resolve("weather", google)
        assertEquals("https://www.google.com/search?q=weather", result)
    }

    @Test
    fun `does not treat an unknown scheme as a url`() {
        // "javascript:" must never be handed to loadUri from the address bar.
        val result = AddressResolver.resolve("javascript:alert(1)", google)
        assertTrue(result!!.startsWith("https://www.google.com/search?q="))
    }

    @Test
    fun `percent encodes the query`() {
        val result = AddressResolver.resolve("a b&c", google)
        assertEquals("https://www.google.com/search?q=a+b%26c", result)
    }

    // ---------------------------------------------------------------- empty

    @Test
    fun `returns null for blank input`() {
        assertNull(AddressResolver.resolve("   ", google))
        assertNull(AddressResolver.resolve("", google))
    }
}
