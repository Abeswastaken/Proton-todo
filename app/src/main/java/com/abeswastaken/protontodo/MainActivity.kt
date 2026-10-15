package com.abeswastaken.protontodo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.abeswastaken.protontodo.data.KIND_EVENT
import com.abeswastaken.protontodo.data.KIND_TASK
import com.abeswastaken.protontodo.ui.AppViewModel
import com.abeswastaken.protontodo.ui.NewRequest
import com.abeswastaken.protontodo.ui.ProtonTodoApp
import com.abeswastaken.protontodo.ui.theme.ProtonTodoTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private val openItemId = mutableStateOf<Long?>(null)
    private val newRequest = mutableStateOf<NewRequest?>(null)
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readItem(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val settings by vm.settings.collectAsState()
            val itemId by openItemId
            val request by newRequest
            ProtonTodoTheme(settings.theme) {
                ProtonTodoApp(
                    vm, itemId,
                    onOpenedItem = { openItemId.value = null },
                    newRequest = request,
                    onHandledNew = { newRequest.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readItem(intent)
    }

    private fun readItem(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_ITEM, 0L) ?: 0L
        if (id != 0L) openItemId.value = id
        when (intent?.action) {
            ACTION_NEW_TASK -> newRequest.value = NewRequest(KIND_TASK)
            ACTION_NEW_EVENT -> newRequest.value = NewRequest(KIND_EVENT)
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
                val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()
                if (text.isNotEmpty() || subject.isNotEmpty()) newRequest.value = shared(subject, text)
            }
        }
        // Handled once; don't replay it when the activity is recreated.
        intent?.action = null
    }

    /** Shared text becomes a to-do: the subject (or first line) is the title, the rest the description. */
    private fun shared(subject: String, text: String): NewRequest {
        if (subject.isNotEmpty()) return NewRequest(KIND_TASK, subject.take(120), text)
        val first = text.lineSequence().first().trim()
        return if (first.length <= 120) NewRequest(KIND_TASK, first, text.substringAfter('\n', "").trim())
        else NewRequest(KIND_TASK, first.take(117).trimEnd() + "...", text)
    }

    companion object {
        const val EXTRA_ITEM = "itemId"
        const val ACTION_NEW_TASK = "com.abeswastaken.protontodo.NEW_TASK"
        const val ACTION_NEW_EVENT = "com.abeswastaken.protontodo.NEW_EVENT"
    }
}
