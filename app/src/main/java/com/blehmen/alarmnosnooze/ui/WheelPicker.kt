package com.blehmen.alarmnosnooze.ui

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.filter

/** Three-row snapping wheel. The middle row is the selection. */
@Composable
fun WheelPicker(
    labels: List<String>,
    selectedIndex: Int,
    infinite: Boolean,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    rowHeight: Dp = 72.dp,
    fontSize: TextUnit = 52.sp,
) {
    val n = labels.size
    val rowPx = with(LocalDensity.current) { rowHeight.toPx() }
    val count = if (infinite) n * 1000 else n + 2
    val initial = if (infinite) n * 500 + selectedIndex - 1 else selectedIndex
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initial)
    val center by remember {
        derivedStateOf { state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > rowPx / 2) 2 else 1 }
    }
    val latest by rememberUpdatedState(onSelected)

    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.filter { !it }.collect {
            val c = center
            latest(if (infinite) c % n else (c - 1).coerceIn(0, n - 1))
        }
    }

    LazyColumn(
        state = state,
        flingBehavior = rememberSnapFlingBehavior(state),
        modifier = modifier.height(rowHeight * 3),
    ) {
        items(count) { i ->
            val label = if (infinite) labels[i % n] else if (i in 1..n) labels[i - 1] else ""
            Box(Modifier.height(rowHeight).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    label,
                    fontSize = fontSize,
                    fontWeight = if (i == center) FontWeight.Bold else FontWeight.Medium,
                    color = if (i == center) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
                )
            }
        }
    }
}

@Composable
fun TimeWheels(hour: Int, minute: Int, is24: Boolean, onChange: (Int, Int) -> Unit) {
    val mins = remember { (0..59).map { "%02d".format(it) } }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (is24) {
            val hours = remember { (0..23).map { "%02d".format(it) } }
            WheelPicker(hours, hour, true, { onChange(it, minute) }, Modifier.width(110.dp))
        } else {
            val hours = remember { (1..12).map { it.toString() } }
            val h12 = if (hour % 12 == 0) 12 else hour % 12
            val pm = hour >= 12
            WheelPicker(hours, h12 - 1, true, { idx -> onChange(((idx + 1) % 12) + if (pm) 12 else 0, minute) }, Modifier.width(110.dp))
        }
        Text(":", fontSize = 48.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp),
            color = MaterialTheme.colorScheme.onBackground)
        WheelPicker(mins, minute, true, { onChange(hour, it) }, Modifier.width(110.dp))
        if (!is24) {
            Spacer(Modifier.width(16.dp))
            val pm = hour >= 12
            WheelPicker(
                listOf("am", "pm"), if (pm) 1 else 0, false,
                { idx -> onChange((hour % 12) + if (idx == 1) 12 else 0, minute) },
                Modifier.width(80.dp), fontSize = 30.sp,
            )
        }
    }
}
