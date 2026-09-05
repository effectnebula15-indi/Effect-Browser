package io.effect.browser.domain.repository

import io.effect.browser.domain.model.Container
import io.effect.browser.domain.model.NetworkMode
import kotlinx.coroutines.flow.Flow

interface ContainerRepository {
    fun observeAll(): Flow<List<Container>>
    suspend fun get(id: Long): Container?
    suspend fun create(name: String, colorArgb: Int, networkMode: NetworkMode): Container
    suspend fun rename(id: Long, name: String, colorArgb: Int)

    /**
     * Changing the network mode moves the container's tabs to the other process on next open.
     * The cookie jar is keyed on contextId and survives the move untouched.
     */
    suspend fun setNetworkMode(id: Long, networkMode: NetworkMode)

    /** Deletes the container, its tabs, and (best effort) its Gecko storage. */
    suspend fun delete(id: Long)
}
