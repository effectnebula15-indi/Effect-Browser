package io.effect.browser.data.repository

import io.effect.browser.data.db.TabDao
import io.effect.browser.data.db.TabEntity
import io.effect.browser.domain.model.NetworkMode
import io.effect.browser.domain.model.Tab
import io.effect.browser.domain.repository.TabRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomTabRepository(
    private val dao: TabDao,
) : TabRepository {

    override fun observeForNetworkMode(mode: NetworkMode): Flow<List<Tab>> =
        dao.observeForNetworkMode(mode.name).map { rows -> rows.map { it.toDomain() } }

    override fun observeForContainer(containerId: Long): Flow<List<Tab>> =
        dao.observeForContainer(containerId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun get(id: Long): Tab? = dao.get(id)?.toDomain()

    override suspend fun open(containerId: Long, url: String): Tab {
        val now = System.currentTimeMillis()
        val entity = TabEntity(
            containerId = containerId,
            url = url,
            title = "",
            createdAt = now,
            lastActiveAt = now,
        )
        val id = dao.insert(entity)
        return entity.copy(id = id).toDomain()
    }

    override suspend fun updateLocation(id: Long, url: String, title: String) {
        dao.updateLocation(id, url, title, System.currentTimeMillis())
    }

    override suspend fun touch(id: Long) = dao.touch(id, System.currentTimeMillis())

    override suspend fun close(id: Long) = dao.delete(id)
}

private fun TabEntity.toDomain() = Tab(
    id = id,
    containerId = containerId,
    url = url,
    title = title,
    createdAt = createdAt,
    lastActiveAt = lastActiveAt,
)
