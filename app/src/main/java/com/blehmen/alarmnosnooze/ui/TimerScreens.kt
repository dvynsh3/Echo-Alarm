package com.blehmen.alarmnosnooze.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blehmen.alarmnosnooze.data.Phase
import com.blehmen.alarmnosnooze.data.StopwatchState
import com.blehmen.alarmnosnooze.data.TimerState
import com.blehmen.alarmnosnooze.engine.fmtStopwatch
import com.blehmen.alarmnosnooze.engine.fmtTimer
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

// ------------------------------------------------------------------ shared bits

@Composable
private fun CircleButton(label: String, filled: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val bg = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        Modifier.size(100.dp).clip(CircleShape).background(bg.copy(alpha = if (enabled) 1f else 0.4f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = fg.copy(alpha = if (enabled) 1f else 0.5f), fontSize = 18.sp, fontWeight = FontWeight.Bold) }
}

private fun presetLabel(sec: Int) = "%02d:%02d:%02d".format(sec / 3600, (sec % 3600) / 60, sec % 60)

@Composable
fun HmsWheels(
    h: Int, m: Int, s: Int,
    onChange: (Int, Int, Int) -> Unit,
    rowHeight: Dp = 72.dp,
    fontSize: TextUnit = 48.sp,
    cellWidth: Dp = 96.dp,
) {
    val hours = remember { (0..23).map { "%02d".format(it) } }
    val sixty = remember { (0..59).map { "%02d".format(it) } }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        WheelPicker(hours, h, true, { onChange(it, m, s) }, Modifier.width(cellWidth), rowHeight, fontSize)
        Text(":", fontSize = fontSize, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        WheelPicker(sixty, m, true, { onChange(h, it, s) }, Modifier.width(cellWidth), rowHeight, fontSize)
        Text(":", fontSize = fontSize, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        WheelPicker(sixty, s, true, { onChange(h, m, it) }, Modifier.width(cellWidth), rowHeight, fontSize)
    }
}

// ------------------------------------------------------------------ timer

@Composable
fun TimerScreen(
    state: TimerState,
    onStart: (Long, Int) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onAddMinute: () -> Unit,
    onSavePresets: (List<Int>) -> Unit,
    onEnterPip: () -> Unit,
) {
    if (state.phase == Phase.IDLE) TimerSetup(state, onStart, onSavePresets)
    else TimerRunning(state, onPause, onResume, onCancel, onAddMinute, onEnterPip)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimerSetup(state: TimerState, onStart: (Long, Int) -> Unit, onSavePresets: (List<Int>) -> Unit) {
    var h by remember(state.lastSeconds) { mutableIntStateOf(state.lastSeconds / 3600) }
    var m by remember(state.lastSeconds) { mutableIntStateOf((state.lastSeconds % 3600) / 60) }
    var s by remember(state.lastSeconds) { mutableIntStateOf(state.lastSeconds % 60) }
    var wheelKey by remember { mutableIntStateOf(0) }
    var editIdx by remember { mutableStateOf<Int?>(null) }
    val total = h * 3600 + m * 60 + s

    Column(Modifier.fillMaxSize().statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(72.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("Hours", "Minutes", "Seconds").forEach {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp, modifier = Modifier.width(110.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        key(wheelKey, state.lastSeconds) { HmsWheels(h, m, s, { a, b, c -> h = a; m = b; s = c }) }

        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            state.presets.forEachIndexed { i, sec ->
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.combinedClickable(
                        onClick = { h = sec / 3600; m = (sec % 3600) / 60; s = sec % 60; wheelKey++ },
                        onLongClick = { editIdx = i },
                    ),
                ) {
                    Text(presetLabel(sec), fontSize = 17.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp))
                }
            }
        }
        Text("Long-press a preset to edit it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp))

        Spacer(Modifier.weight(1f))
        CircleButton("Start", filled = true, enabled = total > 0) { onStart(total * 1000L, total) }
        Spacer(Modifier.height(130.dp))
    }

    editIdx?.let { i ->
        key(i) {
            val cur = state.presets[i]
            var eh by remember { mutableIntStateOf(cur / 3600) }
            var em by remember { mutableIntStateOf((cur % 3600) / 60) }
            var es by remember { mutableIntStateOf(cur % 60) }
            AlertDialog(
                onDismissRequest = { editIdx = null },
                title = { Text("Edit preset") },
                text = {
                    HmsWheels(eh, em, es, { a, b, c -> eh = a; em = b; es = c },
                        rowHeight = 44.dp, fontSize = 28.sp, cellWidth = 64.dp)
                },
                confirmButton = {
                    TextButton(onClick = {
                        val sec = eh * 3600 + em * 60 + es
                        if (sec > 0) onSavePresets(state.presets.toMutableList().also { it[i] = sec })
                        editIdx = null
                    }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { editIdx = null }) { Text("Cancel") } },
            )
        }
    }
}

@Composable
private fun TimerRunning(
    state: TimerState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onAddMinute: () -> Unit,
    onEnterPip: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.phase, state.endAt) {
        while (state.phase == Phase.RUNNING) { now = System.currentTimeMillis(); delay(100) }
    }
    val remaining = when (state.phase) {
        Phase.RUNNING -> (state.endAt - now).coerceAtLeast(0)
        Phase.PAUSED -> state.remainingMs
        else -> 0L
    }
    val progress = if (state.totalMs > 0) remaining.toFloat() / state.totalMs else 0f
    val ringing = state.phase == Phase.RINGING
    val track = MaterialTheme.colorScheme.surfaceVariant
    val accent = if (ringing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Column(Modifier.fillMaxSize().statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
            if (state.phase == Phase.RUNNING) {
                IconButton(onClick = onEnterPip) { Icon(Icons.Filled.PictureInPictureAlt, "Mini window") }
            } else Spacer(Modifier.height(48.dp))
        }
        Spacer(Modifier.height(48.dp))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(290.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 12.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                drawArc(accent, -90f, 360f * (if (ringing) 1f else progress), false, Offset(inset, inset), arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round))
            }
            if (ringing) {
                Text("Time's up", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = accent)
            } else {
                Text(fmtTimer(remaining), fontSize = if (remaining >= 3_600_000) 52.sp else 64.sp, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (state.phase == Phase.PAUSED) 0.6f else 1f))
            }
        }
        Spacer(Modifier.height(24.dp))
        Surface(
            shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.clip(CircleShape).clickable(onClick = onAddMinute),
        ) { Text("+1:00", fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) }

        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(horizontal = 36.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            if (ringing) {
                Spacer(Modifier.size(100.dp))
                CircleButton("Stop", filled = true, onClick = onCancel)
            } else {
                CircleButton("Cancel", filled = false, onClick = onCancel)
                if (state.phase == Phase.RUNNING) CircleButton("Pause", filled = true, onClick = onPause)
                else CircleButton("Resume", filled = true, onClick = onResume)
            }
        }
        Spacer(Modifier.height(130.dp))
    }
}

/** Compact countdown shown inside the picture-in-picture window. */
@Composable
fun MiniTimer(state: TimerState) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.phase, state.endAt) {
        while (state.phase == Phase.RUNNING) { now = System.currentTimeMillis(); delay(100) }
    }
    val remaining = when (state.phase) {
        Phase.RUNNING -> (state.endAt - now).coerceAtLeast(0)
        Phase.PAUSED -> state.remainingMs
        else -> 0L
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Text(
            if (state.phase == Phase.IDLE || state.phase == Phase.RINGING) "00:00" else fmtTimer(remaining),
            fontSize = 36.sp, fontWeight = FontWeight.Bold,
            color = if (state.phase == Phase.RINGING) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
        )
    }
}

// ------------------------------------------------------------------ stopwatch

@Composable
fun StopwatchScreen(state: StopwatchState, onToggle: () -> Unit, onLap: () -> Unit, onReset: () -> Unit) {
    var elapsed by remember { mutableLongStateOf(state.elapsed(System.currentTimeMillis())) }
    LaunchedEffect(state) {
        if (state.running) {
            while (true) {
                withFrameMillis { }
                elapsed = state.elapsed(System.currentTimeMillis())
            }
        } else elapsed = state.elapsed(System.currentTimeMillis())
    }
    val pager = rememberPagerState(pageCount = { 2 })

    Column(Modifier.fillMaxSize().statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(32.dp))
        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().height(340.dp)) { page ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (page == 0) {
                    Dial(elapsed)
                    Text(fmtStopwatch(elapsed), fontSize = 40.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground)
                } else {
                    Text(fmtStopwatch(elapsed), fontSize = 60.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }

        // Newest lap first. Each lap shows its split and the running total.
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp)) {
            val rows = state.laps.mapIndexed { i, total -> Triple(i + 1, total - (state.laps.getOrNull(i - 1) ?: 0L), total) }.reversed()
            itemsIndexed(rows) { _, (n, split, total) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Lap $n", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(72.dp))
                    Text("+" + fmtStopwatch(split), fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text(fmtStopwatch(total), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Row(Modifier.padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(2) { i ->
                Box(Modifier.size(9.dp).clip(CircleShape).background(
                    if (pager.currentPage == i) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 36.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            when {
                state.running -> CircleButton("Lap", filled = false, onClick = onLap)
                elapsed > 0 -> CircleButton("Reset", filled = false, onClick = onReset)
                else -> CircleButton("Lap", filled = false, enabled = false, onClick = {})
            }
            CircleButton(
                when { state.running -> "Stop"; elapsed > 0 -> "Resume"; else -> "Start" },
                filled = true, onClick = onToggle,
            )
        }
        Spacer(Modifier.height(130.dp))
    }
}

@Composable
private fun Dial(elapsed: Long) {
    val tick = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val face = MaterialTheme.colorScheme.surface
    Canvas(Modifier.size(300.dp)) {
        val r = size.minDimension / 2
        val c = center
        drawCircle(face, radius = r * 0.76f, center = c)
        for (i in 0 until 60) {
            val a = Math.toRadians(i * 6.0 - 90.0)
            val big = i % 5 == 0
            val inner = r * (if (big) 0.86f else 0.91f)
            val outer = r * 0.98f
            drawLine(
                tick.copy(alpha = if (big) 0.85f else 0.35f),
                Offset(c.x + cos(a).toFloat() * inner, c.y + sin(a).toFloat() * inner),
                Offset(c.x + cos(a).toFloat() * outer, c.y + sin(a).toFloat() * outer),
                strokeWidth = if (big) 4f else 2.5f,
                cap = StrokeCap.Round,
            )
        }
        val ha = Math.toRadians((elapsed % 60_000) / 60_000.0 * 360.0 - 90.0)
        drawLine(
            accent, c,
            Offset(c.x + cos(ha).toFloat() * r * 0.9f, c.y + sin(ha).toFloat() * r * 0.9f),
            strokeWidth = 6f, cap = StrokeCap.Round,
        )
        drawCircle(accent, radius = 9f, center = c)
    }
}
