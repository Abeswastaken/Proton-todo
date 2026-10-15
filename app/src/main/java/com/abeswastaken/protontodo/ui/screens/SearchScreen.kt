package com.abeswastaken.protontodo.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.abeswastaken.protontodo.data.Item
import com.abeswastaken.protontodo.ui.Markers
import com.abeswastaken.protontodo.ui.components.Card
import com.abeswastaken.protontodo.ui.components.RowDivider
import com.abeswastaken.protontodo.ui.components.SectionLabel

/** Highlights every case-insensitive match of [query] in [text]. */
fun highlight(text: String, query: String, color: Color): AnnotatedString = buildAnnotatedString {
    if (query.isBlank()) { append(text); return@buildAnnotatedString }
    var i = 0
    val lower = text.lowercase()
    val q = query.lowercase()
    while (i < text.length) {
        val hit = lower.indexOf(q, i)
        if (hit < 0) { append(text.substring(i)); break }
        append(text.substring(i, hit))
        pushStyle(SpanStyle(background = color, fontWeight = FontWeight.SemiBold))
        append(text.substring(hit, hit + q.length))
        pop()
        i = hit + q.length
    }
}

@Composable
fun SearchScreen(items: List<Item>, onOpen: (Item) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val q = query.trim()
    val matches = if (q.isEmpty()) emptyList() else items.filter {
        it.title.contains(q, ignoreCase = true) || it.description.contains(q, ignoreCase = true)
    }
    val groups = listOf(
        "To-Do" to matches.filter { !it.isDraft && it.isTask && it.completedAt == null },
        "Events" to matches.filter { !it.isDraft && it.isEvent },
        "Completed tasks" to matches.filter { !it.isDraft && it.isTask && it.completedAt != null },
        "Drafts" to matches.filter { it.isDraft },
    ).filter { it.second.isNotEmpty() }
    val mark = MaterialTheme.colorScheme.primaryContainer

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search titles and descriptions") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).focusRequester(focus),
            )
        }
        if (q.isNotEmpty() && groups.isEmpty()) item {
            Text("No matches.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        }
        groups.forEach { (title, list) ->
            item(key = title) {
                SectionLabel("$title (${list.size})")
                Card {
                    list.forEachIndexed { i, it ->
                        if (i > 0) RowDivider()
                        Column(Modifier.fillMaxWidth().clickable { onOpen(it) }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Markers(it.important, it.category)
                                Text(highlight(it.title, q, mark), style = MaterialTheme.typography.bodyLarge)
                            }
                            if (it.description.isNotBlank()) {
                                Text(
                                    highlight(it.description, q, mark),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
