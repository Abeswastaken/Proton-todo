package com.abeswastaken.protontodo.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.abeswastaken.protontodo.app
import com.abeswastaken.protontodo.data.Item
import com.abeswastaken.protontodo.domain.Reminders
import com.abeswastaken.protontodo.mail.MailQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

/** Each item has at most one alarm: its next reminder. When it fires, the next one is scheduled. */
object ReminderScheduler {
    const val ACTION_FIRE = "com.abeswastaken.protontodo.REMINDER"
    const val EXTRA_ID = "itemId"
    const val ACTION_SNOOZED = "com.abeswastaken.protontodo.SNOOZED"

    /** Brings a snoozed reminder back at [at]. It's separate from the item's own alarm, so that one carries on. */
    fun snooze(context: Context, id: Long, text: String, at: Long) {
        val pi = PendingIntent.getBroadcast(
            context, id.toInt(),
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_SNOOZED).putExtra(EXTRA_ID, id).putExtra(Notifications.EXTRA_TEXT, text),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val am = context.getSystemService(AlarmManager::class.java)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    private fun intent(context: Context, id: Long) =
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_ID, id)

    fun reschedule(context: Context, item: Item) {
        cancel(context, item.id)
        val zone = ZoneId.systemDefault()
        val next = Reminders.nextPending(item, LocalDateTime.now(zone), zone) ?: return
        val at = next.at.atZone(zone).toInstant().toEpochMilli()
        val pi = PendingIntent.getBroadcast(
            context, item.id.toInt(), intent(context, item.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val am = context.getSystemService(AlarmManager::class.java)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    fun cancel(context: Context, id: Long) {
        val pi = PendingIntent.getBroadcast(
            context, id.toInt(), intent(context, id),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(pi)
        pi.cancel()
    }

    suspend fun rescheduleAll(context: Context) {
        context.app.repo.all().forEach { reschedule(context, it) }
    }
}

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        receiverScope.launch {
            try {
                when (intent.action) {
                    ReminderScheduler.ACTION_FIRE -> fire(context, intent.getLongExtra(ReminderScheduler.EXTRA_ID, 0))
                    in Snooze.actions -> {
                        val id = intent.getLongExtra(ReminderScheduler.EXTRA_ID, 0)
                        val text = intent.getStringExtra(Notifications.EXTRA_TEXT).orEmpty()
                        val zone = ZoneId.systemDefault()
                        val at = Snooze.of(intent.action)!!.target(LocalDateTime.now(zone)).atZone(zone).toInstant().toEpochMilli()
                        Notifications.cancel(context, id)
                        ReminderScheduler.snooze(context, id, text, at)
                    }
                    ReminderScheduler.ACTION_SNOOZED -> {
                        val item = context.app.repo.get(intent.getLongExtra(ReminderScheduler.EXTRA_ID, 0))
                        // Skip it if the item was finished or deleted in the meantime.
                        if (item != null && !item.isDraft && item.completedAt == null && item.pendingAckAt == null) {
                            Notifications.showReminder(context, item, intent.getStringExtra(Notifications.EXTRA_TEXT).orEmpty())
                        }
                    }
                    Notifications.ACTION_REPOST -> {
                        val item = context.app.repo.get(intent.getLongExtra(ReminderScheduler.EXTRA_ID, 0))
                        if (item?.pendingAckAt != null) Notifications.showFinal(context, item)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun fire(context: Context, id: Long) {
        val repo = context.app.repo
        val item = repo.get(id) ?: return
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        val inst = Reminders.nextPending(item, now, zone)
        // Stale alarm (the item changed since it was set): just set the right one.
        if (inst == null || Duration.between(now, inst.at).toMinutes() > 1) {
            ReminderScheduler.reschedule(context, item)
            return
        }
        val at = inst.at.atZone(zone).toInstant().toEpochMilli()
        repo.markFired(id, at, inst.isFinal)
        val text = Notifications.whenText(item, inst.occurrence)
        if (inst.isFinal) Notifications.showFinal(context, item) else Notifications.showReminder(context, item, text)
        if (item.emailReminders) {
            val body = buildString {
                appendLine("${item.title} is $text.")
                if (item.description.isNotBlank()) appendLine().appendLine(item.description)
                if (inst.isFinal) appendLine().appendLine("This is the final reminder.")
            }
            MailQueue.enqueue(context, "${if (item.important) "Important reminder" else "Reminder"}: ${item.title} ($text)", body)
        }
        repo.get(id)?.let { ReminderScheduler.reschedule(context, it) }
    }
}

/** Restores alarms after a reboot, an app update or a clock change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        receiverScope.launch {
            try {
                ReminderScheduler.rescheduleAll(context)
                context.app.repo.all().filter { it.pendingAckAt != null }.forEach { Notifications.showFinal(context, it) }
            } finally {
                pending.finish()
            }
        }
    }
}
