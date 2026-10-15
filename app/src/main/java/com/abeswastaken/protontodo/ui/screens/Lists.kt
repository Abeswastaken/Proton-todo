@file:OptIn(ExperimentalMaterial3Api::class)

package com.abeswastaken.protontodo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.abeswastaken.protontodo.data.Item
import com.abeswastaken.protontodo.domain.Checklist
import com.abeswastaken.protontodo.settings.Settings
import com.abeswastaken.protontodo.ui.Category
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

fun taskFootnote(item: Item, byId: Map<Long, Item> = emptyMap(), today: LocalDate = LocalDate.now()): String {
    val due = item.dateEpochDay?.let {
        (if (Lists.isOverdue(item, today)) "  ·  Overdue, was due " else "  ·  Due ") + dateText(LocalDate.ofEpochDay(it))
    } ?: ""
    val parent = item.parentId?.let(byId::get)?.let { "  ·  For " + it.title } ?: ""
    val steps = Checklist.progress(item.checklist)?.let { "  ·  $it steps" } ?: ""
    return createdText(item) + due + parent + steps
}

/** Overdue first, then important, then by [then]. */
private fun openTaskOrder(today: LocalDate, then: Comparator<Item>) =
    compareByDescending<Item> { Lists.isOverdue(it, today) }.thenByDescending { it.important }.then(then)

@Composable
fun HomeScreen(items: List<Item>, settings: Settings, onOpen: (Item) -> Unit, onCheck: (Item) -> Unit) {
    val today = LocalDate.now()
    val events = Lists.upcomingEvents(items, today).take(settings.homeEventCount)
    val tasks = Lists.openTasks(items).sortedWith(openTaskOrder(today, compareBy<Item> { it.createdAt })).take(settings.homeTaskCount)
    val byId = items.associateBy { it.id }
    LazyColumn(Modifier, contentPadding = listPadding) {
        item { SectionLabel("Upcoming events") }
        item {
            Card {
                if (events.isEmpty()) EmptyRow("Nothing coming up.")
                events.forEachIndexed { i, d ->
                    if (i > 0) RowDivider()
                    EventRow(d.item.title, dateTimeText(d.item, d.date), d.item.isRepeating, false, d.item.important, d.item.category) { onOpen(d.item) }
                }
            }
        }
        item { SectionLabel("Open tasks") }
        item {
            Card {
                if (tasks.isEmpty()) EmptyRow("All caught up.")
                tasks.forEachIndexed { i, t ->
                    if (i > 0) RowDivider()
                    TaskRow(t.title, taskFootnote(t, byId, today), false, t.important, t.category, Lists.isOverdue(t, today),
                        onCheck = { onCheck(t) }, onClick = { onOpen(t) })
                }
            }
        }
    }
}

/** Chips for Important and each category in use. Shows nothing until there's something to filter by. */
@Composable
fun FilterChips(items: List<Item>, selected: String?, onSelect: (String?) -> Unit) {
    val cats = Category.entries.filter { c -> items.any { it.category == c.name } }
    val hasImportant = items.any { it.important }
    if (cats.isEmpty() && !hasImportant) return
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (hasImportant) FilterChip(
            selected = selected == Lists.IMPORTANT,
            onClick = { onSelect(if (selected == Lists.IMPORTANT) null else Lists.IMPORTANT) },
            label = { Text("Important") },
            leadingIcon = { Icon(Icons.Rounded.PriorityHigh, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) },
        )
        cats.forEach { c ->
            FilterChip(
                selected = selected == c.name,
                onClick = { onSelect(if (selected == c.name) null else c.name) },
                label = { Text(c.label) },
                leadingIcon = { Icon(c.icon, null, modifier = Modifier.size(18.dp)) },
            )
        }
    }
}

/** A task row you can swipe right to complete. */
@Composable
private fun SwipeTaskRow(t: Item, footnote: String, overdue: Boolean, onCheck: () -> Unit, onOpen: () -> Unit) {
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        if (v == SwipeToDismissBoxValue.StartToEnd) onCheck()
        false
    })
    SwipeToDismissBox(
        state = state,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary).padding(start = 20.dp),
                contentAlignment = Alignment.CenterStart,
            ) { Icon(Icons.Filled.Check, contentDescription = "Complete", tint = MaterialTheme.colorScheme.onPrimary) }
        },
    ) {
        Box(Modifier.background(MaterialTheme.colorScheme.surface)) {
            TaskRow(t.title, footnote, false, t.important, t.category, overdue, onCheck = onCheck, onClick = onOpen)
        }
    }
}

@Composable
fun TodoScreen(items: List<Item>, onOpen: (Item) -> Unit, onCheck: (Item) -> Unit, onUncheck: (Item) -> Unit) {
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val today = LocalDate.now()
    val allTasks = items.filter { it.isTask && !it.isDraft }
    val open = Lists.openTasks(items).filter { Lists.matches(it, filter) }
        .sortedWith(openTaskOrder(today, compareByDescending<Item> { it.createdAt }))
    val done = Lists.completedByMonth(items.filter { Lists.matches(it, filter) })
    val byId = items.associateBy { it.id }
    val thisMonth = YearMonth.now()
    LazyColumn(contentPadding = listPadding) {
        item { FilterChips(allTasks, filter) { filter = it } }
        item { SectionLabel("Open (${open.size})") }
        item {
            Card {
                if (open.isEmpty()) EmptyRow(if (filter == null) "No open tasks." else "No open tasks match.")
                open.forEachIndexed { i, t ->
                    if (i > 0) RowDivider()
                    key(t.id) { SwipeTaskRow(t, taskFootnote(t, byId, today), Lists.isOverdue(t, today), { onCheck(t) }, { onOpen(t) }) }
                }
            }
        }
        if (open.isNotEmpty()) item {
            Text(
                "Swipe right to complete.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
            )
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
                        t.important,
                        t.category,
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
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val today = LocalDate.now()
    val shown = items.filter { Lists.matches(it, filter) }
    val upcoming = Lists.upcomingEvents(shown, today)
    val soon = upcoming.filter { !it.date.isAfter(today.plusDays(30)) }
    val later = upcoming.filter { it.date.isAfter(today.plusDays(30)) }
    val past = Lists.pastEvents(shown, today)
    LazyColumn(contentPadding = listPadding) {
        item { FilterChips(items.filter { it.isEvent && !it.isDraft }, filter) { filter = it } }
        item { SectionLabel("Next 30 days") }
        item {
            Card {
                if (soon.isEmpty()) EmptyRow("No events in the next 30 days.")
                soon.forEachIndexed { i, d ->
                    if (i > 0) RowDivider()
                    EventRow(d.item.title, dateTimeText(d.item, d.date), d.item.isRepeating, false, d.item.important, d.item.category) { onOpen(d.item) }
                }
            }
        }
        if (later.isNotEmpty()) item {
            CollapsibleSection("later", "Later", later.size, initiallyOpen = false) {
                later.forEachIndexed { i, d ->
                    if (i > 0) RowDivider()
                    EventRow(d.item.title, dateTimeText(d.item, d.date), d.item.isRepeating, false, d.item.important, d.item.category) { onOpen(d.item) }
                }
            }
        }
        if (past.isNotEmpty()) item { SectionLabel("Past") }
        items(past, key = { "past-" + it.first.toString() }) { (ym, list) ->
            CollapsibleSection("past-$ym", monthTitle(ym), list.size, initiallyOpen = false) {
                list.forEachIndexed { i, d ->
                    if (i > 0) RowDivider()
                    EventRow(d.item.title, dateTimeText(d.item, d.date), false, d.item.completedAt != null, d.item.important, d.item.category) { onOpen(d.item) }
                }
            }
        }
    }
}
