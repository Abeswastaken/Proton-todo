package com.abeswastaken.protontodo.domain

/** Small tick-off steps inside an item, stored one per line as `1|text` (done) or `0|text`. */
object Checklist {
    data class Entry(val text: String, val done: Boolean = false)

    fun parse(stored: String): List<Entry> = stored.lines().filter { it.length >= 2 && it[1] == '|' }
        .map { Entry(it.substring(2), it[0] == '1') }

    fun encode(entries: List<Entry>): String = entries.filter { it.text.isNotBlank() }
        .joinToString("\n") { (if (it.done) "1|" else "0|") + it.text.replace('\n', ' ').trim() }

    /** "2/5", or null when there's no checklist. */
    fun progress(stored: String): String? {
        val all = parse(stored)
        return if (all.isEmpty()) null else "${all.count { it.done }}/${all.size}"
    }
}

/**
 * A to-do waiting to be created for an event, stored one per line as `days<TAB>title`.
 * [daysBefore] is how long before the event it's due; null means no due date.
 */
data class LinkedTodo(val title: String, val daysBefore: Int? = null) {
    companion object {
        fun parse(stored: String): List<LinkedTodo> = stored.lines().filter { it.isNotBlank() }.map { line ->
            val tab = line.indexOf('\t')
            if (tab < 0) LinkedTodo(line.trim()) else LinkedTodo(line.substring(tab + 1).trim(), line.substring(0, tab).toIntOrNull())
        }.filter { it.title.isNotEmpty() }

        fun encode(todos: List<LinkedTodo>): String = todos.filter { it.title.isNotBlank() }
            .joinToString("\n") { (it.daysBefore?.toString() ?: "") + "\t" + it.title.replace('\n', ' ').trim() }
    }
}
