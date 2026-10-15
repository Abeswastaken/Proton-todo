package com.abeswastaken.protontodo

import com.abeswastaken.protontodo.domain.Checklist
import com.abeswastaken.protontodo.domain.LinkedTodo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChecklistTest {
    @Test
    fun checklistRoundTrips() {
        val entries = listOf(Checklist.Entry("Buy card", true), Checklist.Entry("Mail it | today"), Checklist.Entry("  "))
        val stored = Checklist.encode(entries)
        assertEquals(listOf(Checklist.Entry("Buy card", true), Checklist.Entry("Mail it | today")), Checklist.parse(stored))
        assertEquals("1/2", Checklist.progress(stored))
        assertNull(Checklist.progress(""))
    }

    @Test
    fun linkedTodosRoundTrip() {
        val todos = listOf(LinkedTodo("Print card", 7), LinkedTodo("Call agent"), LinkedTodo(" "))
        assertEquals(listOf(LinkedTodo("Print card", 7), LinkedTodo("Call agent")), LinkedTodo.parse(LinkedTodo.encode(todos)))
    }

    @Test
    fun plainLinesStillParse() {
        assertEquals(listOf(LinkedTodo("Print card")), LinkedTodo.parse("Print card\n"))
    }
}
