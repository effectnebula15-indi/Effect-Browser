package io.effect.browser.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Content-Disposition is the least trustworthy input the download path takes, and a bad name
 * here either loses the file or writes it somewhere it shouldn't go.
 */
class DownloadNamingTest {

    /** Stands in for MimeTypeMap, which needs a real Android runtime. */
    private val extensions: (String) -> String? = { mime ->
        when (mime) {
            "application/pdf" -> "pdf"
            "image/png" -> "png"
            else -> null
        }
    }

    private fun name(
        url: String = "https://example.com/",
        disposition: String? = null,
        mimeType: String? = null,
    ) = DownloadNaming.fileNameFor(url, disposition, mimeType, extensions)

    // ---------------------------------------------------------------- Content-Disposition

    @Test
    fun `takes a quoted filename`() {
        assertEquals(
            "report.pdf",
            name(disposition = "attachment; filename=\"report.pdf\""),
        )
    }

    @Test
    fun `takes an unquoted filename`() {
        assertEquals("report.pdf", name(disposition = "attachment; filename=report.pdf"))
    }

    @Test
    fun `is case insensitive about the header`() {
        assertEquals("report.pdf", name(disposition = "ATTACHMENT; FILENAME=\"report.pdf\""))
    }

    @Test
    fun `decodes an RFC 5987 filename`() {
        assertEquals(
            "отчёт.pdf",
            name(disposition = "attachment; filename*=UTF-8''%D0%BE%D1%82%D1%87%D1%91%D1%82.pdf"),
        )
    }

    @Test
    fun `prefers the encoded filename over the plain one`() {
        // Servers send both for compatibility; only filename* carries the real name.
        val header = "attachment; filename=\"fallback.pdf\"; " +
            "filename*=UTF-8''%D0%BE%D1%82%D1%87%D1%91%D1%82.pdf"
        assertEquals("отчёт.pdf", name(disposition = header))
    }

    // ---------------------------------------------------------------- URL fallback

    @Test
    fun `falls back to the last path segment`() {
        assertEquals("report.pdf", name(url = "https://example.com/files/report.pdf"))
    }

    @Test
    fun `ignores the query string`() {
        assertEquals("report.pdf", name(url = "https://example.com/report.pdf?token=abc&x=1"))
    }

    @Test
    fun `ignores the fragment`() {
        assertEquals("report.pdf", name(url = "https://example.com/report.pdf#page=3"))
    }

    @Test
    fun `percent decodes the path segment`() {
        assertEquals("my report.pdf", name(url = "https://example.com/my%20report.pdf"))
    }

    @Test
    fun `does not name the file after the host when there is no path`() {
        assertEquals("download", name(url = "https://example.com"))
        assertEquals("download", name(url = "https://example.com/"))
    }

    // ---------------------------------------------------------------- safety

    @Test
    fun `strips directory traversal from the header`() {
        val result = name(disposition = "attachment; filename=\"../../etc/passwd\"")
        assertEquals("passwd", result)
        assertFalse(result.contains("/"))
        assertFalse(result.contains(".."))
    }

    @Test
    fun `strips a windows path from the header`() {
        assertEquals("evil.exe", name(disposition = "attachment; filename=\"C:\\\\windows\\\\evil.exe\""))
    }

    @Test
    fun `replaces characters that are illegal in a filename`() {
        val result = name(disposition = "attachment; filename=\"a:b?c|d.txt\"")
        assertEquals("a_b_c_d.txt", result)
    }

    @Test
    fun `caps very long names`() {
        val long = "a".repeat(400)
        assertEquals(120, name(disposition = "attachment; filename=\"$long\"").length)
    }

    // ---------------------------------------------------------------- extensions

    @Test
    fun `adds an extension from the mime type when the name has none`() {
        assertEquals(
            "report.pdf",
            name(disposition = "attachment; filename=\"report\"", mimeType = "application/pdf"),
        )
    }

    @Test
    fun `ignores mime parameters when resolving the extension`() {
        assertEquals(
            "image.png",
            name(disposition = "attachment; filename=\"image\"", mimeType = "image/png; charset=binary"),
        )
    }

    @Test
    fun `keeps an existing extension rather than appending another`() {
        assertEquals(
            "report.pdf",
            name(disposition = "attachment; filename=\"report.pdf\"", mimeType = "image/png"),
        )
    }

    @Test
    fun `leaves the name alone for an unknown mime type`() {
        assertEquals(
            "report",
            name(disposition = "attachment; filename=\"report\"", mimeType = "application/x-unknown"),
        )
    }

    // ---------------------------------------------------------------- fallback

    @Test
    fun `falls back when nothing is usable`() {
        assertEquals("download", name(url = "", disposition = null, mimeType = null))
    }
}
