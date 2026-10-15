package com.blehmen.alarmnosnooze

import android.app.Application
import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.blehmen.alarmnosnooze.data.AppDatabase
import com.blehmen.alarmnosnooze.data.SettingsRepo
import com.blehmen.alarmnosnooze.engine.AlarmEngine
import com.blehmen.alarmnosnooze.engine.Notifier
import com.blehmen.alarmnosnooze.engine.ReminderEngine
import com.blehmen.alarmnosnooze.engine.StopwatchEngine
import com.blehmen.alarmnosnooze.engine.TimerEngine
import com.blehmen.alarmnosnooze.engine.TimerNotifier
import com.blehmen.alarmnosnooze.data.TimerRepo
import com.blehmen.alarmnosnooze.widget.AlarmWidget
import com.blehmen.alarmnosnooze.widget.StopwatchWidget
import com.blehmen.alarmnosnooze.widget.TimerWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest

class AlarmApp : Application() {
    val db by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "alarms.db")
            .addMigrations(MIGRATION_1_2)
            .build()
    }
    val settings by lazy { SettingsRepo(this) }
    val engine by lazy { AlarmEngine(this, db.alarmDao(), settings) }
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Any change to alarms, timer, stopwatch or accent colour refreshes the matching widgets. */
    private fun observeForWidgets() {
        val accent = settings.flow.map { it.accent }.distinctUntilChanged()
        appScope.launch {
            db.alarmDao().observeAll().combine(accent) { a, _ -> a }.collectLatest { AlarmWidget().updateAll(this@AlarmApp) }
        }
        appScope.launch {
            timerRepo.timer.distinctUntilChanged().combine(accent) { t, _ -> t }.collectLatest { TimerWidget().updateAll(this@AlarmApp) }
        }
        appScope.launch {
            timerRepo.stopwatch.distinctUntilChanged().combine(accent) { t, _ -> t }.collectLatest { StopwatchWidget().updateAll(this@AlarmApp) }
        }
    }

    val timerRepo by lazy { TimerRepo(this) }
    val timerEngine by lazy { TimerEngine(this, timerRepo) }
    val stopwatchEngine by lazy { StopwatchEngine(this, timerRepo) }
    val reminders by lazy { ReminderEngine(this, db.reminderDao(), settings) }

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        TimerNotifier.createChannels(this)
        observeForWidgets()
    }
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `reminders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, `epochDay` INTEGER NOT NULL, `hasTime` INTEGER NOT NULL, " +
                "`hour` INTEGER NOT NULL, `minute` INTEGER NOT NULL, `triggerAt` INTEGER NOT NULL, `state` INTEGER NOT NULL)"
        )
    }
}

val Context.app: AlarmApp get() = applicationContext as AlarmApp
