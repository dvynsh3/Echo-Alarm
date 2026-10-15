package com.blehmen.alarmnosnooze.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String = "",
    /** LocalDate.toEpochDay() */
    val epochDay: Long = 0,
    /** false = use the default reminder time from Settings */
    val hasTime: Boolean = false,
    val hour: Int = 9,
    val minute: Int = 0,
    val triggerAt: Long = 0,
    /** 0 scheduled, 1 active (notification showing), 2 done (greyed out) */
    val state: Int = 0,
)

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY CASE WHEN state = 2 THEN 1 ELSE 0 END, triggerAt ASC")
    fun observeAll(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders")
    suspend fun getAll(): List<Reminder>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun get(id: Int): Reminder?

    @Insert
    suspend fun insert(r: Reminder): Long

    @Update
    suspend fun update(r: Reminder)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Int)
}
