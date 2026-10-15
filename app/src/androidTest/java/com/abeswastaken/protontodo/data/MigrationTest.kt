package com.abeswastaken.protontodo.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "migration-test.db"

    /** The items table exactly as v1.0.0 created it. */
    private val v1Table = "CREATE TABLE IF NOT EXISTS `items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`kind` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `dateEpochDay` INTEGER, " +
        "`timeMinutes` INTEGER, `reminders` TEXT NOT NULL, `dayOfReminder` INTEGER NOT NULL, " +
        "`allDayReminderMinutes` INTEGER NOT NULL, `appointmentLeadMinutes` INTEGER NOT NULL, " +
        "`addToCalendar` INTEGER NOT NULL, `emailReminders` INTEGER NOT NULL, `repeatRule` TEXT, " +
        "`extendedReminderDays` INTEGER NOT NULL, `isDraft` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
        "`updatedAt` INTEGER NOT NULL, `completedAt` INTEGER, `doneThroughEpochDay` INTEGER, " +
        "`lastReminderAt` INTEGER, `pendingAckAt` INTEGER, `calendarUid` TEXT, `calendarSequence` INTEGER NOT NULL)"

    @Test
    fun upgradeFromV1KeepsItems() = runBlocking {
        context.deleteDatabase(name)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            db.execSQL(v1Table)
            db.execSQL(
                "INSERT INTO items (kind, title, description, dateEpochDay, reminders, dayOfReminder, allDayReminderMinutes, " +
                    "appointmentLeadMinutes, addToCalendar, emailReminders, extendedReminderDays, isDraft, createdAt, updatedAt, calendarSequence) " +
                    "VALUES ('EVENT', 'Car insurance', '', 20760, 'D2', 1, 360, 120, 1, 1, 0, 0, 1, 1, 0)",
            )
            db.version = 1
        }

        val room = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
        val items = room.items().all()
        room.close()
        context.deleteDatabase(name)

        assertEquals(1, items.size)
        val item = items.single()
        assertEquals("Car insurance", item.title)
        assertFalse(item.important)
        assertNull(item.category)
        assertNull(item.parentId)
        assertEquals("", item.pendingTodos)
    }
}
