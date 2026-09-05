package io.effect.browser.domain.url

/**
 * Turns whatever the user typed into the single address field into something loadable.
 *
 * The rule the UI promises: looks like an address -> navigate, otherwise -> search.
 * Getting this wrong in the "search" direction is merely annoying; getting it wrong in the
 * "navigate" direction leaks the typed text to a search engine, so the checks below only
 * treat input as a URL when it is unambiguous.
 */
object AddressResolver {

    private val SUPPORTED_SCHEMES = setOf("http", "https", "about", "file", "data", "view-source")

    /** Host label chars; deliberately excludes anything that would make this a search phrase. */
    private val HOST_REGEX = Regex("^[a-zA-Z0-9._~%-]+(:\\d{1,5})?(/.*)?$")

    private val IPV4_REGEX = Regex("^\\d{1,3}(\\.\\d{1,3}){3}(:\\d{1,5})?(/.*)?$")

    fun resolve(rawInput: String, engine: SearchEngine): String? {
        val input = rawInput.trim()
        if (input.isEmpty()) return null

        explicitUrl(input)?.let { return it }
        implicitUrl(input)?.let { return it }
        return engine.urlFor(input)
    }

    /** Input that already carries a scheme we are willing to load. */
    private fun explicitUrl(input: String): String? {
        val schemeEnd = input.indexOf(':')
        if (schemeEnd <= 0) return null
        val scheme = input.substring(0, schemeEnd).lowercase()

        // "localhost:8080" parses as scheme "localhost" — reject schemes we don't know so it
        // falls through to the host check below.
        if (scheme !in SUPPORTED_SCHEMES) return null
        return input
    }

    /** Input with no scheme that is nonetheless clearly an address. */
    private fun implicitUrl(input: String): String? {
        if (input.any { it.isWhitespace() }) return null

        val hostPart = input.substringBefore('/').substringBefore('?')
        if (hostPart.isEmpty()) return null

        val looksLikeHost = when {
            IPV4_REGEX.matches(input) -> true
            hostPart.equals("localhost", ignoreCase = true) -> true
            hostPart.startsWith("localhost:") -> true
            // A dotted name with a plausible TLD, e.g. example.com, foo.co.uk, x.onion
            hostPart.contains('.') && HOST_REGEX.matches(input) && hasPlausibleTld(hostPart) -> true
            else -> false
        }

        return if (looksLikeHost) "https://$input" else null
    }

    private fun hasPlausibleTld(hostPart: String): Boolean {
        val host = hostPart.substringBefore(':')
        if (host.endsWith('.')) return false
        val tld = host.substringAfterLast('.', missingDelimiterValue = "")
        // A TLD is at least two letters. This rejects "1.5" and "foo.2" as searches.
        return tld.length >= 2 && tld.all { it.isLetter() }
    }
}
