package com.abeswastaken.protontodo

import android.app.Application
import android.content.Context
import com.abeswastaken.protontodo.alarm.Notifications
import com.abeswastaken.protontodo.data.AppDatabase
import com.abeswastaken.protontodo.data.Repository
import com.abeswastaken.protontodo.backup.AutoBackup
import com.abeswastaken.protontodo.mail.Lookahead
import com.abeswastaken.protontodo.mail.Nudge
import com.abeswastaken.protontodo.widget.ProtonWidget
import com.abeswastaken.protontodo.settings.SettingsStore

class App : Application() {
    val db by lazy { AppDatabase.build(this) }
    val settings by lazy { SettingsStore(this) }
    val repo by lazy { Repository(this, db.items(), settings) }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        Nudge.sync(this, settings.current, replace = false)
        Lookahead.sync(this, settings.current, replace = false)
        AutoBackup.sync(this, settings.current, replace = false)
        ProtonWidget.keepUpdated(this)
    }
}

val Context.app: App get() = applicationContext as App
