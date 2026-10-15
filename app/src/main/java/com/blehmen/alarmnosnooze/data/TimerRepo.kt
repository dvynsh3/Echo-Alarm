package com.blehmen.alarmnosnooze.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.timerStore by preferencesDataStore("timer")

object Phase {
    const val IDLE = 0
    const val RUNNING = 1
    const val PAUSED = 2
    const val RINGING = 3
}

data class TimerState(
    val phase: Int = Phase.IDLE,
    val totalMs: Long = 0,
    /** Wall-clock time the timer ends (RUNNING). */
    val endAt: Long = 0,
    /** Time left (PAUSED). */
    val remainingMs: Long = 0,
    /** Preset durations in seconds. */
    val presets: List<Int> = listOf(600, 900, 1800),
    /** Last duration started, in seconds (prefills the wheels). */
    val lastSeconds: Int = 300,
)

data class StopwatchState(
    val running: Boolean = false,
    val startAt: Long = 0,
    val accumMs: Long = 0,
    /** Cumulative elapsed time at each lap press. */
    val laps: List<Long> = emptyList(),
) {
    fun elapsed(now: Long): Long = accumMs + if (running) (now - startAt).coerceAtLeast(0) else 0L
}

class TimerRepo(private val ctx: Context) {
    private object K {
        val phase = intPreferencesKey("t_phase")
        val total = longPreferencesKey("t_total")
        val end = longPreferencesKey("t_end")
        val rem = longPreferencesKey("t_rem")
        val presets = stringPreferencesKey("t_presets")
        val last = intPreferencesKey("t_last")
        val swRun = booleanPreferencesKey("sw_run")
        val swStart = longPreferencesKey("sw_start")
        val swAccum = longPreferencesKey("sw_accum")
        val swLaps = stringPreferencesKey("sw_laps")
    }

    val timer: Flow<TimerState> = ctx.timerStore.data.map { p ->
        val presets = p[K.presets]?.split(",")?.mapNotNull { it.toIntOrNull() }?.takeIf { it.isNotEmpty() }
            ?: listOf(600, 900, 1800)
        TimerState(
            phase = p[K.phase] ?: Phase.IDLE,
            totalMs = p[K.total] ?: 0,
            endAt = p[K.end] ?: 0,
            remainingMs = p[K.rem] ?: 0,
            presets = presets,
            lastSeconds = p[K.last] ?: 300,
        )
    }

    val stopwatch: Flow<StopwatchState> = ctx.timerStore.data.map { p ->
        StopwatchState(
            running = p[K.swRun] ?: false,
            startAt = p[K.swStart] ?: 0,
            accumMs = p[K.swAccum] ?: 0,
            laps = p[K.swLaps]?.split(",")?.mapNotNull { it.toLongOrNull() } ?: emptyList(),
        )
    }

    suspend fun timerNow() = timer.first()
    suspend fun stopwatchNow() = stopwatch.first()

    suspend fun setTimer(phase: Int, total: Long, end: Long, remaining: Long) {
        ctx.timerStore.edit { it[K.phase] = phase; it[K.total] = total; it[K.end] = end; it[K.rem] = remaining }
    }
    suspend fun clearTimer() = setTimer(Phase.IDLE, 0, 0, 0)
    suspend fun setPresets(list: List<Int>) { ctx.timerStore.edit { it[K.presets] = list.joinToString(",") } }
    suspend fun setLast(seconds: Int) { ctx.timerStore.edit { it[K.last] = seconds } }

    suspend fun setStopwatch(s: StopwatchState) {
        ctx.timerStore.edit {
            it[K.swRun] = s.running; it[K.swStart] = s.startAt; it[K.swAccum] = s.accumMs
            it[K.swLaps] = s.laps.joinToString(",")
        }
    }
}
