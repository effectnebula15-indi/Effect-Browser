package io.effect.browser.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ContainerDao {
    @Query("SELECT * FROM containers ORDER BY created_at ASC")
    fun observeAll(): Flow<List<ContainerEntity>>

    @Query("SELECT * FROM containers WHERE id = :id")
    suspend fun get(id: Long): ContainerEntity?

    @Query("SELECT COUNT(*) FROM containers")
    suspend fun count(): Int

    @Insert
    suspend fun insert(entity: ContainerEntity): Long

    @Query("UPDATE containers SET name = :name, color_argb = :colorArgb WHERE id = :id")
    suspend fun updateNameAndColor(id: Long, name: String, colorArgb: Int)

    @Query("UPDATE containers SET network_mode = :networkMode WHERE id = :id")
    suspend fun updateNetworkMode(id: Long, networkMode: String)

    @Query("DELETE FROM containers WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY created_at DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = :url)")
    fun observeExistsByUrl(url: String): Flow<Boolean>

    @Insert
    suspend fun insert(entity: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface TabDao {
    @Query(
        """
        SELECT tabs.* FROM tabs
        INNER JOIN containers ON containers.id = tabs.container_id
        WHERE containers.network_mode = :networkMode
        ORDER BY tabs.last_active_at DESC
        """,
    )
    fun observeForNetworkMode(networkMode: String): Flow<List<TabEntity>>

    @Query("SELECT * FROM tabs WHERE container_id = :containerId ORDER BY last_active_at DESC")
    fun observeForContainer(containerId: Long): Flow<List<TabEntity>>

    @Query("SELECT * FROM tabs WHERE id = :id")
    suspend fun get(id: Long): TabEntity?

    @Insert
    suspend fun insert(entity: TabEntity): Long

    @Query("UPDATE tabs SET url = :url, title = :title, last_active_at = :now WHERE id = :id")
    suspend fun updateLocation(id: Long, url: String, title: String, now: Long)

    @Query("UPDATE tabs SET last_active_at = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("DELETE FROM tabs WHERE id = :id")
    suspend fun delete(id: Long)
}
