package io.effect.browser.data.repository

import io.effect.browser.data.db.BookmarkDao
import io.effect.browser.data.db.BookmarkEntity
import io.effect.browser.domain.model.Bookmark
import io.effect.browser.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class RoomBookmarkRepository(
    private val dao: BookmarkDao,
) : BookmarkRepository {

    override fun observeAll(): Flow<List<Bookmark>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeIsBookmarked(url: String): Flow<Boolean> =
        dao.observeExistsByUrl(url).distinctUntilChanged()

    override suspend fun add(title: String, url: String): Long = dao.insert(
        BookmarkEntity(
            title = title.ifBlank { url },
            url = url,
            containerId = null,
            createdAt = System.currentTimeMillis(),
        ),
    )

    override suspend fun removeByUrl(url: String) = dao.deleteByUrl(url)

    override suspend fun delete(id: Long) = dao.delete(id)
}

private fun BookmarkEntity.toDomain() = Bookmark(
    id = id,
    title = title,
    url = url,
    containerId = containerId,
    createdAt = createdAt,
)
