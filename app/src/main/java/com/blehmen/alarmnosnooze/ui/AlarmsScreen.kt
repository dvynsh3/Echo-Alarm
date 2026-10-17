package com.blehmen.alarmnosnooze.ui

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
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
    onPin: (Alarm) -> Unit,
    onDuplicate: (Alarm) -> Unit,
    onDelete: (Alarm) -> Unit,
    onReorder: (List<Alarm>) -> Unit,
    onSettings: () -> Unit,
    confirmDelete: Boolean = false,
) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val is24 = remember { DateFormat.is24HourFormat(ctx) }

    // While dragging, `dragList` is the working order. Otherwise the database list is shown directly.
    var dragList by remember { mutableStateOf<List<Alarm>?>(null) }
    val list = dragList ?: alarms
    val currentList by rememberUpdatedState(list)
    val onReorderState by rememberUpdatedState(onReorder)
    var pendingDelete by remember { mutableStateOf<Alarm?>(null) }
    var editMode by remember { mutableStateOf(false) }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(15_000) } }

    BackHandler(enabled = editMode) { editMode = false }

    // ---- drag-to-reorder -------------------------------------------------------------
    val listState = rememberLazyListState()
    var draggingId by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val slackPx = with(density) { 8.dp.toPx() }
    val edgePx = with(density) { 96.dp.toPx() }

    fun endDrag() {
        if (draggingId != null) onReorderState(currentList) // saved the moment you let go
        draggingId = null
        dragOffset = 0f
    }

    // Keep the dropped order on screen until the database emits the same order, then hand back to it.
    LaunchedEffect(alarms, draggingId) {
        val d = dragList
        if (draggingId == null && d != null) {
            if (alarms.map { it.id } == d.map { it.id }) dragList = null
            else { delay(1500); if (draggingId == null) dragList = null }
        }
    }

    fun dragBy(dy: Float) {
        val id = draggingId ?: return
        dragOffset += dy
        val infos = listState.layoutInfo.visibleItemsInfo
        val cur = infos.firstOrNull { it.key == id } ?: return

        // Keep the dragged card under the finger when it nears the top or bottom of the list.
        val top = cur.offset + dragOffset
        val bottom = top + cur.size
        val viewport = listState.layoutInfo
        val scroll = when {
            bottom > viewport.viewportEndOffset - edgePx -> 28f
            top < viewport.viewportStartOffset + edgePx -> -28f
            else -> 0f
        }
        if (scroll != 0f) dragOffset += listState.dispatchRawDelta(scroll)

        // Only swap when the layout on screen already reflects the current order; otherwise a stale
        // measurement could trigger a double swap and make the card jump.
        val order = currentList.map { it.id }
        val visibleKeys = infos.mapNotNull { it.key as? Int }
        if (visibleKeys != order.filter { it in visibleKeys }) return

        val centerY = cur.offset + dragOffset + cur.size / 2f
        val target = infos.firstOrNull {
            it.key != id && it.key is Int && centerY >= it.offset - slackPx && centerY <= it.offset + it.size + slackPx
        } ?: return
        val l = currentList
        val from = l.indexOfFirst { it.id == id }
        val to = l.indexOfFirst { it.id == target.key }
        if (from < 0 || to < 0 || l[from].pinned != l[to].pinned) return // pinned alarms stay above the rest
        dragList = l.toMutableList().apply { add(to, removeAt(from)) }
        val newLayoutOffset = if (to > from) target.offset + target.size - cur.size else target.offset
        dragOffset += cur.offset - newLayoutOffset
    }

    // ---- UI ----------------------------------------------------------------------------
    val nextMs = alarms.filter { it.enabled && it.nextTriggerAt > now }.minOfOrNull { it.nextTriggerAt }
    val headline = when {
        editMode -> "Edit alarms"
        nextMs == null -> "All alarms are off"
        else -> "Alarm in ${durationText(nextMs - now)}"
    }
    // Only animate when the kind of message changes. Minute-by-minute updates swap the text in place; cross-fading
    // those is what made the banner dip to black every time it refreshed.
    val headlineKind = when { editMode -> 0; nextMs == null -> 1; else -> 2 }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Settings") }
            }
            Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = headlineKind,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                    label = "headline",
                ) { _ ->
                    Text(headline, fontSize = 28.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = draggingId == null,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 180.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "actions") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onAdd) {
                            Icon(Icons.Filled.Add, "Add alarm", tint = MaterialTheme.colorScheme.onBackground)
                        }
                        IconButton(onClick = { editMode = !editMode }) {
                            AnimatedContent(
                                targetState = editMode,
                                transitionSpec = { (fadeIn(tween(180)) + scaleIn(initialScale = 0.6f)) togetherWith fadeOut(tween(120)) },
                                label = "editIcon",
                            ) { e ->
                                Icon(
                                    if (e) Icons.Filled.Check else Icons.Outlined.Edit,
                                    if (e) "Done editing" else "Edit alarms",
                                    tint = MaterialTheme.colorScheme.onBackground,
                                )
                            }
                        }
                    }
                }
                itemsIndexed(list, key = { _, a -> a.id }) { _, alarm ->
                    val dragging = draggingId == alarm.id
                    val itemMod = if (dragging) Modifier.zIndex(1f).graphicsLayer {
                        translationY = dragOffset; scaleX = 1.02f; scaleY = 1.02f
                    } else Modifier.animateItem()
                    AlarmCard(
                        alarm = alarm, is24 = is24, now = now, editMode = editMode, dragging = dragging,
                        modifier = itemMod,
                        handleModifier = Modifier.pointerInput(alarm.id) {
                            detectDragGestures(
                                onDragStart = { dragList = currentList; draggingId = alarm.id; dragOffset = 0f },
                                onDragEnd = { endDrag() },
                                onDragCancel = { endDrag() },
                                onDrag = { change, amount -> change.consume(); dragBy(amount.y) },
                            )
                        },
                        onToggle = { onToggle(alarm, it) },
                        onClick = { if (!editMode) onOpen(alarm) },
                        onLongClick = {
                            if (!editMode) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); editMode = true }
                        },
                        onPin = { onPin(alarm) },
                        onDuplicate = { onDuplicate(alarm) },
                        onDelete = { if (confirmDelete) pendingDelete = alarm else onDelete(alarm) },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = !editMode,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 20.dp, bottom = 104.dp),
            enter = scaleIn(tween(220)) + fadeIn(tween(220)),
            exit = scaleOut(tween(160)) + fadeOut(tween(160)),
        ) {
            FloatingActionButton(
                onClick = onAdd,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Icon(Icons.Filled.Add, "Add alarm") }
        }
    }

    pendingDelete?.let { a ->
        ConfirmDeleteDialog(
            onCancel = { pendingDelete = null },
            onConfirm = { pendingDelete = null; onDelete(a) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlarmCard(
    alarm: Alarm,
    is24: Boolean,
    now: Long,
    editMode: Boolean,
    dragging: Boolean,
    modifier: Modifier,
    handleModifier: Modifier,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPin: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    val ctx = LocalContext.current
    val (time, ampm) = timeParts(alarm.hour, alarm.minute, is24)
    val tags = buildList {
        if (alarm.quick) add("Quick")
        if (alarm.snoozeEnabled) add("Snooze ${alarm.snoozeMinutes} min")
        if (alarm.autoRepeatEnabled) add("Repeats every ${alarm.autoRepeatMinutes} min")
        if (alarm.sessionActive && alarm.nextAgainAt > now)
            add("Rings again ${DateFormat.getTimeFormat(ctx).format(Date(alarm.nextAgainAt))}")
    }
    val dim by animateFloatAsState(if (alarm.enabled) 1f else 0.5f, tween(250), label = "dim")
    val timeSize by animateFloatAsState(if (editMode) 38f else 44f, tween(200), label = "timeSize")
    val elevation by animateDpAsState(if (dragging) 14.dp else 0.dp, label = "lift")
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = elevation,
    ) {
        Box {
            // Accent edge on the left while the alarm is on: full height, curving with the card corners.
            AnimatedVisibility(
                visible = alarm.enabled,
                modifier = Modifier.matchParentSize(),
                enter = fadeIn(tween(250)),
                exit = fadeOut(tween(200)),
            ) { AccentEdge(MaterialTheme.colorScheme.surface, Modifier.fillMaxSize()) }
            Row(
                Modifier.padding(start = 24.dp, end = 14.dp, top = 18.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).alpha(dim)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (alarm.pinned) {
                            Icon(Icons.Filled.PushPin, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                        }
                        if (alarm.label.isNotBlank()) Text(alarm.label, fontSize = 14.sp, color = muted)
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(time, fontSize = timeSize.sp, fontWeight = FontWeight.Normal)
                        if (ampm.isNotEmpty())
                            Text(ampm, fontSize = 18.sp, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp))
                    }
                    Text(dayLabel(alarm), fontSize = 14.sp, color = muted)
                    if (tags.isNotEmpty())
                        Text(tags.joinToString(" · "), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
                AnimatedContent(
                    targetState = editMode,
                    transitionSpec = { (fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.8f)) togetherWith fadeOut(tween(120)) },
                    label = "cardControls",
                ) { edit ->
                    if (edit) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onPin, modifier = Modifier.size(34.dp)) {
                                Icon(
                                    if (alarm.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                    if (alarm.pinned) "Unpin" else "Pin",
                                    modifier = Modifier.size(22.dp),
                                    tint = if (alarm.pinned) MaterialTheme.colorScheme.primary else muted,
                                )
                            }
                            IconButton(onClick = onDuplicate, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Outlined.ContentCopy, "Duplicate", modifier = Modifier.size(22.dp), tint = muted)
                            }
                            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Outlined.Delete, "Delete", modifier = Modifier.size(22.dp), tint = muted)
                            }
                            Box(handleModifier.size(34.dp), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.UnfoldMore, "Drag to reorder", modifier = Modifier.size(22.dp), tint = muted)
                            }
                        }
                    } else {
                        Switch(checked = alarm.enabled, onCheckedChange = onToggle)
                    }
                }
            }
        }
    }
}
