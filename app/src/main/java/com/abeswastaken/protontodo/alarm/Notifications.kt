package com.abeswastaken.protontodo.alarm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.abeswastaken.protontodo.MainActivity
import com.abeswastaken.protontodo.R
import com.abeswastaken.protontodo.ack.AckActivity
import com.abeswastaken.protontodo.data.Item
import com.abeswastaken.protontodo.domain.Reminders
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object Notifications {
    const val CH_REMINDERS = "reminders"
    const val CH_FINAL = "final"
    const val ACTION_REPOST = "com.abeswastaken.protontodo.REPOST"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_REMINDERS, "Reminders", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(
            NotificationChannel(CH_FINAL, "Final reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Stays until you pass the challenge"
            },
        )
    }

    /** "tomorrow at 9:00 AM (Fri, Oct 16)" */
    fun whenText(item: Item, occ: LocalDate, today: LocalDate = LocalDate.now()): String {
        val days = ChronoUnit.DAYS.between(today, occ)
        val rel = when {
            days == 0L -> "today"
            days == 1L -> "tomorrow"
            days > 1 -> "in $days days"
            else -> "overdue"
        }
        val time = item.timeMinutes?.let { " at " + Reminders.time(it).format(DateTimeFormatter.ofPattern("h:mm a")) } ?: ""
        return "$rel$time (${occ.format(DateTimeFormatter.ofPattern("EEE, MMM d"))})"
    }

    private fun canNotify(context: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun showReminder(context: Context, item: Item, text: String) {
        if (!canNotify(context)) return
        val open = PendingIntent.getActivity(
            context, item.id.toInt(),
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_ITEM, item.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CH_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_check)
            .setContentTitle(item.title)
            .setContentText(text.replaceFirstChar { it.uppercase() })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(item.id.toInt(), n)
    }

    /** The last reminder: no snooze, and it comes back if swiped away until the challenge is passed. */
    fun showFinal(context: Context, item: Item) {
        if (!canNotify(context)) return
        val ack = PendingIntent.getActivity(
            context, item.id.toInt(),
            Intent(context, AckActivity::class.java).putExtra(AckActivity.EXTRA_ID, item.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val repost = PendingIntent.getBroadcast(
            context, item.id.toInt(),
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_REPOST).putExtra(ReminderScheduler.EXTRA_ID, item.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val occ = Reminders.occurrence(item, LocalDate.now()) ?: item.dateEpochDay?.let(LocalDate::ofEpochDay)
        val text = occ?.let { whenText(item, it).replaceFirstChar { c -> c.uppercase() } } ?: "Final reminder"
        val n = NotificationCompat.Builder(context, CH_FINAL)
            .setSmallIcon(R.drawable.ic_stat_check)
            .setContentTitle("Final reminder: ${item.title}")
            .setContentText("$text. Tap to acknowledge.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(ack)
            .setDeleteIntent(repost)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(item.id.toInt(), n)
    }

    fun cancel(context: Context, id: Long) {
        context.getSystemService(NotificationManager::class.java).cancel(id.toInt())
    }
}
