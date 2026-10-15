package com.abeswastaken.protontodo

import com.abeswastaken.protontodo.data.Item
import com.abeswastaken.protontodo.domain.Freq
import com.abeswastaken.protontodo.domain.Reminders
import com.abeswastaken.protontodo.domain.RepeatSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class RecurrenceTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun paydayEveryFriday() {
        val spec = RepeatSpec(Freq.WEEKLY, byDays = setOf(DayOfWeek.FRIDAY))
        assertEquals("FREQ=WEEKLY;BYDAY=FR", spec.toRrule(d("2026-10-16")))
        assertEquals(d("2026-10-23"), spec.next(d("2026-10-16"), d("2026-10-17")))
        assertEquals(d("2026-10-16"), spec.next(d("2026-10-16"), d("2026-10-16")))
    }

    @Test
    fun billEveryThreeMonths() {
        val spec = RepeatSpec(Freq.MONTHLY, interval = 3)
        assertEquals("FREQ=MONTHLY;INTERVAL=3", spec.toRrule(d("2026-01-31")))
        assertEquals(d("2026-04-30"), spec.next(d("2026-01-31"), d("2026-02-01")))
        assertEquals(d("2026-07-31"), spec.next(d("2026-01-31"), d("2026-05-01")))
    }

    @Test
    fun secondFridayOfTheMonth() {
        val start = d("2026-10-09") // 2nd Friday of October 2026
        val spec = RepeatSpec(Freq.MONTHLY, nthWeekday = true)
        assertEquals("FREQ=MONTHLY;BYDAY=2FR", spec.toRrule(start))
        assertEquals(d("2026-11-13"), spec.next(start, d("2026-10-10")))
        assertEquals(RepeatSpec(Freq.MONTHLY, nthWeekday = true), RepeatSpec.parse("FREQ=MONTHLY;BYDAY=2FR"))
    }

    @Test
    fun yearlyBirthdayAndLeapDay() {
        val spec = RepeatSpec(Freq.YEARLY)
        assertEquals(d("2027-02-28"), spec.next(d("2024-02-29"), d("2026-03-01")))
        assertEquals(d("2027-03-05"), spec.next(d("2020-03-05"), d("2026-03-06")))
    }

    @Test
    fun everyOtherWeekAndEveryThreeDays() {
        val weekly = RepeatSpec(Freq.WEEKLY, interval = 2)
        assertEquals(d("2026-10-28"), weekly.next(d("2026-10-14"), d("2026-10-15")))
        val daily = RepeatSpec(Freq.DAILY, interval = 3)
        assertEquals(d("2026-10-17"), daily.next(d("2026-10-14"), d("2026-10-15")))
    }

    private val created = LocalDateTime.of(2026, 10, 1, 12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun defaultRemindersForAllDayEvent() {
        val item = Item(title = "Sister birthday", dateEpochDay = d("2026-10-20").toEpochDay(), reminders = "D2", updatedAt = created)
        val list = Reminders.instants(item, d("2026-10-20"))
        assertEquals(listOf(LocalDateTime.of(2026, 10, 18, 6, 0), LocalDateTime.of(2026, 10, 20, 6, 0)), list.map { it.at })
        assertTrue(list.last().isFinal)
        val next = Reminders.nextPending(item, LocalDateTime.of(2026, 10, 18, 7, 0), ZoneOffset.UTC)
        // The 2-day reminder is past the last edit, so it's still pending (fires late).
        assertEquals(LocalDateTime.of(2026, 10, 18, 6, 0), next?.at)
        val fired = item.copy(lastReminderAt = LocalDateTime.of(2026, 10, 18, 6, 0).toInstant(ZoneOffset.UTC).toEpochMilli())
        assertEquals(LocalDateTime.of(2026, 10, 20, 6, 0), Reminders.nextPending(fired, LocalDateTime.of(2026, 10, 18, 7, 0), ZoneOffset.UTC)?.at)
    }

    @Test
    fun appointmentDayOfIsTwoHoursBefore() {
        val item = Item(dateEpochDay = d("2026-10-20").toEpochDay(), timeMinutes = 9 * 60, reminders = "D1", updatedAt = created)
        val list = Reminders.instants(item, d("2026-10-20"))
        assertEquals(listOf(LocalDateTime.of(2026, 10, 19, 9, 0), LocalDateTime.of(2026, 10, 20, 7, 0)), list.map { it.at })
    }

    @Test
    fun repeatingMovesToNextCycleAndAddsExtended() {
        val item = Item(
            dateEpochDay = d("2026-10-16").toEpochDay(),
            repeatRule = "FREQ=WEEKLY;BYDAY=FR",
            reminders = "",
            extendedReminderDays = 3,
            updatedAt = created,
            lastReminderAt = LocalDateTime.of(2026, 10, 16, 6, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        val next = Reminders.nextPending(item, LocalDateTime.of(2026, 10, 16, 8, 0), ZoneOffset.UTC)
        assertEquals(LocalDateTime.of(2026, 10, 20, 6, 0), next?.at)
        assertEquals(d("2026-10-23"), next?.occurrence)
    }

    @Test
    fun completedAndDraftsHaveNoReminders() {
        val item = Item(dateEpochDay = d("2026-10-20").toEpochDay(), reminders = "D2", updatedAt = created)
        val now = LocalDateTime.of(2026, 10, 10, 0, 0)
        assertNull(Reminders.nextPending(item.copy(completedAt = 1), now, ZoneOffset.UTC))
        assertNull(Reminders.nextPending(item.copy(isDraft = true), now, ZoneOffset.UTC))
    }
}
