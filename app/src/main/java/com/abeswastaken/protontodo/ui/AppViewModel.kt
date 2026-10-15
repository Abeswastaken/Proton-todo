package com.abeswastaken.protontodo.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.abeswastaken.protontodo.App
import com.abeswastaken.protontodo.backup.Backup
import com.abeswastaken.protontodo.data.Item
import com.abeswastaken.protontodo.mail.Ics
import com.abeswastaken.protontodo.mail.Mailer
import com.abeswastaken.protontodo.mail.Nudge
import com.abeswastaken.protontodo.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as App
    private val repo = app.repo

    /** Null until the database has loaded once. */
    val items: StateFlow<List<Item>?> = repo.items.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val settings: StateFlow<Settings> = app.settings.flow

    fun save(item: Item, done: (Long) -> Unit = {}) = viewModelScope.launch { done(repo.save(item)) }
    fun saveDraft(item: Item, done: () -> Unit = {}) = viewModelScope.launch { repo.saveDraft(item); done() }
    fun delete(item: Item, done: () -> Unit = {}) = viewModelScope.launch { repo.delete(item); done() }
    fun complete(item: Item, done: (LocalDate?) -> Unit = {}) = viewModelScope.launch { done(repo.complete(item)) }
    fun reopen(item: Item) = viewModelScope.launch { repo.reopen(item) }

    fun updateSettings(transform: (Settings) -> Settings) {
        val before = app.settings.current
        app.settings.update(transform)
        val after = app.settings.current
        if (before.nudgeEnabled != after.nudgeEnabled || before.nudgeEveryDays != after.nudgeEveryDays) {
            Nudge.sync(app, after, replace = true)
        }
    }

    fun setToken(token: String) = app.settings.setToken(token)

    /** Sends straight away (not queued) so Settings can show whether email works. */
    fun sendTest(invite: Boolean, done: (String) -> Unit) = viewModelScope.launch {
        val s = app.settings.current
        val token = app.settings.token()
        val result = when {
            s.fromAddress.isBlank() || s.toAddress.isBlank() -> "Add both email addresses first."
            token == null -> "Add your SMTP token first."
            else -> withContext(Dispatchers.IO) {
                runCatching {
                    if (invite) {
                        val sample = Item(
                            title = "Proton To-Do test event",
                            description = "Sent from Settings to check that invites reach Proton Calendar. Safe to delete.",
                            dateEpochDay = LocalDate.now().plusDays(1).toEpochDay(),
                            calendarUid = "${UUID.randomUUID()}@protontodo",
                        )
                        Mailer.send(s.fromAddress, s.toAddress, token, "Invitation: ${sample.title}", sample.description, Ics.invite(sample, s.fromAddress, s.toAddress))
                        "Test invite sent. It should appear in Proton Calendar tomorrow."
                    } else {
                        Mailer.send(s.fromAddress, s.toAddress, token, "Proton To-Do test", "Email from Proton To-Do is working.")
                        "Test email sent to ${s.toAddress}."
                    }
                }.getOrElse { "Couldn't send: ${it.message ?: it.javaClass.simpleName}" }
            }
        }
        done(result)
    }

    fun export(uri: Uri, done: (String) -> Unit) = viewModelScope.launch {
        done(runCatching { "Exported ${Backup.export(app, uri)} items." }.getOrElse { "Export failed: ${it.message}" })
    }

    fun import(uri: Uri, done: (String) -> Unit) = viewModelScope.launch {
        done(runCatching { "Imported ${Backup.import(app, uri)} items. Re-enter your SMTP token if needed." }.getOrElse { "Import failed: ${it.message}" })
    }
}
