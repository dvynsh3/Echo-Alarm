package com.blehmen.alarmnosnooze.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

const val DEFAULT_ACCENT = 0xFF8C6BFF.toInt()

data class AppSettings(
    /** 0 = system, 1 = light, 2 = dark */
    val themeMode: Int = 0,
    val accent: Int = DEFAULT_ACCENT,
    val maxRepeats: Int = 60,
    val resumeAfterReboot: Boolean = false,
    /** Minutes since midnight. */
    val reminderDefaultMinutes: Int = 9 * 60,
    /** Defaults applied to newly created alarms. Null sound = system default alarm tone. */
    val defaultSoundUri: String? = null,
    val defaultVolume: Int = 80,
    val defaultRampEnabled: Boolean = false,
    val defaultRampSeconds: Int = 30,
)

class SettingsRepo(private val ctx: Context) {
    private object K {
        val theme = intPreferencesKey("theme")
        val accent = intPreferencesKey("accent")
        val maxRepeats = intPreferencesKey("max_repeats")
        val resume = booleanPreferencesKey("resume_after_reboot")
        val reminderTime = intPreferencesKey("reminder_default_minutes")
        val defSound = stringPreferencesKey("def_sound")
        val defVolume = intPreferencesKey("def_volume")
        val defRamp = booleanPreferencesKey("def_ramp")
        val defRampSec = intPreferencesKey("def_ramp_seconds")
    }

    val flow: Flow<AppSettings> = ctx.dataStore.data.map { p ->
        AppSettings(
            themeMode = p[K.theme] ?: 0,
            accent = p[K.accent] ?: DEFAULT_ACCENT,
            maxRepeats = p[K.maxRepeats] ?: 60,
            resumeAfterReboot = p[K.resume] ?: false,
            reminderDefaultMinutes = p[K.reminderTime] ?: (9 * 60),
            defaultSoundUri = p[K.defSound],
            defaultVolume = p[K.defVolume] ?: 80,
            defaultRampEnabled = p[K.defRamp] ?: false,
            defaultRampSeconds = p[K.defRampSec] ?: 30,
        )
    }

    suspend fun current(): AppSettings = flow.first()
    suspend fun setTheme(v: Int) { ctx.dataStore.edit { it[K.theme] = v } }
    suspend fun setAccent(v: Int) { ctx.dataStore.edit { it[K.accent] = v } }
    suspend fun setMaxRepeats(v: Int) { ctx.dataStore.edit { it[K.maxRepeats] = v } }
    suspend fun setResume(v: Boolean) { ctx.dataStore.edit { it[K.resume] = v } }
    suspend fun setReminderTime(v: Int) { ctx.dataStore.edit { it[K.reminderTime] = v } }
    suspend fun setDefaultSound(v: String?) {
        ctx.dataStore.edit { if (v == null) it.remove(K.defSound) else it[K.defSound] = v }
    }
    suspend fun setDefaultVolume(v: Int) { ctx.dataStore.edit { it[K.defVolume] = v } }
    suspend fun setDefaultRamp(v: Boolean) { ctx.dataStore.edit { it[K.defRamp] = v } }
    suspend fun setDefaultRampSeconds(v: Int) { ctx.dataStore.edit { it[K.defRampSec] = v } }
}
