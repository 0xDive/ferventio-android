package io.ferventio.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE chat_messages ADD COLUMN notice_json TEXT",
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS channel_scroll_state (
                channel_id TEXT NOT NULL PRIMARY KEY,
                first_visible_item_index INTEGER NOT NULL DEFAULT 0,
                first_visible_item_scroll_offset INTEGER NOT NULL DEFAULT 0,
                updated_at_millis INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE channel_scroll_state ADD COLUMN anchor_message_id TEXT",
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE channel_scroll_state ADD COLUMN is_at_bottom INTEGER NOT NULL DEFAULT 0",
        )
    }
}


val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS attention_entries (
                message_id TEXT NOT NULL PRIMARY KEY,
                channel_id TEXT NOT NULL,
                channel_login TEXT NOT NULL,
                author_id TEXT NOT NULL,
                author_login TEXT NOT NULL,
                author_display_name TEXT NOT NULL,
                text TEXT NOT NULL,
                timestamp TEXT NOT NULL,
                timestamp_millis INTEGER NOT NULL,
                is_read INTEGER NOT NULL,
                is_direct_mention INTEGER NOT NULL,
                is_highlight INTEGER NOT NULL,
                highlight_reasons_json TEXT NOT NULL,
                highlight_color_argb INTEGER
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_attention_entries_is_read_timestamp_millis " +
                "ON attention_entries (is_read, timestamp_millis)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_attention_entries_channel_id_timestamp_millis " +
                "ON attention_entries (channel_id, timestamp_millis)",
        )
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chat_messages ADD COLUMN reward_id TEXT")
        db.execSQL("ALTER TABLE chat_messages ADD COLUMN reward_title TEXT")
        db.execSQL("ALTER TABLE chat_messages ADD COLUMN reward_cost INTEGER")
    }
}


val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_chat_messages_channel_login_timestamp_millis " +
                "ON chat_messages (channel_login, timestamp_millis)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_chat_messages_author_login_timestamp_millis " +
                "ON chat_messages (author_login, timestamp_millis)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_chat_messages_message_type_timestamp_millis " +
                "ON chat_messages (message_type, timestamp_millis)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_chat_messages_is_deleted_timestamp_millis " +
                "ON chat_messages (is_deleted, timestamp_millis)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_chat_messages_moderation_action_timestamp_millis " +
                "ON chat_messages (moderation_action, timestamp_millis)",
        )
    }
}


val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_chat_messages_channel_id_timestamp_millis_id " +
                "ON chat_messages (channel_id, timestamp_millis, id)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_chat_messages_channel_id_author_id_timestamp_millis " +
                "ON chat_messages (channel_id, author_id, timestamp_millis)",
        )
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE channel_scroll_state_new (
                channel_id TEXT NOT NULL PRIMARY KEY,
                anchor_message_id TEXT,
                first_visible_item_index INTEGER NOT NULL,
                first_visible_item_scroll_offset INTEGER NOT NULL,
                is_at_bottom INTEGER NOT NULL,
                updated_at_millis INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO channel_scroll_state_new (
                channel_id,
                anchor_message_id,
                first_visible_item_index,
                first_visible_item_scroll_offset,
                is_at_bottom,
                updated_at_millis
            )
            SELECT
                channel_id,
                anchor_message_id,
                first_visible_item_index,
                first_visible_item_scroll_offset,
                is_at_bottom,
                updated_at_millis
            FROM channel_scroll_state
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE channel_scroll_state")
        db.execSQL("ALTER TABLE channel_scroll_state_new RENAME TO channel_scroll_state")
    }
}

val ALL_MIGRATIONS = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
    MIGRATION_6_7,
    MIGRATION_7_8,
    MIGRATION_8_9,
)
