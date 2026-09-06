package io.effect.browser.files

import android.webkit.MimeTypeMap
import java.net.URLDecoder

/**
 * Works out what to call a downloaded file.
 *
 * Servers are inconsistent here — some send a quoted `filename`, some send RFC 5987
 * `filename*` with percent-encoding, many send nothing at all — so this falls back through
 * several sources rather than trusting any one of them.
 *
 * Deliberately free of Android framework types apart from the MIME lookup, which is injected,
 * so the rules can be pinned by ordinary unit tests.
 */
object DownloadNaming {

    private const val FALLBACK = "download"
    private const val MAX_LENGTH = 120
    private const val UTF_8 = "UTF-8"

    /** Path separators and characters that are illegal in a filename on Android. */
    private val ILLEGAL = Regex("[\\\\/:*?\"<>|\\x00-\\x1f]")

    fun fileNameFor(url: String, contentDisposition: String?, mimeType: String?): String =
        fileNameFor(url, contentDisposition, mimeType, ::systemExtensionFor)

    internal fun fileNameFor(
        url: String,
        contentDisposition: String?,
        mimeType: String?,
        extensionResolver: (String) -> String?,
    ): String {
        val raw = fromContentDisposition(contentDisposition)
            ?: fromUrl(url)
            ?: FALLBACK
        return withExtension(sanitize(raw), mimeType, extensionResolver)
    }

    /**
     * Pulls the name out of a Content-Disposition header.
     *
     * `filename*` wins over `filename` when both are present, since it is the one that can
     * carry a non-ASCII name correctly.
     */
    private fun fromContentDisposition(header: String?): String? {
        if (header.isNullOrBlank()) return null

        Regex("filename\\*\\s*=\\s*([^']*)'([^']*)'([^;]+)", RegexOption.IGNORE_CASE)
            .find(header)
            ?.let { match ->
                val charset = match.groupValues[1].trim().ifBlank { UTF_8 }
                val encoded = match.groupValues[3].trim()
                runCatching { URLDecoder.decode(encoded, charset) }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { return it }
            }

        Regex("filename\\s*=\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)
            .find(header)
            ?.groupValues?.get(1)
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        return Regex("filename\\s*=\\s*([^;]+)", RegexOption.IGNORE_CASE)
            .find(header)
            ?.groupValues?.get(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    /**
     * Last path segment of the URL, if it has one.
     *
     * A URL with no path at all (`https://example.com`) yields nothing — naming the file after
     * the host would be worse than falling back to a generic name.
     */
    private fun fromUrl(url: String): String? {
        val path = url.substringBefore('#').substringBefore('?')

        val afterAuthority = when (val schemeEnd = path.indexOf("://")) {
            -1 -> path
            else -> {
                val rest = path.substring(schemeEnd + 3)
                val firstSlash = rest.indexOf('/')
                if (firstSlash < 0) return null
                rest.substring(firstSlash + 1)
            }
        }

        val segment = afterAuthority.trimEnd('/').substringAfterLast('/')
        if (segment.isBlank()) return null
        return runCatching { URLDecoder.decode(segment, UTF_8) }.getOrDefault(segment)
    }

    private fun sanitize(name: String): String {
        // Strip any directory component first: a server is free to send "../../etc/passwd".
        val base = name.substringAfterLast('/').substringAfterLast('\\')
        val cleaned = ILLEGAL.replace(base, "_").trim().trim('.')
        return cleaned.ifBlank { FALLBACK }.take(MAX_LENGTH)
    }

    /** Adds an extension when the name has none but the server told us the type. */
    private fun withExtension(
        name: String,
        mimeType: String?,
        extensionResolver: (String) -> String?,
    ): String {
        if (name.contains('.')) return name
        val normalised = mimeType?.substringBefore(';')?.trim()?.lowercase()
        if (normalised.isNullOrBlank()) return name
        val extension = extensionResolver(normalised)
        return if (extension.isNullOrBlank()) name else "$name.$extension"
    }

    private fun systemExtensionFor(mimeType: String): String? =
        runCatching { MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) }.getOrNull()
}
