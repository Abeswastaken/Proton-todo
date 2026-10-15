package com.abeswastaken.protontodo.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.abeswastaken.protontodo.data.Item
import com.abeswastaken.protontodo.settings.Settings
import com.abeswastaken.protontodo.ui.Lists
import com.abeswastaken.protontodo.ui.components.Card
import com.abeswastaken.protontodo.ui.components.CollapsibleSection
import com.abeswastaken.protontodo.ui.components.EmptyRow
import com.abeswastaken.protontodo.ui.components.EventRow
import com.abeswastaken.protontodo.ui.components.RowDivider
import com.abeswastaken.protontodo.ui.components.SectionLabel
import com.abeswastaken.protontodo.ui.components.TaskRow
import com.abeswastaken.protontodo.ui.createdText
import com.abeswastaken.protontodo.ui.dateText
import com.abeswastaken.protontodo.ui.dateTimeText
import com.abeswastaken.protontodo.ui.millisToDate
import com.abeswastaken.protontodo.ui.monthTitle
import java.time.LocalDate
import java.time.YearMonth

private val listPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)

fun taskFootnote(item: Item): String =
    createdText(item) + (item.dateEpochDay?.let { "  ·  Due " + dateText(LocalDate.ofEpochDay(it)) } ?: "")

@Composable
fun HomeScreen(items: List<Item>, settings: Settings, onOpen: (Item) -> Unit, onCheck: (Item) -> Unit) {
    val today = LocalDate.now()
    val events = Lists.upcomingEvents(items, today).take(settings.homeEventCount)
    val tasks = Lists.openTasks(items).sortedBy { it.createdAt }.take(settings.homeTaskCount)
    LazyColumn(Modifier, contentPadding = listPadding) {
        item { SectionLabel("Upcoming events") }
        item {
            Card {
                if (events.isEmpty()) EmptyRow("Nothing coming up.")
                events.forEachIndexed { i, d ->
                    if (i > 0) RowDivider()
                    EventRow(d.item.title, dateTimeText(d.item, d.date), d.item.isRepeating, false) { onOpen(d.item) }
                }
            }
        }
        item { SectionLabel("Open tasks") }
        item {
            Card {
                if (tasks.isEmpty()) EmptyRow("All caught up.")
                tasks.forEachIndexed { i, t ->
                    if (i > 0) RowDivider()
                    TaskRow(t.title, taskFootnote(t), false, onCheck = { onCheck(t) }, onClick = { onOpen(t) })
                }
            }
        }
    }
}

@Composable
fun TodoScreen(items: List<Item>, onOpen: (Item) -> Unit, onCheck: (Item) -> Unit, onUncheck: (Item) -> Unit) {
    val open = Lists.openTasks(items).sortedByDescending { it.createdAt }
    val done = Lists.completedByMonth(items)
    val thisMonth = YearMonth.now()
    LazyColumn(contentPadding = listPadding) {
        item { SectionLabel("Open (${open.size})") }
        item {
            Card {
                if (open.isEmpty()) EmptyRow("No open tasks.")
                open.forEachIndexed { i, t ->
                    if (i > 0) RowDivider()
                    TaskRow(t.title, taskFootnote(t), false, onCheck = { onCheck(t) }, onClick = { onOpen(t) })
                }
            }
        }
        if (done.isNotEmpty()) item { SectionLabel("Completed") }
        items(done, key = { it.first.toString() }) { (ym, list) ->
            CollapsibleSection("done-$ym", monthTitle(ym), list.size, initiallyOpen = ym == thisMonth) {
                list.forEachIndexed { i, t ->
                    if (i > 0) RowDivider()
                    TaskRow(
                        t.title,
                        "Completed " + dateText(millisToDate(t.completedAt!!)),
                        true,
                        onCheck = { onUncheck(t) },
                        onClick = { onOpen(t) },
                    )
                }
            }
        }
    }
}

@Composable
fun EventsScreen(items: List<Item>, onOpen: (Item) -> Unit) {
    val today = LocalDate.now()
    val upcoming = Lists.upcomingEvents(items, today)
    val soon = upcoming.filter { !it.date.isAfter(today.plusDays(30)) }
    val later = upcoming.filter { it.date.isAfter(today.plusDays(30)) }
    val past = Lists.pastEvents(items, today)
    LazyColumn(contentPadding = listPadding) {
        item { SectionLabel("Next 30 days") }
        item {
            Card {
                if (soon.isEmpty()) EmptyRow("No events in the next 30 days.")
                soon.forEachIndexed { i, d ->
                    if (i > 0) RowDivider()
                    EventRow(d.item.title, dateTimeText(d.item, d.date), d.item.isRepeating, false) { onOpen(d.item) }
                }
            }
        }
        if (later.isNotEmpty()) item {
            CollapsibleSection("later", "Later", later.size, initiallyOpen = false) {
                later.forEachIndexed { i, d ->
                    if (i > 0) RowDivider()
                    EventRow(d.item.title, dateTimeText(d.item, d.date), d.item.isRepeating, false) { onOpen(d.item) }
                }
            }
        }
        if (past.isNotEmpty()) item { SectionLabel("Past") }
        items(past, key = { "past-" + it.first.toString() }) { (ym, list) ->
            CollapsibleSection("past-$ym", monthTitle(ym), list.size, initiallyOpen = false) {
                list.forEachIndexed { i, d ->
                    if (i > 0) RowDivider()
                    EventRow(d.item.title, dateTimeText(d.item, d.date), false, d.item.completedAt != null) { onOpen(d.item) }
                }
            }
        }
    }
}
