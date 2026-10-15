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
import com.abeswastaken.protontodo.ui.AppViewModel
import com.abeswastaken.protontodo.ui.ProtonTodoApp
import com.abeswastaken.protontodo.ui.theme.ProtonTodoTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private val openItemId = mutableStateOf<Long?>(null)
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
            ProtonTodoTheme(settings.theme) {
                ProtonTodoApp(vm, itemId, onOpenedItem = { openItemId.value = null })
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
    }

    companion object { const val EXTRA_ITEM = "itemId" }
}
