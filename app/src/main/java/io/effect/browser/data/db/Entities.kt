package io.effect.browser.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "containers",
    indices = [Index(value = ["context_id"], unique = true)],
)
data class ContainerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "color_argb") val colorArgb: Int,
    /** Stored as the enum name so the column stays readable in a DB dump. */
    @ColumnInfo(name = "network_mode") val networkMode: String,
    @ColumnInfo(name = "context_id") val contextId: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "bookmarks",
    indices = [Index(value = ["url"])],
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    /** Null means "available in every container", which is every bookmark v1 creates. */
    @ColumnInfo(name = "container_id") val containerId: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "tabs",
    foreignKeys = [
        ForeignKey(
            entity = ContainerEntity::class,
            parentColumns = ["id"],
            childColumns = ["container_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["container_id"])],
)
data class TabEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "container_id") val containerId: Long,
    val url: String,
    val title: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "last_active_at") val lastActiveAt: Long,
)
