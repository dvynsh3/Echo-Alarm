package com.blehmen.alarmnosnooze.data

import com.blehmen.alarmnosnooze.AlarmApp
import org.json.JSONArray
import org.json.JSONObject

/** Everything except runtime state (ringing sessions, timer, stopwatch) goes into one JSON file. */
object Backup {
    private const val FORMAT = "alarmnosnooze-backup"
    private const val VERSION = 1

    data class RestoreResult(val alarms: Int)

    suspend fun export(app: AlarmApp): String {
        val s = app.settings.current()
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("version", VERSION)

        root.put("settings", JSONObject().apply {
            put("themeMode", s.themeMode)
            put("accent", s.accent)
            put("maxRepeats", s.maxRepeats)
            put("resumeAfterReboot", s.resumeAfterReboot)
            s.defaultSoundUri?.let { put("defaultSoundUri", it) }
            put("defaultVolume", s.defaultVolume)
            put("defaultRampEnabled", s.defaultRampEnabled)
            put("defaultRampSeconds", s.defaultRampSeconds)
            put("confirmDelete", s.confirmDelete)
            put("bedtimeReminder", s.bedtimeReminder)
            put("upcomingNotice", s.upcomingNotice)
        })

        root.put("timerPresets", JSONArray(app.timerRepo.timerNow().presets))

        val alarms = JSONArray()
        for (a in app.db.alarmDao().getAll().sortedWith(compareByDescending<Alarm> { it.pinned }.thenBy { it.sortOrder })) {
            alarms.put(JSONObject().apply {
                put("hour", a.hour); put("minute", a.minute); put("label", a.label)
                put("repeatDays", a.repeatDays); put("enabled", a.enabled)
                a.soundUri?.let { put("soundUri", it) }
                put("volume", a.volume); put("vibrate", a.vibrate)
                put("rampEnabled", a.rampEnabled); put("rampSeconds", a.rampSeconds)
                put("snoozeEnabled", a.snoozeEnabled); put("snoozeMinutes", a.snoozeMinutes)
                put("autoRepeatEnabled", a.autoRepeatEnabled); put("autoRepeatMinutes", a.autoRepeatMinutes)
                a.endMinutes?.let { put("endMinutes", it) }
                put("quick", a.quick); put("pinned", a.pinned); put("sortOrder", a.sortOrder)
            })
        }
        root.put("alarms", alarms)

        return root.toString(2)
    }

    /** Parses the whole file first, so a bad file changes nothing. */
    suspend fun restore(app: AlarmApp, text: String): RestoreResult {
        val root = JSONObject(text)
        require(root.optString("format") == FORMAT) { "not an Alarm - No Snooze backup" }
        require(root.optInt("version", 0) in 1..VERSION) { "backup is from a newer app version" }

        val alarms = root.optJSONArray("alarms")?.let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Alarm(
                    hour = o.optInt("hour", 7).coerceIn(0, 23),
                    minute = o.optInt("minute", 0).coerceIn(0, 59),
                    label = o.optString("label", "").take(40),
                    repeatDays = o.optInt("repeatDays", 0) and 127,
                    enabled = o.optBoolean("enabled", true),
                    soundUri = o.optString("soundUri", "").ifEmpty { null },
                    volume = o.optInt("volume", 80).coerceIn(5, 100),
                    vibrate = o.optBoolean("vibrate", true),
                    rampEnabled = o.optBoolean("rampEnabled", false),
                    rampSeconds = o.optInt("rampSeconds", 30).coerceIn(5, 120),
                    snoozeEnabled = o.optBoolean("snoozeEnabled", false),
                    snoozeMinutes = o.optInt("snoozeMinutes", 5).coerceIn(1, 720),
                    autoRepeatEnabled = o.optBoolean("autoRepeatEnabled", false),
                    autoRepeatMinutes = o.optInt("autoRepeatMinutes", 5).coerceIn(1, 720),
                    endMinutes = if (o.has("endMinutes")) o.optInt("endMinutes").coerceIn(0, 1439) else null,
                    quick = o.optBoolean("quick", false),
                    pinned = o.optBoolean("pinned", false),
                    sortOrder = o.optInt("sortOrder", i),
                ).let { a -> if (a.snoozeEnabled && a.autoRepeatEnabled) a.copy(snoozeEnabled = false) else a }
            }
        } ?: emptyList()


        val presets = root.optJSONArray("timerPresets")?.let { arr ->
            (0 until arr.length()).map { arr.getInt(it) }.filter { it in 1..86399 }
        }

        // Apply: settings first, then data.
        root.optJSONObject("settings")?.let { st ->
            val repo = app.settings
            if (st.has("themeMode")) repo.setTheme(st.getInt("themeMode").coerceIn(0, 2))
            if (st.has("accent")) repo.setAccent(st.getInt("accent"))
            if (st.has("maxRepeats")) repo.setMaxRepeats(st.getInt("maxRepeats").coerceAtLeast(1))
            if (st.has("resumeAfterReboot")) repo.setResume(st.getBoolean("resumeAfterReboot"))
            repo.setDefaultSound(st.optString("defaultSoundUri", "").ifEmpty { null })
            if (st.has("defaultVolume")) repo.setDefaultVolume(st.getInt("defaultVolume").coerceIn(5, 100))
            if (st.has("defaultRampEnabled")) repo.setDefaultRamp(st.getBoolean("defaultRampEnabled"))
            if (st.has("defaultRampSeconds")) repo.setDefaultRampSeconds(st.getInt("defaultRampSeconds").coerceIn(5, 120))
            if (st.has("confirmDelete")) repo.setConfirmDelete(st.getBoolean("confirmDelete"))
            if (st.has("bedtimeReminder")) repo.setBedtimeReminder(st.getBoolean("bedtimeReminder"))
            if (st.has("upcomingNotice")) repo.setUpcomingNotice(st.getBoolean("upcomingNotice"))
        }
        if (!presets.isNullOrEmpty()) app.timerRepo.setPresets(presets)

        app.engine.replaceAll(alarms)
        return RestoreResult(alarms.size)
    }
}
