package com.abeswastaken.protontodo.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class Freq { DAILY, WEEKLY, MONTHLY, YEARLY }

/**
 * The repeat patterns the app supports, stored as an iCalendar RRULE so the same text goes into
 * the Proton Calendar invite. Monthly can repeat on the same day number, or on the same weekday
 * position as the start date (for example the 2nd Friday).
 */
data class RepeatSpec(
    val freq: Freq,
    val interval: Int = 1,
    val byDays: Set<DayOfWeek> = emptySet(),
    val nthWeekday: Boolean = false,
) {
    fun toRrule(start: LocalDate): String {
        val parts = mutableListOf("FREQ=${freq.name}")
        if (interval > 1) parts += "INTERVAL=$interval"
        when (freq) {
            Freq.WEEKLY -> if (byDays.isNotEmpty()) {
                parts += "BYDAY=" + byDays.sorted().joinToString(",") { code(it) }
            }
            Freq.MONTHLY -> if (nthWeekday) parts += "BYDAY=${nth(start)}${code(start.dayOfWeek)}"
            else -> Unit
        }
        return parts.joinToString(";")
    }

    /** The first occurrence on or after [onOrAfter]. Occurrences never come before [start]. */
    fun next(start: LocalDate, onOrAfter: LocalDate): LocalDate? {
        val from = if (onOrAfter.isAfter(start)) onOrAfter else start
        val iv = interval.coerceAtLeast(1).toLong()
        when (freq) {
            Freq.DAILY -> {
                val d = ChronoUnit.DAYS.between(start, from)
                return start.plusDays(((d + iv - 1) / iv) * iv)
            }
            Freq.WEEKLY -> {
                val days = byDays.ifEmpty { setOf(start.dayOfWeek) }
                val startWeek = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                var d = from
                repeat((7 * iv + 7).toInt()) {
                    val week = ChronoUnit.WEEKS.between(startWeek, d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
                    if (d.dayOfWeek in days && week % iv == 0L) return d
                    d = d.plusDays(1)
                }
                return null
            }
            Freq.MONTHLY -> {
                var ym = YearMonth.from(start)
                val target = YearMonth.from(from)
                val diff = ChronoUnit.MONTHS.between(ym, target)
                if (diff > 0) ym = ym.plusMonths(((diff + iv - 1) / iv) * iv)
                repeat(48) {
                    val cand = monthlyDate(ym, start)
                    if (cand >= from) return cand
                    ym = ym.plusMonths(iv)
                }
                return null
            }
            Freq.YEARLY -> {
                var year = start.year.toLong()
                val diff = from.year - start.year
                if (diff > 0) year += ((diff + iv - 1) / iv) * iv
                repeat(8) {
                    val ym = YearMonth.of(year.toInt(), start.month)
                    val cand = ym.atDay(minOf(start.dayOfMonth, ym.lengthOfMonth()))
                    if (cand >= from) return cand
                    year += iv
                }
                return null
            }
        }
    }

    fun describe(start: LocalDate): String {
        val every = if (interval > 1) "Every $interval" else "Every"
        return when (freq) {
            Freq.DAILY -> if (interval > 1) "$every days" else "Every day"
            Freq.WEEKLY -> {
                val days = byDays.ifEmpty { setOf(start.dayOfWeek) }.sorted()
                val names = days.joinToString(", ") {
                    it.getDisplayName(if (days.size == 1) TextStyle.FULL else TextStyle.SHORT, Locale.getDefault())
                }
                if (interval > 1) "$every weeks on $names" else "Every $names"
            }
            Freq.MONTHLY -> {
                val prefix = if (interval > 1) "$every months" else "Monthly"
                if (nthWeekday) "$prefix on the ${ordinalWeek(start)} ${start.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}"
                else "$prefix on day ${start.dayOfMonth}"
            }
            Freq.YEARLY -> {
                val md = "${start.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${start.dayOfMonth}"
                if (interval > 1) "$every years on $md" else "Every year on $md"
            }
        }
    }

    companion object {
        fun parse(rrule: String?): RepeatSpec? {
            if (rrule.isNullOrBlank()) return null
            val map = rrule.split(";").mapNotNull {
                val i = it.indexOf('=')
                if (i > 0) it.substring(0, i).uppercase() to it.substring(i + 1) else null
            }.toMap()
            val freq = runCatching { Freq.valueOf(map["FREQ"] ?: return null) }.getOrNull() ?: return null
            val interval = map["INTERVAL"]?.toIntOrNull() ?: 1
            val byDay = map["BYDAY"]
            return when {
                freq == Freq.MONTHLY && byDay != null -> RepeatSpec(freq, interval, nthWeekday = true)
                freq == Freq.WEEKLY && byDay != null -> RepeatSpec(freq, interval, byDay.split(",").mapNotNull { fromCode(it.takeLast(2)) }.toSet())
                else -> RepeatSpec(freq, interval)
            }
        }

        private val codes = mapOf(
            DayOfWeek.MONDAY to "MO", DayOfWeek.TUESDAY to "TU", DayOfWeek.WEDNESDAY to "WE",
            DayOfWeek.THURSDAY to "TH", DayOfWeek.FRIDAY to "FR", DayOfWeek.SATURDAY to "SA", DayOfWeek.SUNDAY to "SU",
        )

        fun code(d: DayOfWeek) = codes.getValue(d)
        fun fromCode(c: String) = codes.entries.firstOrNull { it.value == c.uppercase() }?.key

        /** Week position of [d] within its month, 1 to 4, or -1 for a 5th (last) weekday. */
        fun nth(d: LocalDate): Int = ((d.dayOfMonth - 1) / 7 + 1).let { if (it == 5) -1 else it }

        fun ordinalWeek(d: LocalDate) = when (nth(d)) { 1 -> "1st"; 2 -> "2nd"; 3 -> "3rd"; 4 -> "4th"; else -> "last" }
    }

    private fun monthlyDate(ym: YearMonth, start: LocalDate): LocalDate =
        if (nthWeekday) {
            val n = nth(start)
            if (n == -1) ym.atEndOfMonth().with(TemporalAdjusters.previousOrSame(start.dayOfWeek))
            else ym.atDay(1).with(TemporalAdjusters.dayOfWeekInMonth(n, start.dayOfWeek))
        } else {
            ym.atDay(minOf(start.dayOfMonth, ym.lengthOfMonth()))
        }
}
