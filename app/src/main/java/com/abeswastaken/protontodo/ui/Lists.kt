package com.abeswastaken.protontodo.ui

import com.abeswastaken.protontodo.data.Item
import com.abeswastaken.protontodo.domain.Reminders
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** An event or dated task paired with the date it's currently about. */
data class Dated(val item: Item, val date: LocalDate)

private val shortFmt = DateTimeFormatter.ofPattern("EEE, MMM d")
private val longFmt = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")
private val timeFmt = DateTimeFormatter.ofPattern("h:mm a")

fun dateText(d: LocalDate, today: LocalDate = LocalDate.now()): String =
    d.format(if (d.year == today.year) shortFmt else longFmt)

fun timeText(minutes: Int): String = Reminders.time(minutes).format(timeFmt)

fun dateTimeText(item: Item, d: LocalDate): String =
    dateText(d) + (item.timeMinutes?.let { " at " + timeText(it) } ?: "")

fun millisToDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

fun createdText(item: Item): String =
    "Created " + millisToDate(item.createdAt).format(DateTimeFormatter.ofPattern("M/d"))

fun monthTitle(ym: YearMonth, today: LocalDate = LocalDate.now()): String =
    ym.atDay(1).format(DateTimeFormatter.ofPattern(if (ym.year == today.year) "MMMM" else "MMMM yyyy"))

object Lists {
    private fun live(items: List<Item>) = items.filter { !it.isDraft }

    fun openTasks(items: List<Item>) = live(items).filter { it.isTask && it.completedAt == null }

    /** Completed tasks by month, newest month first, most recently completed first within each. */
    fun completedByMonth(items: List<Item>): List<Pair<YearMonth, List<Item>>> =
        live(items).filter { it.isTask && it.completedAt != null }
            .groupBy { YearMonth.from(millisToDate(it.completedAt!!)) }
            .toSortedMap(Comparator.reverseOrder())
            .map { (ym, list) -> ym to list.sortedByDescending { it.completedAt } }

    /** Events still ahead (including today), soonest first. */
    fun upcomingEvents(items: List<Item>, today: LocalDate): List<Dated> =
        live(items).filter { it.isEvent && it.completedAt == null }
            .mapNotNull { e -> Reminders.occurrence(e, today)?.takeIf { !it.isBefore(today) }?.let { Dated(e, it) } }
            .sortedWith(compareBy<Dated>({ it.date }, { it.item.timeMinutes ?: -1 }))

    /** Past or completed one-off events from the last 12 months, by month, newest first. */
    fun pastEvents(items: List<Item>, today: LocalDate): List<Pair<YearMonth, List<Dated>>> {
        val cutoff = YearMonth.from(today).minusMonths(11).atDay(1)
        return live(items).filter { it.isEvent && !it.isRepeating && it.dateEpochDay != null }
            .map { Dated(it, LocalDate.ofEpochDay(it.dateEpochDay!!)) }
            .filter { (it.date.isBefore(today) || it.item.completedAt != null) && !it.date.isBefore(cutoff) }
            .groupBy { YearMonth.from(it.date) }
            .toSortedMap(Comparator.reverseOrder())
            .map { (ym, list) -> ym to list.sortedByDescending { it.date } }
    }

    fun drafts(items: List<Item>) = items.filter { it.isDraft }.sortedByDescending { it.updatedAt }
}
