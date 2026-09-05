package io.effect.browser.domain.url

import java.net.URLEncoder

internal object UrlEncoding {
    fun encodeQuery(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())
}
