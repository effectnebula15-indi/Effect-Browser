package io.effect.browser.data.repository

import io.effect.browser.data.db.ContainerDao
import io.effect.browser.data.db.ContainerEntity
import io.effect.browser.domain.model.Container
import io.effect.browser.domain.model.NetworkMode
import io.effect.browser.domain.repository.ContainerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class RoomContainerRepository(
    private val dao: ContainerDao,
    private val onContainerDeleted: suspend (contextId: String) -> Unit,
) : ContainerRepository {

    override fun observeAll(): Flow<List<Container>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun get(id: Long): Container? = dao.get(id)?.toDomain()

    override suspend fun create(name: String, colorArgb: Int, networkMode: NetworkMode): Container {
        val entity = ContainerEntity(
            name = name.trim(),
            colorArgb = colorArgb,
            networkMode = networkMode.name,
            // Generated once and persisted. This value *is* the identity of the cookie jar.
            contextId = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis(),
        )
        val id = dao.insert(entity)
        return entity.copy(id = id).toDomain()
    }

    override suspend fun rename(id: Long, name: String, colorArgb: Int) {
        dao.updateNameAndColor(id, name.trim(), colorArgb)
    }

    override suspend fun setNetworkMode(id: Long, networkMode: NetworkMode) {
        dao.updateNetworkMode(id, networkMode.name)
    }

    override suspend fun delete(id: Long) {
        val existing = dao.get(id) ?: return
        dao.delete(id)
        // Drop the cookie jar too, otherwise the storage outlives the container that owned it.
        onContainerDeleted(existing.contextId)
    }

    /** Seeds a first container so a fresh install opens onto something usable. */
    suspend fun ensureSeeded(): Container? {
        if (dao.count() > 0) return null
        return create(name = "Personal", colorArgb = DEFAULT_COLOR, networkMode = NetworkMode.DIRECT)
    }

    private companion object {
        const val DEFAULT_COLOR = 0xFF4C8DF6.toInt()
    }
}

private fun ContainerEntity.toDomain() = Container(
    id = id,
    name = name,
    colorArgb = colorArgb,
    // An unknown string can only come from a downgrade; treat it as the safe, non-tor default.
    networkMode = runCatching { NetworkMode.valueOf(networkMode) }.getOrDefault(NetworkMode.DIRECT),
    contextId = contextId,
    createdAt = createdAt,
)
