package com.abeswastaken.protontodo.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

const val KIND_TASK = "TASK"
const val KIND_EVENT = "EVENT"

/**
 * One row for every task, event and draft.
 *
 * Reminder offsets are stored as a comma list of codes: `D2` (2 days before), `W1` (1 week before)
 * or `C20300` (a custom date, as an epoch day). [repeatRule] is an iCalendar RRULE body.
 */
@Entity(tableName = "items")
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String = KIND_TASK,
    val title: String = "",
    val description: String = "",
    val dateEpochDay: Long? = null,
    /** Minutes after midnight. Null means all-day. */
    val timeMinutes: Int? = null,
    val reminders: String = "",
    val dayOfReminder: Boolean = true,
    /** Time of the day-of reminder for all-day items, in minutes after midnight. */
    val allDayReminderMinutes: Int = 360,
    /** How long before an appointment the day-of reminder fires. */
    val appointmentLeadMinutes: Int = 120,
    val addToCalendar: Boolean = true,
    val emailReminders: Boolean = true,
    val repeatRule: String? = null,
    /** Extra early reminder for repeating items, in days. 0 means off. */
    val extendedReminderDays: Int = 0,
    val isDraft: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val completedAt: Long? = null,
    /** For repeating items: occurrences up to and including this day are done. */
    val doneThroughEpochDay: Long? = null,
    /** When the most recent reminder fired (its scheduled time), in epoch millis. */
    val lastReminderAt: Long? = null,
    /** Set while a final reminder waits for its challenge to be passed. */
    val pendingAckAt: Long? = null,
    val calendarUid: String? = null,
    val calendarSequence: Int = 0,
    /** For a to-do made from an event: that event's id. */
    val parentId: Long? = null,
    @ColumnInfo(defaultValue = "0") val important: Boolean = false,
    /** A [com.abeswastaken.protontodo.ui.Category] name shown as an icon, or null. */
    val category: String? = null,
    /** Linked to-dos waiting to be created when this item is saved for real. See [com.abeswastaken.protontodo.domain.LinkedTodo]. */
    @ColumnInfo(defaultValue = "") val pendingTodos: String = "",
    /** See [com.abeswastaken.protontodo.domain.Checklist]. */
    @ColumnInfo(defaultValue = "") val checklist: String = "",
    /** Templates are stored as drafts too, so nothing schedules or sends for them. */
    @ColumnInfo(defaultValue = "0") val isTemplate: Boolean = false,
) {
    val isTask get() = kind == KIND_TASK
    val isEvent get() = kind == KIND_EVENT
    val isRepeating get() = repeatRule != null && dateEpochDay != null
}
