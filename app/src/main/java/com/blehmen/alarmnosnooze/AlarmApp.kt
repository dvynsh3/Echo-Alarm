package com.blehmen.alarmnosnooze

import android.app.Application
import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.blehmen.alarmnosnooze.data.AppDatabase
import com.blehmen.alarmnosnooze.data.SettingsRepo
import com.blehmen.alarmnosnooze.data.TimerRepo
import com.blehmen.alarmnosnooze.engine.AlarmEngine
import com.blehmen.alarmnosnooze.engine.Notifier
import com.blehmen.alarmnosnooze.engine.StopwatchEngine
import com.blehmen.alarmnosnooze.engine.TimerEngine
import com.blehmen.alarmnosnooze.engine.TimerNotifier
import com.blehmen.alarmnosnooze.widget.AlarmWidget
import com.blehmen.alarmnosnooze.widget.StopwatchWidget
import com.blehmen.alarmnosnooze.widget.TimerWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AlarmApp : Application() {
    val db by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "alarms.db")
            .addMigrations(MIGRATION_1_3, MIGRATION_2_3)
            .build()
    }
    val settings by lazy { SettingsRepo(this) }
    val engine by lazy { AlarmEngine(this, db.alarmDao(), settings) }
    val timerRepo by lazy { TimerRepo(this) }
    val timerEngine by lazy { TimerEngine(this, timerRepo) }
    val stopwatchEngine by lazy { StopwatchEngine(this, timerRepo) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        TimerNotifier.createChannels(this)
        observeForWidgets()
    }

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
}

// v2 briefly had a reminders table. v3 removes it again.
private val MIGRATION_1_3 = object : Migration(1, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {}
}
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `reminders`")
    }
}

val Context.app: AlarmApp get() = applicationContext as AlarmApp
