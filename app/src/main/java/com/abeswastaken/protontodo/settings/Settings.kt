package com.abeswastaken.protontodo.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.abeswastaken.protontodo.data.KIND_TASK
import com.abeswastaken.protontodo.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class Challenge(val label: String) { PHRASE("Type a phrase"), MATH("Solve a sum"), MIXED("Either, at random") }

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val fromAddress: String = "",
    val toAddress: String = "",
    /** Organizer on calendar invites. Blank means the from address. */
    val inviteOrganizer: String = "",
    val hasToken: Boolean = false,
    val defaultKind: String = KIND_TASK,
    val defaultReminder: String = "D2",
    val defaultDayOf: Boolean = true,
    val allDayReminderMinutes: Int = 360,
    val appointmentLeadMinutes: Int = 120,
    val defaultAddToCalendar: Boolean = true,
    val defaultEmail: Boolean = true,
    val extendedReminderDays: Int = 14,
    val challenge: Challenge = Challenge.MIXED,
    val nudgeEnabled: Boolean = false,
    val nudgeEveryDays: Int = 7,
    val homeEventCount: Int = 5,
    val homeTaskCount: Int = 5,
    /** Sunday evening email of the week ahead. */
    val lookaheadEnabled: Boolean = false,
    val autoBackup: Boolean = false,
    /** A folder the user picked (a document tree URI) for automatic backups. */
    val backupFolder: String = "",
    val lastBackupAt: Long = 0,
) {
    val organizer get() = inviteOrganizer.ifBlank { fromAddress }

    val emailReady get() = fromAddress.isNotBlank() && toAddress.isNotBlank() && hasToken

    /** Everything except the token, which never leaves the phone. */
    fun toJson(): JSONObject = JSONObject().apply {
        put("theme", theme.name)
        put("fromAddress", fromAddress)
        put("toAddress", toAddress)
        put("inviteOrganizer", inviteOrganizer)
        put("defaultKind", defaultKind)
        put("defaultReminder", defaultReminder)
        put("defaultDayOf", defaultDayOf)
        put("allDayReminderMinutes", allDayReminderMinutes)
        put("appointmentLeadMinutes", appointmentLeadMinutes)
        put("defaultAddToCalendar", defaultAddToCalendar)
        put("defaultEmail", defaultEmail)
        put("extendedReminderDays", extendedReminderDays)
        put("challenge", challenge.name)
        put("nudgeEnabled", nudgeEnabled)
        put("nudgeEveryDays", nudgeEveryDays)
        put("homeEventCount", homeEventCount)
        put("homeTaskCount", homeTaskCount)
        put("lookaheadEnabled", lookaheadEnabled)
        put("autoBackup", autoBackup)
        put("backupFolder", backupFolder)
        put("lastBackupAt", lastBackupAt)
    }

    companion object {
        fun fromJson(j: JSONObject, hasToken: Boolean): Settings {
            val d = Settings()
            return Settings(
                theme = runCatching { ThemeMode.valueOf(j.optString("theme")) }.getOrDefault(d.theme),
                fromAddress = j.optString("fromAddress", d.fromAddress),
                toAddress = j.optString("toAddress", d.toAddress),
                inviteOrganizer = j.optString("inviteOrganizer", d.inviteOrganizer),
                hasToken = hasToken,
                defaultKind = j.optString("defaultKind", d.defaultKind),
                defaultReminder = j.optString("defaultReminder", d.defaultReminder),
                defaultDayOf = j.optBoolean("defaultDayOf", d.defaultDayOf),
                allDayReminderMinutes = j.optInt("allDayReminderMinutes", d.allDayReminderMinutes),
                appointmentLeadMinutes = j.optInt("appointmentLeadMinutes", d.appointmentLeadMinutes),
                defaultAddToCalendar = j.optBoolean("defaultAddToCalendar", d.defaultAddToCalendar),
                defaultEmail = j.optBoolean("defaultEmail", d.defaultEmail),
                extendedReminderDays = j.optInt("extendedReminderDays", d.extendedReminderDays),
                challenge = runCatching { Challenge.valueOf(j.optString("challenge")) }.getOrDefault(d.challenge),
                nudgeEnabled = j.optBoolean("nudgeEnabled", d.nudgeEnabled),
                nudgeEveryDays = j.optInt("nudgeEveryDays", d.nudgeEveryDays),
                homeEventCount = j.optInt("homeEventCount", d.homeEventCount),
                homeTaskCount = j.optInt("homeTaskCount", d.homeTaskCount),
                lookaheadEnabled = j.optBoolean("lookaheadEnabled", d.lookaheadEnabled),
                autoBackup = j.optBoolean("autoBackup", d.autoBackup),
                backupFolder = j.optString("backupFolder", d.backupFolder),
                lastBackupAt = j.optLong("lastBackupAt", d.lastBackupAt),
            )
        }
    }
}

/** Settings live in SharedPreferences as one JSON blob; the SMTP token is stored encrypted. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    val flow: StateFlow<Settings> = state
    val current: Settings get() = state.value

    private fun load(): Settings {
        val hasToken = prefs.contains(TOKEN_KEY)
        val raw = prefs.getString(SETTINGS_KEY, null) ?: return Settings(hasToken = hasToken)
        return runCatching { Settings.fromJson(JSONObject(raw), hasToken) }.getOrDefault(Settings(hasToken = hasToken))
    }

    fun update(transform: (Settings) -> Settings) {
        val next = transform(current).copy(hasToken = current.hasToken)
        prefs.edit().putString(SETTINGS_KEY, next.toJson().toString()).apply()
        state.value = next
    }

    fun token(): String? = prefs.getString(TOKEN_KEY, null)?.let(TokenCipher::decrypt)

    fun setToken(token: String?) {
        val edit = prefs.edit()
        if (token.isNullOrBlank()) edit.remove(TOKEN_KEY) else edit.putString(TOKEN_KEY, TokenCipher.encrypt(token.trim()))
        edit.apply()
        state.value = current.copy(hasToken = !token.isNullOrBlank())
    }

    private companion object {
        const val SETTINGS_KEY = "settings_json"
        const val TOKEN_KEY = "smtp_token_enc"
    }
}

/** AES-GCM with a key held in the Android Keystore, so the token can't be read off the device. */
object TokenCipher {
    private const val ALIAS = "smtp_token_key"
    private const val STORE = "AndroidKeyStore"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(STORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val ct = c.doFinal(plain.toByteArray())
        return Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP)
    }

    fun decrypt(stored: String): String? = runCatching {
        val (iv, ct) = stored.split(":")
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        String(c.doFinal(Base64.decode(ct, Base64.NO_WRAP)))
    }.getOrNull()
}
