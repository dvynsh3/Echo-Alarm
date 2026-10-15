package com.blehmen.alarmnosnooze.ui

import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.blehmen.alarmnosnooze.data.Alarm
import com.blehmen.alarmnosnooze.data.Days
import com.blehmen.alarmnosnooze.engine.nextOccurrence
import com.blehmen.alarmnosnooze.engine.timeParts
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date

private val dateFmt = DateTimeFormatter.ofPattern("EEE, d MMM")

fun durationText(ms: Long): String {
    val mins = (ms / 60_000).toInt() + 1
    val d = mins / 1440
    val h = (mins % 1440) / 60
    val m = mins % 60
    fun u(n: Int, s: String) = if (n == 1) "$n $s" else "$n ${s}s"
    return when {
        d > 0 -> listOfNotNull(u(d, "day"), if (h > 0) u(h, "hour") else null).joinToString(" ")
        h > 0 -> listOfNotNull(u(h, "hour"), if (m > 0) u(m, "minute") else null).joinToString(" ")
        else -> u(m.coerceAtLeast(1), "minute")
    }
}

fun dayLabel(a: Alarm): String {
    if (a.repeatDays != 0) return Days.summary(a.repeatDays)
    val d = nextOccurrence(a.hour, a.minute, 0).toLocalDate()
    val prefix = if (d == LocalDate.now()) "Today" else "Tomorrow"
    return "$prefix, ${d.format(dateFmt)}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmsScreen(
    alarms: List<Alarm>,
    onToggle: (Alarm, Boolean) -> Unit,
    onOpen: (Alarm) -> Unit,
    onAdd: () -> Unit,
    onQuick: (Int, Int) -> Unit,
    onPin: (Alarm) -> Unit,
    onDelete: (Alarm) -> Unit,
    onReorder: (List<Alarm>) -> Unit,
    onSettings: () -> Unit,
    quickRequest: Boolean = false,
    onQuickConsumed: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val is24 = remember { DateFormat.is24HourFormat(ctx) }
    var list by remember(alarms) { mutableStateOf(alarms) }
    val currentList by rememberUpdatedState(list)
    var reorder by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var showQuick by remember { mutableStateOf(false) }
    LaunchedEffect(quickRequest) { if (quickRequest) { showQuick = true; onQuickConsumed() } }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(15_000) } }

    val listState = rememberLazyListState()
    var dragIdx by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    fun dragBy(dy: Float) {
        val from = dragIdx ?: return
        dragOffset += dy
        val infos = listState.layoutInfo.visibleItemsInfo
        val cur = infos.firstOrNull { it.index == from } ?: return
        val center = cur.offset + dragOffset + cur.size / 2f
        val target = infos.firstOrNull { it.index != from && center >= it.offset && center <= it.offset + it.size } ?: return
        val l = currentList
        if (l[from].pinned != l[target.index].pinned) return
        list = l.toMutableList().apply { add(target.index, removeAt(from)) }
        val newLayout = if (target.index > from) target.offset + target.size - cur.size else target.offset
        dragOffset += cur.offset - newLayout
        dragIdx = target.index
    }

    val nextMs = alarms.filter { it.enabled && it.nextTriggerAt > now }.minOfOrNull { it.nextTriggerAt }
    val headline = if (nextMs == null) "All alarms are off" else "Alarm in ${durationText(nextMs - now)}"

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically) {
            if (reorder) {
                TextButton(onClick = { reorder = false; onReorder(list) }) { Text("Done") }
            } else {
                IconButton(onClick = { showQuick = true }) { Icon(Icons.Filled.FlashOn, "Quick alarm") }
                IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, "Add alarm") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Reorder") }, onClick = { menu = false; reorder = true })
                        DropdownMenuItem(text = { Text("Settings") }, onClick = { menu = false; onSettings() })
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            Text(headline, fontSize = 28.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(list, key = { _, a -> a.id }) { index, alarm ->
                val dragging = dragIdx == index
                val itemMod = if (dragging) Modifier.zIndex(1f).graphicsLayer { translationY = dragOffset }
                else Modifier.animateItem()
                AlarmCard(
                    alarm = alarm, is24 = is24, now = now, reorder = reorder,
                    modifier = itemMod,
                    handleModifier = Modifier.pointerInput(alarm.id) {
                        detectDragGestures(
                            onDragStart = { dragIdx = currentList.indexOfFirst { it.id == alarm.id }; dragOffset = 0f },
                            onDragEnd = { dragIdx = null; dragOffset = 0f },
                            onDragCancel = { dragIdx = null; dragOffset = 0f },
                            onDrag = { change, amount -> change.consume(); dragBy(amount.y) },
                        )
                    },
                    onToggle = { onToggle(alarm, it) },
                    onClick = { onOpen(alarm) },
                    onPin = { onPin(alarm) },
                    onDelete = { onDelete(alarm) },
                )
            }
        }
    }

    if (showQuick) {
        val t = remember { java.time.LocalTime.now() }
        val st = rememberTimePickerState(t.hour, t.minute, is24)
        AlertDialog(
            onDismissRequest = { showQuick = false },
            title = { Text("Quick alarm") },
            text = { TimePicker(state = st) },
            confirmButton = { TextButton(onClick = { showQuick = false; onQuick(st.hour, st.minute) }) { Text("Set") } },
            dismissButton = { TextButton(onClick = { showQuick = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlarmCard(
    alarm: Alarm,
    is24: Boolean,
    now: Long,
    reorder: Boolean,
    modifier: Modifier,
    handleModifier: Modifier,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val (time, ampm) = timeParts(alarm.hour, alarm.minute, is24)
    val tags = buildList {
        if (alarm.quick) add("Quick")
        if (alarm.snoozeEnabled) add("Snooze ${alarm.snoozeMinutes} min")
        if (alarm.autoRepeatEnabled) add("Repeats every ${alarm.autoRepeatMinutes} min")
        if (alarm.sessionActive && alarm.nextAgainAt > now)
            add("Rings again ${DateFormat.getTimeFormat(ctx).format(Date(alarm.nextAgainAt))}")
    }
    val clickMod = if (reorder) Modifier else Modifier.combinedClickable(onClick = onClick, onLongClick = { menu = true })

    Surface(
        modifier = modifier.fillMaxWidth().then(clickMod),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(Modifier.padding(horizontal = 22.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).alpha(if (alarm.enabled) 1f else 0.5f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (alarm.pinned) {
                        Icon(Icons.Filled.PushPin, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                    }
                    if (alarm.label.isNotBlank())
                        Text(alarm.label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(time, fontSize = 48.sp, fontWeight = FontWeight.Normal)
                    if (ampm.isNotEmpty())
                        Text(ampm, fontSize = 20.sp, modifier = Modifier.padding(start = 6.dp, bottom = 9.dp))
                }
                Text(dayLabel(alarm), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (tags.isNotEmpty())
                    Text(tags.joinToString(" · "), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
            if (reorder) {
                Icon(Icons.Filled.DragHandle, "Drag to reorder", handleModifier.size(36.dp))
            } else {
                Switch(checked = alarm.enabled, onCheckedChange = onToggle)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(if (alarm.pinned) "Unpin" else "Pin to top") },
                    onClick = { menu = false; onPin() })
                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
            }
        }
    }
}
