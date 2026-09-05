package io.effect.browser.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ContainerEntity::class, BookmarkEntity::class, TabEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class EffectDatabase : RoomDatabase() {
    abstract fun containerDao(): ContainerDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun tabDao(): TabDao

    companion object {
        private const val NAME = "effect-browser.db"

        @Volatile
        private var instance: EffectDatabase? = null

        /**
         * The app runs in two processes (see `docs/ARCHITECTURE.md`), and both of them read and
         * write this database. [RoomDatabase.Builder.enableMultiInstanceInvalidation] is what
         * makes a write in the tor process invalidate the main process's live queries — without
         * it each process would sit on a stale view of the container list.
         */
        fun get(context: Context): EffectDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                EffectDatabase::class.java,
                NAME,
            )
                .enableMultiInstanceInvalidation()
                .build()
                .also { instance = it }
        }
    }
}
