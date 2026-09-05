package io.effect.browser.domain.repository

import io.effect.browser.domain.model.NetworkMode
import io.effect.browser.domain.model.Tab
import kotlinx.coroutines.flow.Flow

interface TabRepository {
    /** Tabs whose container runs in [mode] — i.e. the tabs this process is allowed to render. */
    fun observeForNetworkMode(mode: NetworkMode): Flow<List<Tab>>
    fun observeForContainer(containerId: Long): Flow<List<Tab>>
    suspend fun get(id: Long): Tab?
    suspend fun open(containerId: Long, url: String): Tab
    suspend fun updateLocation(id: Long, url: String, title: String)
    suspend fun touch(id: Long)
    suspend fun close(id: Long)
}
