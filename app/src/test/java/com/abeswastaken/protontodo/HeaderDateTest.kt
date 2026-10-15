package com.abeswastaken.protontodo

import com.abeswastaken.protontodo.ui.headerDate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class HeaderDateTest {
    @Test
    fun formatsWithOrdinal() {
        Locale.setDefault(Locale.US)
        assertEquals("Wednesday, October 14th", headerDate(LocalDate.of(2026, 10, 14)))
        assertEquals("Thursday, October 1st", headerDate(LocalDate.of(2026, 10, 1)))
        assertEquals("Sunday, October 11th", headerDate(LocalDate.of(2026, 10, 11)))
        assertEquals("Thursday, October 22nd", headerDate(LocalDate.of(2026, 10, 22)))
    }
}
