package io.ferventio.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ChannelEntity::class,
        UserEntity::class,
        ChatMessageEntity::class,
        ChatBadgeEntity::class,
        ChatFragmentEntity::class,
        ModerationActionEntity::class,
        ChannelScrollStateEntity::class,
        AttentionEntryEntity::class,
    ],
    version = 9,
    exportSchema = true,
)
abstract class FerventioDatabase : RoomDatabase() {
    abstract fun chatHistoryDao(): ChatHistoryDao

    companion object {
        @Volatile
        private var instance: FerventioDatabase? = null

        fun getInstance(context: Context): FerventioDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FerventioDatabase::class.java,
                    "ferventio.db",
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
                    .also { instance = it }
            }
    }
}
