package io.effect.browser.domain.repository

import io.effect.browser.domain.model.Bookmark
import kotlinx.coroutines.flow.Flow

interface BookmarkRepository {
    fun observeAll(): Flow<List<Bookmark>>
    fun observeIsBookmarked(url: String): Flow<Boolean>
    suspend fun add(title: String, url: String): Long
    suspend fun removeByUrl(url: String)
    suspend fun delete(id: Long)
}
