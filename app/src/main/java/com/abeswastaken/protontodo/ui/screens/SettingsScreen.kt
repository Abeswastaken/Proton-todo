@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.abeswastaken.protontodo.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.abeswastaken.protontodo.data.Item
import com.abeswastaken.protontodo.data.KIND_EVENT
import com.abeswastaken.protontodo.data.KIND_TASK
import com.abeswastaken.protontodo.domain.Reminders
import com.abeswastaken.protontodo.settings.Challenge
import com.abeswastaken.protontodo.settings.Settings
import com.abeswastaken.protontodo.ui.AppViewModel
import com.abeswastaken.protontodo.ui.Lists
import com.abeswastaken.protontodo.ui.components.Card
import com.abeswastaken.protontodo.ui.components.ChoiceRow
import com.abeswastaken.protontodo.ui.components.EmptyRow
import com.abeswastaken.protontodo.ui.components.RowDivider
import com.abeswastaken.protontodo.ui.components.SectionLabel
import com.abeswastaken.protontodo.ui.components.SwitchRow
import com.abeswastaken.protontodo.ui.components.ValueRow
import com.abeswastaken.protontodo.ui.dateText
import com.abeswastaken.protontodo.ui.millisToDate
import com.abeswastaken.protontodo.ui.theme.ThemeMode
import com.abeswastaken.protontodo.ui.timeText
import java.time.LocalDate

private const val RELEASES_URL = "https://github.com/Abeswastaken/Proton-todo/releases/latest"

@Composable
fun SettingsScreen(vm: AppViewModel, items: List<Item>, s: Settings, onOpenDraft: (Item) -> Unit) {
    val context = LocalContext.current
    val toast = { msg: String -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
    var pickAllDayTime by remember { mutableStateOf(false) }
    var confirmImport by remember { mutableStateOf<android.net.Uri?>(null) }
    var testStatus by remember { mutableStateOf<String?>(null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.export(uri, toast)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmImport = uri
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
        val drafts = Lists.drafts(items)
        SectionLabel("Drafts (${drafts.size})")
        Card {
            if (drafts.isEmpty()) EmptyRow("No drafts. Use Draft on the New form to save one for later.")
            drafts.forEachIndexed { i, d ->
                if (i > 0) RowDivider()
                ValueRow(d.title.ifBlank { "(untitled)" }, "Edited " + dateText(millisToDate(d.updatedAt))) { onOpenDraft(d) }
            }
        }

        SectionLabel("Appearance")
        Card {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(12.dp)) {
                ThemeMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = s.theme == m,
                        onClick = { vm.updateSettings { it.copy(theme = m) } },
                        shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                    ) { Text(m.name.lowercase().replaceFirstChar { it.uppercase() }) }
                }
            }
        }

        SectionLabel("Email")
        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                var from by remember(s.fromAddress) { mutableStateOf(s.fromAddress) }
                var to by remember(s.toAddress) { mutableStateOf(s.toAddress) }
                var token by remember { mutableStateOf("") }
                var organizer by remember(s.inviteOrganizer) { mutableStateOf(s.inviteOrganizer) }
                OutlinedTextField(
                    value = from, onValueChange = { from = it }, singleLine = true,
                    label = { Text("Send from (your custom-domain address)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = to, onValueChange = { to = it }, singleLine = true,
                    label = { Text("Send to (a real address on your Proton account)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = organizer, onValueChange = { organizer = it }, singleLine = true,
                    label = { Text("Invite organizer (optional)") },
                    supportingText = { Text("Use an address that isn't on your Proton account, like a catch-all on your domain, so Proton treats invites as coming from someone else. To add an event, open its invite email and tap the calendar icon.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = token, onValueChange = { token = it }, singleLine = true,
                    label = { Text(if (s.hasToken) "SMTP token (saved; type to replace)" else "SMTP token") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                val changed = from.trim() != s.fromAddress || to.trim() != s.toAddress || organizer.trim() != s.inviteOrganizer || token.isNotBlank()
                OutlinedButton(
                    enabled = changed,
                    onClick = {
                        vm.updateSettings { it.copy(fromAddress = from.trim(), toAddress = to.trim(), inviteOrganizer = organizer.trim()) }
                        if (token.isNotBlank()) vm.setToken(token)
                        token = ""
                        toast("Email settings saved.")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save email settings") }
                Text(
                    "Proton SMTP: smtp.protonmail.ch, port 587. Make the token in Proton Mail under Settings, IMAP/SMTP. It's stored encrypted on this phone and never exported.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { testStatus = "Sending..."; vm.sendTest(false) { testStatus = it } }, modifier = Modifier.weight(1f)) { Text("Test email") }
                    OutlinedButton(onClick = { testStatus = "Sending..."; vm.sendTest(true) { testStatus = it } }, modifier = Modifier.weight(1f)) { Text("Test invite") }
                }
                testStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            }
        }

        SectionLabel("Defaults for new items")
        Card {
            ChoiceRow("New items start as", s.defaultKind, listOf(KIND_TASK, KIND_EVENT), { if (it == KIND_TASK) "To-Do" else "Event" }) { v ->
                vm.updateSettings { it.copy(defaultKind = v) }
            }
            RowDivider()
            ChoiceRow("Reminder", s.defaultReminder, listOf("") + Reminders.dayCodes + Reminders.weekCodes, { if (it.isEmpty()) "None" else Reminders.label(it) }) { v ->
                vm.updateSettings { it.copy(defaultReminder = v) }
            }
            RowDivider()
            SwitchRow("Day-of reminder", s.defaultDayOf) { v -> vm.updateSettings { it.copy(defaultDayOf = v) } }
            RowDivider()
            ValueRow("All-day items remind at", timeText(s.allDayReminderMinutes)) { pickAllDayTime = true }
            RowDivider()
            ChoiceRow("Appointments remind", s.appointmentLeadMinutes, leadOptions, ::leadText) { v ->
                vm.updateSettings { it.copy(appointmentLeadMinutes = v) }
            }
            RowDivider()
            SwitchRow("Add to calendar", s.defaultAddToCalendar) { v -> vm.updateSettings { it.copy(defaultAddToCalendar = v) } }
            RowDivider()
            SwitchRow("Email reminders", s.defaultEmail) { v -> vm.updateSettings { it.copy(defaultEmail = v) } }
            RowDivider()
            ChoiceRow("Extended reminder (repeating)", s.extendedReminderDays, extendedOptions, ::extendedText) { v ->
                vm.updateSettings { it.copy(extendedReminderDays = v) }
            }
        }

        SectionLabel("Final reminder")
        Card {
            ChoiceRow("Challenge", s.challenge, Challenge.entries.toList(), { it.label }) { v -> vm.updateSettings { it.copy(challenge = v) } }
        }

        SectionLabel("Open-task nudge")
        Card {
            SwitchRow("Email a list of open tasks", s.nudgeEnabled) { v -> vm.updateSettings { it.copy(nudgeEnabled = v) } }
            if (s.nudgeEnabled) {
                RowDivider()
                ChoiceRow("How often", s.nudgeEveryDays, listOf(1, 3, 7, 14, 30), {
                    when (it) { 1 -> "Daily"; 7 -> "Weekly"; 14 -> "Every 2 weeks"; 30 -> "Monthly"; else -> "Every $it days" }
                }) { v -> vm.updateSettings { it.copy(nudgeEveryDays = v) } }
            }
        }

        SectionLabel("Home screen")
        Card {
            ChoiceRow("Upcoming events shown", s.homeEventCount, (1..15).toList(), { it.toString() }) { v -> vm.updateSettings { it.copy(homeEventCount = v) } }
            RowDivider()
            ChoiceRow("Open tasks shown", s.homeTaskCount, (1..15).toList(), { it.toString() }) { v -> vm.updateSettings { it.copy(homeTaskCount = v) } }
        }

        SectionLabel("Backup")
        Card {
            ValueRow("Export", "Save a backup file") { exporter.launch("proton-todo-backup-${LocalDate.now()}.json") }
            RowDivider()
            ValueRow("Import", "Restore from a file") { importer.launch(arrayOf("application/json", "application/octet-stream", "*/*")) }
        }

        SectionLabel("About")
        Card {
            val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?" }
            ValueRow("Version", version) {}
            RowDivider()
            ValueRow("Get the latest version", "Open releases") {
                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(RELEASES_URL)))
            }
        }
    }

    if (pickAllDayTime) TimePick(Reminders.time(s.allDayReminderMinutes), onDismiss = { pickAllDayTime = false }) { t ->
        vm.updateSettings { it.copy(allDayReminderMinutes = t.hour * 60 + t.minute) }
    }
    confirmImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmImport = null },
            title = { Text("Replace everything?") },
            text = { Text("Importing replaces all tasks, events, drafts and settings on this phone with the ones in the backup.") },
            confirmButton = { TextButton(onClick = { confirmImport = null; vm.import(uri, toast) }) { Text("Import") } },
            dismissButton = { TextButton(onClick = { confirmImport = null }) { Text("Cancel") } },
        )
    }
}
