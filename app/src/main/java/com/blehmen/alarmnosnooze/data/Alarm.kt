package com.blehmen.alarmnosnooze.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hour: Int = 7,
    val minute: Int = 0,
    val label: String = "",
    /** Bitmask: bit0 = Monday ... bit6 = Sunday. 0 = rings once. */
    val repeatDays: Int = 0,
    val enabled: Boolean = true,
    val soundUri: String? = null,
    val volume: Int = 80,
    val vibrate: Boolean = true,
    val rampEnabled: Boolean = false,
    val rampSeconds: Int = 30,
    val snoozeEnabled: Boolean = false,
    val snoozeMinutes: Int = 5,
    val autoRepeatEnabled: Boolean = false,
    val autoRepeatMinutes: Int = 5,
    /** Minutes since midnight. Null = use app-wide max repeats. */
    val endMinutes: Int? = null,
    val quick: Boolean = false,
    val pinned: Boolean = false,
    val sortOrder: Int = 0,
    // ---- runtime state ----
    val nextTriggerAt: Long = 0,
    val sessionActive: Boolean = false,
    val sessionStart: Long = 0,
    val repeatCount: Int = 0,
    val nextAgainAt: Long = 0,
    /** The ring at exactly this time was skipped. Occurrences up to and including it are ignored. */
    val skipUntil: Long = 0,
)

object Days {
    const val ALL = 127
    const val WEEKDAYS = 31
    val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    fun summary(mask: Int): String = when (mask) {
        ALL -> "Every day"
        WEEKDAYS -> "Mon–Fri"
        else -> names.filterIndexed { i, _ -> (mask and (1 shl i)) != 0 }.joinToString(", ")
    }
}
