package com.abeswastaken.protontodo.mail

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.abeswastaken.protontodo.app
import com.abeswastaken.protontodo.data.Item
import com.abeswastaken.protontodo.domain.Reminders
import com.abeswastaken.protontodo.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.mail.AuthenticationFailedException

private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

/** Emails go through WorkManager so they wait for a connection and retry after failures. */
object MailQueue {
    fun enqueue(context: Context, subject: String, body: String, ics: String? = null) {
        val data = Data.Builder()
            .putString("subject", subject)
            .putString("body", body.take(4000))
        if (ics != null) data.putString("ics", ics)
        val req = OneTimeWorkRequestBuilder<SendMailWorker>()
            .setInputData(data.build())
            .setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueue(req)
    }

    fun invite(context: Context, item: Item, s: Settings) {
        if (s.fromAddress.isBlank() || s.toAddress.isBlank()) return
        val ics = Ics.invite(item, s.fromAddress, s.toAddress)
        val date = LocalDate.ofEpochDay(item.dateEpochDay ?: return)
        val whenText = date.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")) +
            (item.timeMinutes?.let { " at " + Reminders.time(it).format(DateTimeFormatter.ofPattern("h:mm a")) } ?: "")
        val verb = if (item.calendarSequence == 0) "Invitation" else "Updated invitation"
        enqueue(context, "$verb: ${item.title} ($whenText)", "${item.title}\n$whenText\n\n${item.description}".trim(), ics)
    }
}

class SendMailWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val store = applicationContext.app.settings
        val s = store.current
        val token = store.token()
        if (s.fromAddress.isBlank() || s.toAddress.isBlank() || token == null) {
            Log.w(TAG, "Email isn't set up; dropping message")
            return@withContext Result.failure()
        }
        try {
            Mailer.send(
                from = s.fromAddress,
                to = s.toAddress,
                token = token,
                subject = inputData.getString("subject") ?: "Proton To-Do",
                body = inputData.getString("body") ?: "",
                ics = inputData.getString("ics"),
            )
            Result.success()
        } catch (e: AuthenticationFailedException) {
            Log.w(TAG, "SMTP login failed", e)
            Result.failure()
        } catch (e: Exception) {
            Log.w(TAG, "Send failed, attempt $runAttemptCount", e)
            if (runAttemptCount < 30) Result.retry() else Result.failure()
        }
    }

    private companion object { const val TAG = "SendMail" }
}

/** Optional digest of open tasks, every few days. */
object Nudge {
    private const val NAME = "open-task-nudge"

    fun sync(context: Context, s: Settings, replace: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!s.nudgeEnabled) {
            wm.cancelUniqueWork(NAME)
            return
        }
        val req = PeriodicWorkRequestBuilder<NudgeWorker>(s.nudgeEveryDays.coerceAtLeast(1).toLong(), TimeUnit.DAYS)
            .setConstraints(network)
            .build()
        val policy = if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP
        wm.enqueueUniquePeriodicWork(NAME, policy, req)
    }
}

class NudgeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val open = applicationContext.app.repo.all()
            .filter { it.isTask && !it.isDraft && it.completedAt == null }
            .sortedBy { it.createdAt }
        if (open.isEmpty()) return Result.success()
        val body = buildString {
            appendLine("You have ${open.size} open task${if (open.size == 1) "" else "s"}:")
            appendLine()
            open.forEach { appendLine("- ${it.title}") }
        }
        MailQueue.enqueue(applicationContext, "Open tasks: ${open.size}", body)
        return Result.success()
    }
}
