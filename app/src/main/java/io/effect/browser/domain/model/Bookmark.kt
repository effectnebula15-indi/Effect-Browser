package io.effect.browser.domain.model

/**
 * A saved page.
 *
 * Bookmarks are deliberately *not* partitioned by container: a bookmark is a pointer to a
 * page, not a credential, so scoping it per identity would mean re-saving the same URL in
 * every container. [containerId] is reserved for a future "open this bookmark in container X"
 * feature and is null for every bookmark v1 creates.
 */
data class Bookmark(
    val id: Long,
    val title: String,
    val url: String,
    val containerId: Long?,
    val createdAt: Long,
)
