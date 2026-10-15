package com.abeswastaken.protontodo.mail

import com.abeswastaken.protontodo.data.Item
import com.abeswastaken.protontodo.domain.Reminders
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Builds the iCalendar invite that Proton Mail auto-adds to Proton Calendar. Updates reuse the
 * same UID with a higher SEQUENCE so Proton replaces the event instead of adding a second one.
 */
object Ics {
    private val dateFmt = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val localFmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val utcFmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

    fun invite(item: Item, from: String, to: String, zone: ZoneId = ZoneId.systemDefault()): String {
        val date = LocalDate.ofEpochDay(requireNotNull(item.dateEpochDay))
        val lines = mutableListOf(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:-//Abeswastaken//Proton To-Do//EN",
            "CALSCALE:GREGORIAN",
            "METHOD:REQUEST",
            "BEGIN:VEVENT",
            "UID:${item.calendarUid}",
            "SEQUENCE:${item.calendarSequence}",
            "DTSTAMP:${ZonedDateTime.now(ZoneOffset.UTC).format(utcFmt)}",
        )
        val time = item.timeMinutes
        if (time == null) {
            lines += "DTSTART;VALUE=DATE:${date.format(dateFmt)}"
            lines += "DTEND;VALUE=DATE:${date.plusDays(1).format(dateFmt)}"
            lines += "TRANSP:TRANSPARENT"
        } else {
            // Appointments have a start time and no duration.
            lines += "DTSTART;TZID=${zone.id}:${date.atTime(Reminders.time(time)).format(localFmt)}"
        }
        item.repeatRule?.let { lines += "RRULE:$it" }
        lines += "SUMMARY:${escape(item.title)}"
        if (item.description.isNotBlank()) lines += "DESCRIPTION:${escape(item.description.take(2000))}"
        lines += "ORGANIZER;CN=Proton To-Do:mailto:$from"
        lines += "ATTENDEE;CUTYPE=INDIVIDUAL;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=FALSE:mailto:$to"
        lines += "STATUS:CONFIRMED"
        lines += "END:VEVENT"
        lines += "END:VCALENDAR"
        return lines.joinToString("\r\n") { fold(it) } + "\r\n"
    }

    fun escape(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r\n", "\\n").replace("\n", "\\n")

    /** Folds long lines at 73 characters, as RFC 5545 asks. */
    private fun fold(line: String): String {
        if (line.length <= 73) return line
        return line.chunked(73).joinToString("\r\n ")
    }
}
