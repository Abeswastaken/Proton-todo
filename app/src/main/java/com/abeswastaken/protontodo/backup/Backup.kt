package com.abeswastaken.protontodo.backup

import android.content.Context
import android.net.Uri
import com.abeswastaken.protontodo.app
import com.abeswastaken.protontodo.data.Item
import com.abeswastaken.protontodo.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

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
        )
    }
}
