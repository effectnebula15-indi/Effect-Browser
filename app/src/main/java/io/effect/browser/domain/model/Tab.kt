package io.effect.browser.domain.model

/** One open page. A tab belongs to exactly one container for its whole life. */
data class Tab(
    val id: Long,
    val containerId: Long,
    val url: String,
    val title: String,
    val createdAt: Long,
    val lastActiveAt: Long,
)
