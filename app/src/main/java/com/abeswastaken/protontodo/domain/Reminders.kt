package com.abeswastaken.protontodo.domain

import com.abeswastaken.protontodo.data.Item
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ReminderInstant(val at: LocalDateTime, val occurrence: LocalDate, val isFinal: Boolean)

object Reminders {
    val dayCodes = (1..7).map { "D$it" }
    val weekCodes = (1..10).map { "W$it" }

    fun parse(codes: String): List<String> = codes.split(',').map { it.trim() }.filter { it.length >= 2 }

    fun label(code: String): String {
        val n = code.drop(1).toLongOrNull() ?: return code
        return when (code[0]) {
            'D' -> if (n == 1L) "1 day before" else "$n days before"
            'W' -> if (n == 1L) "1 week before" else "$n weeks before"
            'C' -> "On " + LocalDate.ofEpochDay(n).format(DateTimeFormatter.ofPattern("EEE, MMM d"))
            else -> code
        }
    }

    fun time(minutes: Int): LocalTime = LocalTime.of((minutes / 60) % 24, minutes % 60)

    /** The date this item is about right now: its date, or for a repeating item its next occurrence. */
    fun occurrence(item: Item, today: LocalDate): LocalDate? {
        val date = item.dateEpochDay?.let(LocalDate::ofEpochDay) ?: return null
        val spec = RepeatSpec.parse(item.repeatRule) ?: return date
        val doneAfter = item.doneThroughEpochDay?.let { LocalDate.ofEpochDay(it + 1) }
        val from = if (doneAfter != null && doneAfter.isAfter(today)) doneAfter else today
        return spec.next(date, from)
    }

    /** Every reminder for one occurrence, oldest first. The last one is the force-acknowledged one. */
    fun instants(item: Item, occ: LocalDate): List<ReminderInstant> {
        val apptTime = item.timeMinutes?.let(::time)
        val beforeTime = apptTime ?: time(item.allDayReminderMinutes)
        val times = mutableListOf<LocalDateTime>()
        for (code in parse(item.reminders)) {
            val n = code.drop(1).toLongOrNull() ?: continue
            val day = when (code[0]) {
                'D' -> occ.minusDays(n)
                'W' -> occ.minusWeeks(n)
                'C' -> LocalDate.ofEpochDay(n).takeIf { !it.isAfter(occ) }
                else -> null
            } ?: continue
            times += day.atTime(beforeTime)
        }
        if (item.dayOfReminder) {
            times += if (apptTime == null) occ.atTime(time(item.allDayReminderMinutes))
            else occ.atTime(apptTime).minusMinutes(item.appointmentLeadMinutes.toLong())
        }
        if (item.isRepeating && item.extendedReminderDays > 0) {
            times += occ.minusDays(item.extendedReminderDays.toLong()).atTime(beforeTime)
        }
        val sorted = times.distinct().sorted()
        return sorted.mapIndexed { i, t -> ReminderInstant(t, occ, i == sorted.lastIndex) }
    }

    /**
     * The next reminder that hasn't fired yet. Anything older than the last edit or the last fired
     * reminder is skipped, so editing an item never replays old reminders. A result in the past
     * means it was missed (for example the phone was off) and should fire now.
     */
    fun nextPending(item: Item, now: LocalDateTime, zone: ZoneId): ReminderInstant? {
        if (item.isDraft || item.completedAt != null) return null
        val thresholdMillis = maxOf(item.lastReminderAt ?: 0L, item.updatedAt)
        val threshold = LocalDateTime.ofInstant(Instant.ofEpochMilli(thresholdMillis), zone)
        val first = occurrence(item, now.toLocalDate()) ?: return null
        instants(item, first).firstOrNull { it.at.isAfter(threshold) }?.let { return it }
        if (!item.isRepeating) return null
        // This cycle is finished; look at the next one.
        val second = occurrence(item.copy(doneThroughEpochDay = first.toEpochDay()), now.toLocalDate()) ?: return null
        return instants(item, second).firstOrNull { it.at.isAfter(threshold) }
    }
}
