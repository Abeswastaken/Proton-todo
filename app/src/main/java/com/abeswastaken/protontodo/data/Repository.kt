package com.abeswastaken.protontodo.data

import android.content.Context
import com.abeswastaken.protontodo.alarm.Notifications
import com.abeswastaken.protontodo.alarm.ReminderScheduler
import com.abeswastaken.protontodo.domain.Reminders
import com.abeswastaken.protontodo.mail.MailQueue
import com.abeswastaken.protontodo.settings.SettingsStore
import java.time.LocalDate
import java.util.UUID

class Repository(
    private val context: Context,
    private val dao: ItemDao,
    private val settings: SettingsStore,
) {
    val items = dao.observeAll()

    suspend fun get(id: Long) = dao.get(id)

    suspend fun all() = dao.all()

    /** Saves for real: sends or updates the calendar invite and schedules reminders. */
    suspend fun save(item: Item): Long {
        val now = System.currentTimeMillis()
        val existing = if (item.id != 0L) dao.get(item.id) else null
        val firstRealSave = existing == null || existing.isDraft
        var next = item.copy(
            isDraft = false,
            pendingTodos = "",
            updatedAt = now,
            createdAt = if (firstRealSave) now else existing!!.createdAt,
            lastReminderAt = if (firstRealSave) null else item.lastReminderAt,
        )
        val calendarChanged = existing == null || existing.isDraft || existing.calendarUid == null ||
            existing.title != next.title || existing.description != next.description ||
            existing.dateEpochDay != next.dateEpochDay || existing.timeMinutes != next.timeMinutes ||
            existing.repeatRule != next.repeatRule
        val sendInvite = next.dateEpochDay != null && next.addToCalendar && calendarChanged
        if (sendInvite) {
            next = if (next.calendarUid == null) next.copy(calendarUid = "${UUID.randomUUID()}@protontodo", calendarSequence = 0)
            else next.copy(calendarSequence = next.calendarSequence + 1)
        }
        val id = if (existing == null) dao.insert(next.copy(id = 0)) else { dao.update(next); next.id }
        next = next.copy(id = id)
        createLinkedTodos(item.copy(id = id))
        if (sendInvite) MailQueue.invite(context, next, settings.current)
        ReminderScheduler.reschedule(context, next)
        return id
    }

    /** Creates the undated to-dos waiting on an event, such as "Print the new insurance card". */
    private suspend fun createLinkedTodos(event: Item) {
        event.pendingTodos.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach {
            save(Item(kind = KIND_TASK, title = it, parentId = event.id, addToCalendar = false, emailReminders = event.emailReminders))
        }
    }

    /** Drafts stay local: no reminders, emails or calendar invites. */
    suspend fun saveDraft(item: Item): Long {
        val now = System.currentTimeMillis()
        val d = item.copy(isDraft = true, updatedAt = now, createdAt = if (item.createdAt == 0L) now else item.createdAt)
        return if (d.id == 0L) dao.insert(d) else { dao.update(d); d.id }
    }

    suspend fun delete(item: Item) {
        ReminderScheduler.cancel(context, item.id)
        Notifications.cancel(context, item.id)
        dao.delete(item)
    }

    /**
     * Completes a task or event. A repeating item moves on to its next occurrence instead.
     * Returns the next occurrence for a repeating item, or null.
     */
    suspend fun complete(item: Item): LocalDate? {
        val now = System.currentTimeMillis()
        val occ = if (item.isRepeating) Reminders.occurrence(item, LocalDate.now()) else null
        val next = if (occ != null) item.copy(doneThroughEpochDay = occ.toEpochDay(), lastReminderAt = now, pendingAckAt = null)
        else item.copy(completedAt = now, pendingAckAt = null)
        dao.update(next)
        Notifications.cancel(context, item.id)
        ReminderScheduler.reschedule(context, next)
        return occ?.let { Reminders.occurrence(next, LocalDate.now()) }
    }

    suspend fun reopen(item: Item) {
        val next = item.copy(completedAt = null, lastReminderAt = System.currentTimeMillis())
        dao.update(next)
        ReminderScheduler.reschedule(context, next)
    }

    suspend fun markFired(id: Long, at: Long, final: Boolean) {
        val item = dao.get(id) ?: return
        dao.update(item.copy(lastReminderAt = at, pendingAckAt = if (final) at else item.pendingAckAt))
    }

    suspend fun acknowledge(id: Long) {
        val item = dao.get(id) ?: return
        dao.update(item.copy(pendingAckAt = null))
        Notifications.cancel(context, id)
    }

    suspend fun replaceAll(items: List<Item>) {
        dao.all().forEach { ReminderScheduler.cancel(context, it.id) }
        dao.clear()
        dao.insertAll(items)
        ReminderScheduler.rescheduleAll(context)
    }
}
