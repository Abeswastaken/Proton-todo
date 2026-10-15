@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.abeswastaken.protontodo.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.abeswastaken.protontodo.data.Item
import com.abeswastaken.protontodo.data.KIND_EVENT
import com.abeswastaken.protontodo.data.KIND_TASK
import com.abeswastaken.protontodo.domain.Checklist
import com.abeswastaken.protontodo.domain.Freq
import com.abeswastaken.protontodo.domain.LinkedTodo
import com.abeswastaken.protontodo.domain.Reminders
import com.abeswastaken.protontodo.domain.RepeatSpec
import com.abeswastaken.protontodo.settings.Settings
import com.abeswastaken.protontodo.ui.components.Card
import com.abeswastaken.protontodo.ui.components.ChoiceRow
import com.abeswastaken.protontodo.ui.components.RowDivider
import com.abeswastaken.protontodo.ui.components.SectionLabel
import com.abeswastaken.protontodo.ui.components.SwitchRow
import com.abeswastaken.protontodo.ui.components.TaskRow
import com.abeswastaken.protontodo.ui.Category
import com.abeswastaken.protontodo.ui.components.ValueRow
import com.abeswastaken.protontodo.ui.dateText
import com.abeswastaken.protontodo.ui.timeText
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

/** Everything the New form and the detail view can edit. */
data class FormState(
    val kind: String,
    val title: String = "",
    val description: String = "",
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val reminders: List<String> = emptyList(),
    val dayOf: Boolean = true,
    val allDayMinutes: Int = 360,
    val leadMinutes: Int = 120,
    val addToCalendar: Boolean = true,
    val email: Boolean = true,
    val repeat: RepeatSpec? = null,
    val extendedDays: Int = 14,
    val important: Boolean = false,
    val category: String? = null,
    /** To-dos to create for this event when it's saved. */
    val newTodos: List<LinkedTodo> = emptyList(),
    val checklist: List<Checklist.Entry> = emptyList(),
) {
    val kindName get() = if (kind == KIND_EVENT) "event" else "to-do"

    /** The linked to-dos worth creating: events only, blank rows skipped, due dates only with a date. */
    val todosToCreate: List<LinkedTodo> get() = if (kind != KIND_EVENT) emptyList() else
        newTodos.filter { it.title.isNotBlank() }.map { if (date == null) it.copy(daysBefore = null) else it }

    /** True when the only differences are ticked checklist steps, which save on their own. */
    fun sameIgnoringTicks(other: FormState) =
        copy(checklist = checklist.map { it.copy(done = false) }) == other.copy(checklist = other.checklist.map { it.copy(done = false) })

    val valid get() = title.isNotBlank() && (kind == KIND_TASK || date != null)

    fun toItem(base: Item): Item = base.copy(
        kind = kind,
        title = title.trim(),
        description = description.trim(),
        dateEpochDay = date?.toEpochDay(),
        timeMinutes = if (date == null) null else time?.let { it.hour * 60 + it.minute },
        reminders = reminders.joinToString(","),
        dayOfReminder = dayOf,
        allDayReminderMinutes = allDayMinutes,
        appointmentLeadMinutes = leadMinutes,
        addToCalendar = addToCalendar,
        emailReminders = email,
        repeatRule = if (date == null) null else repeat?.toRrule(date),
        extendedReminderDays = if (repeat == null) 0 else extendedDays,
        important = important,
        category = category,
        pendingTodos = LinkedTodo.encode(todosToCreate),
        checklist = Checklist.encode(checklist),
    )

    companion object {
        fun defaults(s: Settings, today: LocalDate = LocalDate.now()) = FormState(
            kind = s.defaultKind,
            date = if (s.defaultKind == KIND_EVENT) today else null,
            reminders = Reminders.parse(s.defaultReminder),
            dayOf = s.defaultDayOf,
            allDayMinutes = s.allDayReminderMinutes,
            leadMinutes = s.appointmentLeadMinutes,
            addToCalendar = s.defaultAddToCalendar,
            email = s.defaultEmail,
            extendedDays = s.extendedReminderDays,
        )

        fun from(item: Item, s: Settings) = FormState(
            kind = item.kind,
            title = item.title,
            description = item.description,
            date = item.dateEpochDay?.let(LocalDate::ofEpochDay),
            time = item.timeMinutes?.let(Reminders::time),
            reminders = Reminders.parse(item.reminders),
            dayOf = item.dayOfReminder,
            allDayMinutes = item.allDayReminderMinutes,
            leadMinutes = item.appointmentLeadMinutes,
            addToCalendar = item.addToCalendar,
            email = item.emailReminders,
            repeat = RepeatSpec.parse(item.repeatRule),
            extendedDays = if (item.extendedReminderDays > 0) item.extendedReminderDays else s.extendedReminderDays,
            important = item.important,
            category = item.category,
            newTodos = LinkedTodo.parse(item.pendingTodos),
            checklist = Checklist.parse(item.checklist),
        )
    }
}

val leadOptions = listOf(0, 15, 30, 60, 120, 180, 360, 720, 1440)
fun leadText(m: Int) = when {
    m == 0 -> "At start time"
    m < 60 -> "$m min before"
    m % 60 == 0 && m < 1440 -> "${m / 60} hr before"
    m == 1440 -> "1 day before"
    else -> "$m min before"
}

val dueOptions = listOf<Int?>(null, 0, 1, 2, 3, 7, 14, 30, 60)
fun dueText(d: Int?) = when (d) {
    null -> "No due date"
    0 -> "On the day"
    1 -> "1 day before"
    7 -> "1 week before"
    14 -> "2 weeks before"
    30 -> "30 days before"
    60 -> "60 days before"
    else -> "$d days before"
}

val extendedOptions = listOf(0, 7, 14, 21, 28, 42, 56, 70)
fun extendedText(d: Int) = when {
    d == 0 -> "Off"
    d % 7 == 0 -> if (d == 7) "1 week before" else "${d / 7} weeks before"
    else -> "$d days before"
}

private const val CUSTOM = "CUSTOM"

enum class FormMode { NEW, DRAFT, DETAIL }

/**
 * The New form, also used to reopen drafts and for the detail view. Sections appear as choices
 * are made: reminders once there's a date, and the repeat pattern once Repeat is on.
 */
@Composable
fun ItemForm(
    initial: FormState,
    mode: FormMode,
    completed: Boolean = false,
    /** To-dos already linked to this event. */
    children: List<Item> = emptyList(),
    /** The event this to-do was made for. */
    parent: Item? = null,
    onOpenItem: (Item) -> Unit = {},
    onToggleChild: (Item) -> Unit = {},
    /** NEW mode: templates to start from, by name. */
    templates: List<Pair<String, FormState>> = emptyList(),
    /** DETAIL mode: ticking a step saves it straight away. */
    onChecklistTick: (List<Checklist.Entry>) -> Unit = {},
    onDuplicate: () -> Unit = {},
    onSaveTemplate: (String) -> Unit = {},
    onResendInvite: () -> Unit = {},
    onDelete: () -> Unit = {},
    onDirtyChange: (Boolean) -> Unit,
    onSave: (FormState) -> Unit,
    onDraft: (FormState) -> Unit = {},
    onDiscard: () -> Unit,
    onComplete: () -> Unit = {},
    onReopen: () -> Unit = {},
) {
    var st by remember(initial) { mutableStateOf(initial) }
    var showDescription by remember(initial) { mutableStateOf(initial.description.isNotBlank()) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var pickAllDayTime by remember { mutableStateOf(false) }
    var customReminderIndex by remember { mutableStateOf<Int?>(null) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    var templateName by remember { mutableStateOf<String?>(null) }
    var pickedTemplate by remember(initial) { mutableStateOf("") }
    val dirty = if (mode == FormMode.DETAIL) !st.sameIgnoringTicks(initial) else st != initial

    LaunchedEffect(dirty) { onDirtyChange(dirty) }
    DisposableEffect(Unit) { onDispose { onDirtyChange(false) } }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
    ) {
        if (mode == FormMode.NEW && templates.isNotEmpty()) {
            SectionLabel("Template")
            Card {
                ChoiceRow("Start from", pickedTemplate, listOf("") + templates.map { it.first }, { it.ifEmpty { "Blank" } }) { name ->
                    pickedTemplate = name
                    st = templates.firstOrNull { it.first == name }?.second ?: initial
                    showDescription = st.description.isNotBlank()
                }
            }
        }

        SectionLabel("Type")
        Card {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(KIND_TASK to "To-Do", KIND_EVENT to "Event").forEach { (k, label) ->
                    Row(
                        Modifier.clickable {
                            st = st.copy(kind = k, date = if (k == KIND_EVENT && st.date == null) LocalDate.now() else st.date)
                        }.padding(end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = st.kind == k, onClick = {
                            st = st.copy(kind = k, date = if (k == KIND_EVENT && st.date == null) LocalDate.now() else st.date)
                        })
                        Text(label)
                    }
                }
            }
        }

        SectionLabel("Details")
        OutlinedTextField(
            value = st.title,
            onValueChange = { st = st.copy(title = it) },
            label = { Text("Title") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        if (showDescription) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = st.description,
                onValueChange = { st = st.copy(description = it) },
                label = { Text("Description") },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            TextButton(onClick = { showDescription = true }) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text("Add description")
            }
        }

        if (parent != null) {
            SectionLabel("For event")
            Card {
                ValueRow(parent.title, parent.dateEpochDay?.let { dateText(LocalDate.ofEpochDay(it)) } ?: "") { onOpenItem(parent) }
            }
        }

        SectionLabel("Marker")
        Card {
            SwitchRow("Important", st.important, "Shows a red ! in front of the title") { st = st.copy(important = it) }
            RowDivider()
            ChoiceRow("Icon", Category.of(st.category), listOf<Category?>(null) + Category.entries, { it?.label ?: "None" }) {
                st = st.copy(category = it?.name)
            }
        }

        SectionLabel("When")
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    ValueRow("Date", st.date?.let { dateText(it) } ?: "No due date") { pickDate = true }
                }
                if (st.date != null && st.kind == KIND_TASK) {
                    IconButton(onClick = { st = st.copy(date = null, time = null, repeat = null) }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Clear date")
                    }
                }
            }
            RowDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    ValueRow("Time", if (st.date == null) "Set a date first" else st.time?.let { timeText(it.hour * 60 + it.minute) } ?: "All day", enabled = st.date != null) { pickTime = true }
                }
                if (st.time != null) {
                    IconButton(onClick = { st = st.copy(time = null) }) { Icon(Icons.Outlined.Close, contentDescription = "Make all-day") }
                }
            }
        }

        if (st.date != null) {
            SectionLabel("Reminders")
            Card {
                st.reminders.forEachIndexed { i, code ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            ChoiceRow(
                                label = "Remind me",
                                value = code,
                                options = Reminders.dayCodes + Reminders.weekCodes + CUSTOM,
                                display = { if (it == CUSTOM) "Custom date..." else Reminders.label(it) },
                            ) { picked ->
                                if (picked == CUSTOM) customReminderIndex = i
                                else st = st.copy(reminders = st.reminders.toMutableList().also { l -> l[i] = picked })
                            }
                        }
                        IconButton(onClick = { st = st.copy(reminders = st.reminders.filterIndexed { j, _ -> j != i }) }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Remove reminder")
                        }
                    }
                    RowDivider()
                }
                TextButton(onClick = { st = st.copy(reminders = st.reminders + "D1") }, modifier = Modifier.padding(start = 4.dp)) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text("Add reminder")
                }
                RowDivider()
                SwitchRow("Day-of reminder", st.dayOf) { st = st.copy(dayOf = it) }
                if (st.dayOf) {
                    if (st.time == null) {
                        ValueRow("At", timeText(st.allDayMinutes)) { pickAllDayTime = true }
                    } else {
                        ChoiceRow("When", st.leadMinutes, leadOptions, ::leadText) { st = st.copy(leadMinutes = it) }
                    }
                }
                RowDivider()
                Text(
                    "The last reminder has to be acknowledged before it goes away.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }

            SectionLabel("Options")
            Card {
                SwitchRow("Add to calendar", st.addToCalendar, "Sends an invite to Proton Calendar") { st = st.copy(addToCalendar = it) }
                RowDivider()
                SwitchRow("Email reminders", st.email) { st = st.copy(email = it) }
                RowDivider()
                SwitchRow("Repeat", st.repeat != null) { on ->
                    st = st.copy(repeat = if (on) RepeatSpec(Freq.WEEKLY, byDays = setOf(st.date!!.dayOfWeek)) else null)
                }
                st.repeat?.let { spec -> RepeatEditor(spec, st.date!!) { st = st.copy(repeat = it) } }
                if (st.repeat != null) {
                    RowDivider()
                    ChoiceRow("Extended reminder", st.extendedDays, extendedOptions, ::extendedText) { st = st.copy(extendedDays = it) }
                }
            }
        }

        if (st.kind == KIND_EVENT) {
            SectionLabel("Linked to-dos")
            Card {
                children.forEach { c ->
                    TaskRow(c.title, if (c.completedAt != null) "Done" else "Open", c.completedAt != null, c.important, c.category,
                        onCheck = { onToggleChild(c) }, onClick = { onOpenItem(c) })
                    RowDivider()
                }
                st.newTodos.forEachIndexed { i, t ->
                    Row(Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = t.title,
                            onValueChange = { v -> st = st.copy(newTodos = st.newTodos.toMutableList().also { it[i] = t.copy(title = v) }) },
                            placeholder = { Text("e.g. Print the new insurance card") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { st = st.copy(newTodos = st.newTodos.filterIndexed { j, _ -> j != i }) }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Remove to-do")
                        }
                    }
                    if (st.date != null) {
                        ChoiceRow("Due", t.daysBefore, dueOptions, ::dueText) { d ->
                            st = st.copy(newTodos = st.newTodos.toMutableList().also { it[i] = t.copy(daysBefore = d) })
                        }
                    }
                    RowDivider()
                }
                TextButton(onClick = { st = st.copy(newTodos = st.newTodos + LinkedTodo("")) }, modifier = Modifier.padding(start = 4.dp)) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text("Add a to-do for this event")
                }
            }
        }

        SectionLabel("Checklist")
        Card {
            st.checklist.forEachIndexed { i, e ->
                Row(Modifier.padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = e.done, onCheckedChange = { on ->
                        val next = st.checklist.toMutableList().also { it[i] = e.copy(done = on) }
                        st = st.copy(checklist = next)
                        if (mode == FormMode.DETAIL) onChecklistTick(next)
                    })
                    OutlinedTextField(
                        value = e.text,
                        onValueChange = { v -> st = st.copy(checklist = st.checklist.toMutableList().also { it[i] = e.copy(text = v) }) },
                        placeholder = { Text("Step") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { st = st.copy(checklist = st.checklist.filterIndexed { j, _ -> j != i }) }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Remove step")
                    }
                }
            }
            TextButton(onClick = { st = st.copy(checklist = st.checklist + Checklist.Entry("")) }, modifier = Modifier.padding(start = 4.dp)) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text("Add a step")
            }
        }

        if (mode == FormMode.DETAIL) {
            SectionLabel("More")
            Card {
                ValueRow("Copy as new", "") { onDuplicate() }
                RowDivider()
                ValueRow("Save as template", "") { templateName = st.title }
                if (st.date != null) {
                    RowDivider()
                    ValueRow("Resend calendar invite", "") {
                        confirm = Confirm("Send the invite again?", "Send", "Open the email and tap the calendar icon to add it.", onResendInvite)
                    }
                }
                RowDivider()
                ValueRow("Delete", "") {
                    confirm = Confirm(
                        "Delete this ${st.kindName}?", "Delete",
                        "This can't be undone. Anything already in Proton Calendar stays there.", onDelete,
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        when (mode) {
            FormMode.NEW, FormMode.DRAFT -> {
                Button(
                    onClick = { confirm = Confirm("Save this ${st.kindName}?", "Save", savedText(st, isNew = true)) { onSave(st) } },
                    enabled = st.valid,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save") }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { confirm = Confirm("Save as a draft?", "Save draft", "Drafts stay on this phone with no reminders or invites. Find them in Settings.") { onDraft(st) } },
                        enabled = st.title.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) { Text("Draft") }
                    if (mode == FormMode.DRAFT) {
                        OutlinedButton(onClick = onDiscard, modifier = Modifier.weight(1f)) { Text("Delete draft") }
                    } else {
                        OutlinedButton(
                            onClick = { confirm = Confirm("Discard this ${st.kindName}?", "Discard", "What you entered will be cleared.") { onDiscard() } },
                            modifier = Modifier.weight(1f),
                        ) { Text("Discard") }
                    }
                }
            }
            FormMode.DETAIL -> {
                Button(
                    onClick = { confirm = Confirm("Save changes?", "Update", savedText(st, isNew = false)) { onSave(st) } },
                    enabled = dirty && st.valid,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Update") }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDiscard, modifier = Modifier.weight(1f)) { Text("Close") }
                    if (completed) OutlinedButton(
                        onClick = { confirm = Confirm("Reopen this ${st.kindName}?", "Reopen", null, onReopen) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Reopen") }
                    else OutlinedButton(
                        onClick = { confirm = Confirm("Mark as completed?", "Completed", null, onComplete) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Completed") }
                }
            }
        }
    }

    confirm?.let { c ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(c.title) },
            text = if (c.text != null) { { Text(c.text) } } else null,
            confirmButton = { TextButton(onClick = { confirm = null; c.action() }) { Text(c.button) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Keep editing") } },
        )
    }
    templateName?.let { name ->
        AlertDialog(
            onDismissRequest = { templateName = null },
            title = { Text("Save as template") },
            text = {
                OutlinedTextField(value = name, onValueChange = { templateName = it }, label = { Text("Template name") }, singleLine = true)
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { templateName = null; onSaveTemplate(name.trim()) }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { templateName = null }) { Text("Cancel") } },
        )
    }
    if (pickDate) DatePick(st.date, onDismiss = { pickDate = false }) { st = st.copy(date = it) }
    if (pickTime) TimePick(st.time ?: LocalTime.of(9, 0), onDismiss = { pickTime = false }) { st = st.copy(time = it) }
    if (pickAllDayTime) TimePick(Reminders.time(st.allDayMinutes), onDismiss = { pickAllDayTime = false }) {
        st = st.copy(allDayMinutes = it.hour * 60 + it.minute)
    }
    customReminderIndex?.let { i ->
        DatePick(st.date?.minusDays(1), onDismiss = { customReminderIndex = null }) { d ->
            st = st.copy(reminders = st.reminders.toMutableList().also { l -> if (i < l.size) l[i] = "C${d.toEpochDay()}" })
        }
    }
}

/** A "are you sure?" prompt for a form button, so a stray tap never loses your place. */
private class Confirm(val title: String, val button: String, val text: String?, val action: () -> Unit)

private fun savedText(st: FormState, isNew: Boolean): String? {
    val parts = buildList {
        if (isNew && st.date != null && st.addToCalendar) add("a calendar invite will be emailed")
        if (st.todosToCreate.isNotEmpty()) add("${st.todosToCreate.size} linked to-do${if (st.todosToCreate.size > 1) "s" else ""} will be added")
    }
    return if (parts.isEmpty()) null else parts.joinToString(", ").replaceFirstChar { it.uppercase() } + "."
}

private enum class Preset(val label: String) { DAILY("Daily"), WEEKLY("Weekly"), MONTHLY("Monthly"), YEARLY("Yearly"), CUSTOM("Custom") }

@Composable
private fun RepeatEditor(spec: RepeatSpec, date: LocalDate, onChange: (RepeatSpec) -> Unit) {
    val isPreset = spec.interval == 1 && !spec.nthWeekday && (spec.freq != Freq.WEEKLY || spec.byDays.isEmpty() || spec.byDays == setOf(date.dayOfWeek))
    var custom by remember { mutableStateOf(!isPreset) }
    val preset = if (custom) Preset.CUSTOM else Preset.valueOf(spec.freq.name)
    RowDivider()
    ChoiceRow("Pattern", preset, Preset.entries.toList(), { it.label }) { p ->
        if (p == Preset.CUSTOM) custom = true
        else {
            custom = false
            onChange(RepeatSpec(Freq.valueOf(p.name), byDays = if (p == Preset.WEEKLY) setOf(date.dayOfWeek) else emptySet()))
        }
    }
    if (custom) {
        RowDivider()
        ChoiceRow("Every", spec.interval, (1..30).toList(), { it.toString() }) { onChange(spec.copy(interval = it)) }
        RowDivider()
        val units = listOf(Freq.DAILY to "days", Freq.WEEKLY to "weeks", Freq.MONTHLY to "months", Freq.YEARLY to "years")
        ChoiceRow("Unit", spec.freq, units.map { it.first }, { f -> units.first { it.first == f }.second }) { f ->
            onChange(spec.copy(freq = f, byDays = if (f == Freq.WEEKLY) setOf(date.dayOfWeek) else emptySet(), nthWeekday = false))
        }
        if (spec.freq == Freq.WEEKLY) {
            FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DayOfWeek.values().forEach { d ->
                    val on = d in spec.byDays.ifEmpty { setOf(date.dayOfWeek) }
                    FilterChip(
                        selected = on,
                        onClick = {
                            val cur = spec.byDays.ifEmpty { setOf(date.dayOfWeek) }
                            val next = if (on) cur - d else cur + d
                            if (next.isNotEmpty()) onChange(spec.copy(byDays = next))
                        },
                        label = { Text(d.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                    )
                }
            }
        }
        if (spec.freq == Freq.MONTHLY) {
            RowDivider()
            val byWeekday = "On the ${RepeatSpec.ordinalWeek(date)} ${date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}"
            ChoiceRow("On", spec.nthWeekday, listOf(false, true), { if (it) byWeekday else "Day ${date.dayOfMonth}" }) {
                onChange(spec.copy(nthWeekday = it))
            }
        }
    }
    Text(
        spec.describe(date),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
fun DatePick(initial: LocalDate?, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (initial ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}

@Composable
fun TimePick(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) },
    )
}
