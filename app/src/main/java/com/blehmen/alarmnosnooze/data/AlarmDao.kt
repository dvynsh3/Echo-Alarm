package com.blehmen.alarmnosnooze.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY pinned DESC, sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<Alarm>>

    @Query("SELECT * FROM alarms")
    suspend fun getAll(): List<Alarm>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun get(id: Int): Alarm?

    @Insert
    suspend fun insert(alarm: Alarm): Long

    @Update
    suspend fun update(alarm: Alarm)

    /** One transaction, so observers never see a half-written order. */
    @Update
    suspend fun updateAll(alarms: List<Alarm>)

    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM alarms")
    suspend fun maxOrder(): Int

    @Query("UPDATE alarms SET sortOrder = :order WHERE id = :id")
    suspend fun setOrder(id: Int, order: Int)
}

@Database(entities = [Alarm::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
}
