package com.abeswastaken.protontodo.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.abeswastaken.protontodo.app
import com.abeswastaken.protontodo.data.Item
import com.abeswastaken.protontodo.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** One JSON file with every item and setting. The SMTP token is left out on purpose. */
object Backup {
    private const val VERSION = 1

    suspend fun export(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        val items = context.app.repo.all()
        val root = JSONObject()
            .put("app", "proton-todo")
            .put("version", VERSION)
            .put("exportedAt", System.currentTimeMillis())
            .put("settings", context.app.settings.current.toJson())
            .put("items", JSONArray().apply { items.forEach { put(toJson(it)) } })
        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(root.toString(2).toByteArray()) }
        items.size
    }

    suspend fun import(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        val root = JSONObject(text)
        require(root.optString("app") == "proton-todo") { "This isn't a Proton To-Do backup." }
        val arr = root.getJSONArray("items")
        val items = (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        val store = context.app.settings
        root.optJSONObject("settings")?.let { j -> store.update { Settings.fromJson(j, it.hasToken) } }
        context.app.repo.replaceAll(items)
        items.size
    }

    private fun toJson(i: Item) = JSONObject().apply {
        put("id", i.id)
        put("kind", i.kind)
        put("title", i.title)
        put("description", i.description)
        put("dateEpochDay", i.dateEpochDay ?: JSONObject.NULL)
        put("timeMinutes", i.timeMinutes ?: JSONObject.NULL)
        put("reminders", i.reminders)
        put("dayOfReminder", i.dayOfReminder)
        put("allDayReminderMinutes", i.allDayReminderMinutes)
        put("appointmentLeadMinutes", i.appointmentLeadMinutes)
        put("addToCalendar", i.addToCalendar)
        put("emailReminders", i.emailReminders)
        put("repeatRule", i.repeatRule ?: JSONObject.NULL)
        put("extendedReminderDays", i.extendedReminderDays)
        put("isDraft", i.isDraft)
        put("createdAt", i.createdAt)
        put("updatedAt", i.updatedAt)
        put("completedAt", i.completedAt ?: JSONObject.NULL)
        put("doneThroughEpochDay", i.doneThroughEpochDay ?: JSONObject.NULL)
        put("lastReminderAt", i.lastReminderAt ?: JSONObject.NULL)
        put("calendarUid", i.calendarUid ?: JSONObject.NULL)
        put("calendarSequence", i.calendarSequence)
        put("parentId", i.parentId ?: JSONObject.NULL)
        put("important", i.important)
        put("category", i.category ?: JSONObject.NULL)
        put("pendingTodos", i.pendingTodos)
        put("checklist", i.checklist)
        put("isTemplate", i.isTemplate)
    }

    private fun JSONObject.longOrNull(k: String) = if (isNull(k) || !has(k)) null else getLong(k)
    private fun JSONObject.intOrNull(k: String) = if (isNull(k) || !has(k)) null else getInt(k)
    private fun JSONObject.stringOrNull(k: String) = if (isNull(k) || !has(k)) null else getString(k)

    private fun fromJson(j: JSONObject): Item {
        val now = System.currentTimeMillis()
        return Item(
            id = j.optLong("id", 0),
            kind = j.optString("kind", "TASK"),
            title = j.optString("title"),
            description = j.optString("description"),
            dateEpochDay = j.longOrNull("dateEpochDay"),
            timeMinutes = j.intOrNull("timeMinutes"),
            reminders = j.optString("reminders"),
            dayOfReminder = j.optBoolean("dayOfReminder", true),
            allDayReminderMinutes = j.optInt("allDayReminderMinutes", 360),
            appointmentLeadMinutes = j.optInt("appointmentLeadMinutes", 120),
            addToCalendar = j.optBoolean("addToCalendar", true),
            emailReminders = j.optBoolean("emailReminders", true),
            repeatRule = j.stringOrNull("repeatRule"),
            extendedReminderDays = j.optInt("extendedReminderDays", 0),
            isDraft = j.optBoolean("isDraft", false),
            createdAt = j.optLong("createdAt", now),
            // Treat the import as an edit, so reminders that already passed don't replay.
            updatedAt = now,
            completedAt = j.longOrNull("completedAt"),
            doneThroughEpochDay = j.longOrNull("doneThroughEpochDay"),
            lastReminderAt = j.longOrNull("lastReminderAt"),
            calendarUid = j.stringOrNull("calendarUid"),
            calendarSequence = j.optInt("calendarSequence", 0),
            parentId = j.longOrNull("parentId"),
            important = j.optBoolean("important", false),
            category = j.stringOrNull("category"),
            pendingTodos = j.optString("pendingTodos"),
            checklist = j.optString("checklist"),
            isTemplate = j.optBoolean("isTemplate", false),
        )
    }
}

/** Weekly backups into a folder the user picked, keeping the newest [KEEP]. */
object AutoBackup {
    private const val NAME = "auto-backup"
    private const val PREFIX = "proton-todo-backup-"
    private const val KEEP = 8

    fun sync(context: Context, s: Settings, replace: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!s.autoBackup || s.backupFolder.isBlank()) {
            wm.cancelUniqueWork(NAME)
            return
        }
        val req = PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS).build()
        val policy = if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP
        wm.enqueueUniquePeriodicWork(NAME, policy, req)
    }

    /** Writes one backup into the chosen folder now. Returns how many items it saved. */
    suspend fun runNow(context: Context): Int = withContext(Dispatchers.IO) {
        val store = context.app.settings
        val tree = Uri.parse(store.current.backupFolder.ifBlank { error("Pick a backup folder first.") })
        val resolver = context.contentResolver
        val dir = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val name = "$PREFIX${LocalDate.now()}-${System.currentTimeMillis() % 100000}.json"
        val file = DocumentsContract.createDocument(resolver, dir, "application/json", name)
            ?: error("Couldn't create a file in the backup folder.")
        val count = Backup.export(context, file)
        prune(context, tree)
        store.update { it.copy(lastBackupAt = System.currentTimeMillis()) }
        count
    }

    private fun prune(context: Context, tree: Uri) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val cols = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val ours = mutableListOf<Pair<String, String>>()
        context.contentResolver.query(children, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                if (name.startsWith(PREFIX)) ours += c.getString(0) to name
            }
        }
        ours.sortedByDescending { it.second }.drop(KEEP).forEach { (docId, _) ->
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, DocumentsContract.buildDocumentUriUsingTree(tree, docId)) }
        }
    }
}

class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching { AutoBackup.runNow(applicationContext) }
        .fold({ Result.success() }, { if (runAttemptCount < 3) Result.retry() else Result.failure() })
}
