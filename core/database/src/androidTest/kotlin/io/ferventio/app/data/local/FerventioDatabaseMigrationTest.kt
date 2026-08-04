package io.ferventio.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FerventioDatabaseMigrationTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val createdDatabases = mutableSetOf<String>()

    @After
    fun deleteTestDatabases() {
        createdDatabases.forEach { databaseName -> context.deleteDatabase(databaseName) }
        createdDatabases.clear()
    }

    @Test
    fun everySupportedStartingVersionMigratesToLatestAndPreservesHistory() {
        for (startVersion in 1..8) {
            val databaseName = "ferventio-migration-$startVersion-${System.nanoTime()}.db"
            createdDatabases += databaseName
            createLegacyDatabase(databaseName, startVersion)

            val roomDatabase = Room.databaseBuilder(
                context,
                FerventioDatabase::class.java,
                databaseName,
            )
                .addMigrations(*ALL_MIGRATIONS)
                .allowMainThreadQueries()
                .build()
            try {
                val db = roomDatabase.openHelper.writableDatabase
                assertEquals(9, db.version)
                assertMigratedFixture(db, startVersion)
                assertLatestSchema(db)
            } finally {
                roomDatabase.close()
            }
        }
    }

    @Test
    fun migrationEightToNineRemovesLegacyDefaultsAndPreservesScrollState() {
        val databaseName = "ferventio-migration-8-9-${System.nanoTime()}.db"
        createdDatabases += databaseName
        createLegacyDatabase(databaseName, 8)

        val roomDatabase = Room.databaseBuilder(
            context,
            FerventioDatabase::class.java,
            databaseName,
        )
            .addMigrations(MIGRATION_8_9)
            .allowMainThreadQueries()
            .build()
        try {
            val db = roomDatabase.openHelper.writableDatabase
            val defaults = columnDefaults(db, "channel_scroll_state")

            assertNull(defaults.getValue("first_visible_item_index"))
            assertNull(defaults.getValue("first_visible_item_scroll_offset"))
            assertNull(defaults.getValue("is_at_bottom"))
            assertNull(defaults.getValue("updated_at_millis"))

            db.query(
                """
                SELECT anchor_message_id,
                       first_visible_item_index,
                       first_visible_item_scroll_offset,
                       is_at_bottom,
                       updated_at_millis
                FROM channel_scroll_state
                WHERE channel_id = 'channel-1'
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("message-1", cursor.getString(0))
                assertEquals(7, cursor.getInt(1))
                assertEquals(19, cursor.getInt(2))
                assertEquals(1, cursor.getInt(3))
                assertEquals(1_700_000_000_500L, cursor.getLong(4))
                assertFalse(cursor.moveToNext())
            }
        } finally {
            roomDatabase.close()
        }
    }

    private fun createLegacyDatabase(databaseName: String, targetVersion: Int) {
        require(targetVersion in 1..8)
        context.deleteDatabase(databaseName)

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            createVersionOneSchema(db)
                            insertVersionOneFixture(db)
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                )
                .build(),
        )

        helper.writableDatabase.use { db ->
            ALL_MIGRATIONS
                .asSequence()
                .filter { migration -> migration.endVersion <= targetVersion }
                .forEach { migration ->
                    assertEquals(migration.startVersion, db.version)
                    migration.migrate(db)
                    db.version = migration.endVersion
                }

            if (targetVersion >= 2) {
                insertScrollFixture(db, targetVersion)
            }
        }
        helper.close()
    }

    private fun createVersionOneSchema(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE channels (
                id TEXT NOT NULL PRIMARY KEY,
                login TEXT NOT NULL,
                display_name TEXT NOT NULL,
                profile_image_url TEXT,
                updated_at_millis INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX index_channels_login ON channels (login)")

        db.execSQL(
            """
            CREATE TABLE users (
                id TEXT NOT NULL PRIMARY KEY,
                login TEXT NOT NULL,
                display_name TEXT NOT NULL,
                color TEXT,
                profile_image_url TEXT,
                updated_at_millis INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX index_users_login ON users (login)")

        db.execSQL(
            """
            CREATE TABLE chat_messages (
                id TEXT NOT NULL PRIMARY KEY,
                eventsub_message_id TEXT,
                channel_id TEXT NOT NULL,
                channel_login TEXT NOT NULL,
                author_id TEXT NOT NULL,
                author_login TEXT NOT NULL,
                author_display_name TEXT NOT NULL,
                author_color TEXT,
                text TEXT NOT NULL,
                timestamp TEXT NOT NULL,
                timestamp_millis INTEGER NOT NULL,
                message_type TEXT NOT NULL,
                is_deleted INTEGER NOT NULL,
                is_system INTEGER NOT NULL,
                is_action INTEGER NOT NULL,
                is_first_message INTEGER NOT NULL,
                is_returning_chatter INTEGER NOT NULL,
                reply_parent_message_id TEXT,
                reply_parent_message_body TEXT,
                reply_parent_user_id TEXT,
                reply_parent_user_login TEXT,
                reply_parent_user_name TEXT,
                reply_thread_message_id TEXT,
                reply_thread_user_id TEXT,
                reply_thread_user_login TEXT,
                reply_thread_user_name TEXT,
                moderation_action TEXT,
                moderation_actor_user_id TEXT,
                moderation_reason TEXT,
                moderation_at_millis INTEGER,
                FOREIGN KEY(channel_id) REFERENCES channels(id)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX index_chat_messages_channel_id_timestamp_millis " +
                "ON chat_messages (channel_id, timestamp_millis)",
        )
        db.execSQL(
            "CREATE INDEX index_chat_messages_author_id_timestamp_millis " +
                "ON chat_messages (author_id, timestamp_millis)",
        )
        db.execSQL(
            "CREATE INDEX index_chat_messages_timestamp_millis " +
                "ON chat_messages (timestamp_millis)",
        )

        db.execSQL(
            """
            CREATE TABLE chat_badges (
                message_id TEXT NOT NULL,
                position INTEGER NOT NULL,
                set_id TEXT NOT NULL,
                badge_id TEXT NOT NULL,
                info TEXT,
                PRIMARY KEY(message_id, position),
                FOREIGN KEY(message_id) REFERENCES chat_messages(id)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX index_chat_badges_message_id ON chat_badges (message_id)")

        db.execSQL(
            """
            CREATE TABLE chat_fragments (
                message_id TEXT NOT NULL,
                position INTEGER NOT NULL,
                type TEXT NOT NULL,
                text TEXT NOT NULL,
                emote_id TEXT,
                emote_set_id TEXT,
                owner_id TEXT,
                formats TEXT,
                mention_user_id TEXT,
                mention_user_login TEXT,
                mention_user_name TEXT,
                cheermote_prefix TEXT,
                cheermote_bits INTEGER,
                cheermote_tier INTEGER,
                url TEXT,
                raw_type TEXT,
                PRIMARY KEY(message_id, position),
                FOREIGN KEY(message_id) REFERENCES chat_messages(id)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX index_chat_fragments_message_id ON chat_fragments (message_id)")

        db.execSQL(
            """
            CREATE TABLE moderation_actions (
                id TEXT NOT NULL PRIMARY KEY,
                channel_id TEXT NOT NULL,
                target_user_id TEXT,
                target_user_login TEXT,
                message_id TEXT,
                action TEXT NOT NULL,
                duration_seconds INTEGER,
                reason TEXT,
                created_at_millis INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX index_moderation_actions_channel_id_created_at_millis " +
                "ON moderation_actions (channel_id, created_at_millis)",
        )
        db.execSQL(
            "CREATE INDEX index_moderation_actions_target_user_id_created_at_millis " +
                "ON moderation_actions (target_user_id, created_at_millis)",
        )
    }

    private fun insertVersionOneFixture(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO channels VALUES (?, ?, ?, ?, ?)",
            arrayOf<Any?>("channel-1", "ferventio", "Ferventio", null, 1_700_000_000_000L),
        )
        db.execSQL(
            "INSERT INTO users VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>("user-1", "viewer", "Viewer", "#00FF00", null, 1_700_000_000_100L),
        )
        db.execSQL(
            """
            INSERT INTO chat_messages (
                id, eventsub_message_id, channel_id, channel_login,
                author_id, author_login, author_display_name, author_color,
                text, timestamp, timestamp_millis, message_type,
                is_deleted, is_system, is_action, is_first_message,
                is_returning_chatter, reply_parent_message_id,
                reply_parent_message_body, reply_parent_user_id,
                reply_parent_user_login, reply_parent_user_name,
                reply_thread_message_id, reply_thread_user_id,
                reply_thread_user_login, reply_thread_user_name,
                moderation_action, moderation_actor_user_id,
                moderation_reason, moderation_at_millis
            ) VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
            )
            """.trimIndent(),
            arrayOf<Any?>(
                "message-1", "eventsub-1", "channel-1", "ferventio",
                "user-1", "viewer", "Viewer", "#00FF00",
                "preserved message", "2023-11-14T22:13:20Z", 1_700_000_000_200L, "text",
                0, 0, 0, 1, 0,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null,
            ),
        )
        db.execSQL(
            "INSERT INTO chat_badges VALUES (?, ?, ?, ?, ?)",
            arrayOf<Any?>("message-1", 0, "subscriber", "12", "12"),
        )
        db.execSQL(
            """
            INSERT INTO chat_fragments (
                message_id, position, type, text, emote_id, emote_set_id,
                owner_id, formats, mention_user_id, mention_user_login,
                mention_user_name, cheermote_prefix, cheermote_bits,
                cheermote_tier, url, raw_type
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(
                "message-1", 0, "text", "preserved message",
                null, null, null, null, null, null, null, null, null, null, null, "text",
            ),
        )
    }

    private fun insertScrollFixture(db: SupportSQLiteDatabase, version: Int) {
        when (version) {
            2 -> db.execSQL(
                """
                INSERT INTO channel_scroll_state (
                    channel_id,
                    first_visible_item_index,
                    first_visible_item_scroll_offset,
                    updated_at_millis
                ) VALUES (?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>("channel-1", 7, 19, 1_700_000_000_500L),
            )

            3 -> db.execSQL(
                """
                INSERT INTO channel_scroll_state (
                    channel_id,
                    first_visible_item_index,
                    first_visible_item_scroll_offset,
                    updated_at_millis,
                    anchor_message_id
                ) VALUES (?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>("channel-1", 7, 19, 1_700_000_000_500L, "message-1"),
            )

            else -> db.execSQL(
                """
                INSERT INTO channel_scroll_state (
                    channel_id,
                    first_visible_item_index,
                    first_visible_item_scroll_offset,
                    updated_at_millis,
                    anchor_message_id,
                    is_at_bottom
                ) VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>("channel-1", 7, 19, 1_700_000_000_500L, "message-1", 1),
            )
        }
    }

    private fun assertMigratedFixture(db: SupportSQLiteDatabase, startVersion: Int) {
        db.query(
            """
            SELECT text, notice_json, reward_id, reward_title, reward_cost
            FROM chat_messages
            WHERE id = 'message-1'
            """.trimIndent(),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("preserved message", cursor.getString(0))
            assertNull(cursor.getString(1))
            assertNull(cursor.getString(2))
            assertNull(cursor.getString(3))
            assertTrue(cursor.isNull(4))
            assertFalse(cursor.moveToNext())
        }

        assertEquals(1L, scalarLong(db, "SELECT COUNT(*) FROM chat_badges"))
        assertEquals(1L, scalarLong(db, "SELECT COUNT(*) FROM chat_fragments"))

        if (startVersion >= 2) {
            assertEquals(
                1L,
                scalarLong(
                    db,
                    "SELECT COUNT(*) FROM channel_scroll_state WHERE channel_id = 'channel-1'",
                ),
            )
        }
    }

    private fun assertLatestSchema(db: SupportSQLiteDatabase) {
        val expectedTables = setOf(
            "channels",
            "users",
            "chat_messages",
            "chat_badges",
            "chat_fragments",
            "moderation_actions",
            "channel_scroll_state",
            "attention_entries",
        )
        val actualTables = mutableSetOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            while (cursor.moveToNext()) {
                actualTables += cursor.getString(0)
            }
        }
        assertTrue(actualTables.containsAll(expectedTables))

        val messageIndexes = indexNames(db, "chat_messages")
        assertTrue(messageIndexes.contains("index_chat_messages_channel_id_timestamp_millis_id"))
        assertTrue(messageIndexes.contains("index_chat_messages_channel_id_author_id_timestamp_millis"))
        assertTrue(messageIndexes.contains("index_chat_messages_moderation_action_timestamp_millis"))

        val defaults = columnDefaults(db, "channel_scroll_state")
        assertNull(defaults.getValue("first_visible_item_index"))
        assertNull(defaults.getValue("first_visible_item_scroll_offset"))
        assertNull(defaults.getValue("is_at_bottom"))
        assertNull(defaults.getValue("updated_at_millis"))
    }

    private fun indexNames(db: SupportSQLiteDatabase, table: String): Set<String> {
        val result = mutableSetOf<String>()
        db.query("PRAGMA index_list(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                result += cursor.getString(nameIndex)
            }
        }
        return result
    }

    private fun columnDefaults(db: SupportSQLiteDatabase, table: String): Map<String, String?> {
        val result = linkedMapOf<String, String?>()
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val defaultIndex = cursor.getColumnIndexOrThrow("dflt_value")
            while (cursor.moveToNext()) {
                result[cursor.getString(nameIndex)] =
                    if (cursor.isNull(defaultIndex)) null else cursor.getString(defaultIndex)
            }
        }
        return result
    }

    private fun scalarLong(db: SupportSQLiteDatabase, query: String): Long =
        db.query(query).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getLong(0)
        }
}
