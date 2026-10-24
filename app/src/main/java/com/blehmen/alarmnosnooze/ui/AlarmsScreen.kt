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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.blehmen.alarmnosnooze.data.Alarm
import com.blehmen.alarmnosnooze.data.Days
import com.blehmen.alarmnosnooze.engine.nextOccurrence
import com.blehmen.alarmnosnooze.engine.timeParts
import com.blehmen.alarmnosnooze.service.AlarmService
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import java.time.format.DateTimeFormatter
import java.util.Date

private val dateFmt = DateTimeFormatter.ofPattern("EEE, d MMM")

private fun dayShort(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())

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
    onSkipUpcoming: (Alarm) -> Unit = {},
    skipEvents: Flow<SkipEvent> = emptyFlow(),
    onUndoSkip: (Alarm) -> Unit = {},
    onStopRinging: (Alarm) -> Unit = {},
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
            bottom + viewport.beforeContentPadding > viewport.viewportSize.height - edgePx -> 28f
            top + viewport.beforeContentPadding < edgePx -> -28f
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
    // The alarm that is ringing right now, if any. It takes over the banner.
    val ringingAlarm by AlarmService.state.collectAsState()
    val ringing = ringingAlarm

    // The next ring across all alarms. A pending repeat or delayed ring counts, not only the regular schedule.
    val upcoming = alarms.mapNotNull { a ->
        if (!a.enabled) null else {
            val t = if (a.sessionActive && a.nextAgainAt > now) a.nextAgainAt else a.nextTriggerAt
            if (t > now) a to t else null
        }
    }.minByOrNull { it.second }
    val nextAlarm = upcoming?.first
    val nextMs = upcoming?.second
    val headline = when {
        ringing != null -> "Ringing now"
        editMode -> "Edit alarms"
        nextMs == null -> "All alarms are off"
        else -> "Alarm in ${durationText(nextMs - now)}"
    }
    // Only animate when the kind of message changes. Minute-by-minute updates swap the text in place; cross-fading
    // those is what made the banner dip to black every time it refreshed.
    val headlineKind = when { ringing != null -> 3; editMode -> 0; nextMs == null -> 1; else -> 2 }
    val bannerButton = ringing != null || (!editMode && nextAlarm != null)
    val buttonLabel = if (ringing != null) "Turn off ringing alarm" else "Turn off upcoming alarm instance"
    val subtitle = if (ringing != null) {
        val (tm, ap) = timeParts(ringing.hour, ringing.minute, is24)
        "${ringing.label.ifBlank { "Alarm" }} · ${if (ap.isEmpty()) tm else "$tm $ap"}"
    } else upcoming?.let { (a, t) ->
        val z = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault())
        val (tm, ap) = timeParts(z.hour, z.minute, is24)
        val day = when (z.toLocalDate()) {
            LocalDate.now() -> "Today"
            LocalDate.now().plusDays(1) -> "Tomorrow"
            else -> dayShort(t)
        }
        "${a.label.ifBlank { "Alarm" }} · $day ${if (ap.isEmpty()) tm else "$tm $ap"}"
    }

    // Banner takes the top half of the screen and stays put. The list slides up over it while it zooms out.
    val headerHeight = (LocalConfiguration.current.screenHeightDp * 0.5f).dp
    val headerPx = with(density) { headerHeight.toPx() }
    // 0 while the first card sits half way down, 1 once it has reached the top of the screen. Driven by that card's
    // position (not a scroll offset) so it keeps going smoothly the whole way.
    val zoom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            if (info.totalItemsCount <= 1) 0f
            else {
                val card = info.visibleItemsInfo.firstOrNull { it.index == 1 }
                // item offsets do not include the list's top padding
                if (card == null) 1f else ((headerPx - (card.offset + info.beforeContentPadding)) / (headerPx * 0.45f)).coerceIn(0f, 1f)
            }
        }
    }
    // The button lives in a layer in front of the list so it can receive taps. Once the cards get near it, it is gone.
    val buttonLive by remember { derivedStateOf { zoom < 0.3f } }
    // Where the + / Edit row sits on screen: just above the first card, rising with the list.
    val statusTopPx = WindowInsets.statusBars.getTop(density)
    val rowPx = with(density) { 48.dp.toPx() }
    val actionsTop by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val slot = info.visibleItemsInfo.firstOrNull { it.key == "actions" }
            if (slot == null) -rowPx else (slot.offset + info.beforeContentPadding).toFloat()
        }
    }
    val barColor = MaterialTheme.colorScheme.background
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(skipEvents) {
        skipEvents.collectLatest { e ->
            val r = snackbarHost.showSnackbar(e.message, actionLabel = "Undo", withDismissAction = true, duration = SnackbarDuration.Long)
            if (r == SnackbarResult.ActionPerformed) onUndoSkip(e.snapshot)
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Layer behind the list: the banner text. The cards slide up over it while it shrinks to nothing.
        Box(
            Modifier.fillMaxWidth().height(headerHeight).statusBarsPadding().padding(top = 24.dp)
                .graphicsLayer {
                    val scale = (1f - zoom).coerceAtLeast(0.001f)
                    scaleX = scale; scaleY = scale; alpha = 1f - zoom
                },
            contentAlignment = Alignment.Center,
        ) {
            BannerColumn(
                headline, headlineKind, subtitle, showSubtitle = (ringing != null || !editMode) && subtitle != null,
                showButton = bannerButton, textAlpha = { 1f }, buttonAlpha = { 0f }, onSkip = {}, buttonLabel = buttonLabel,
            )
        }
        Column(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = draggingId == null,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = headerHeight - 56.dp, bottom = 180.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Empty slot that keeps the spacing; the real + / Edit buttons are drawn in front (see below).
                item(key = "actions") { Spacer(Modifier.height(48.dp)) }
                itemsIndexed(list, key = { _, a -> a.id }) { _, alarm ->
                    val dragging = draggingId == alarm.id
                    val itemMod = if (dragging) Modifier.zIndex(1f).graphicsLayer {
                        translationY = dragOffset; scaleX = 1.02f; scaleY = 1.02f
                    } else Modifier.animateItem()
                    AlarmCard(
                        alarm = alarm, is24 = is24, now = now, editMode = editMode, dragging = dragging,
                        modifier = itemMod,
                        handleModifier = Modifier.pointerInput(alarm.id) {
                            // press and hold anywhere on the card, then drag (a plain swipe still scrolls the list)
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    dragList = currentList; draggingId = alarm.id; dragOffset = 0f
                                },
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
                        ringing = ringing?.id == alarm.id,
                        onDelete = { if (confirmDelete) pendingDelete = alarm else onDelete(alarm) },
                    )
                }
            }
        }

        // Layer in front of the list: identical layout, but only the button is visible. The list would swallow taps
        // on anything behind it.
        if (bannerButton && buttonLive) {
            Box(
                Modifier.fillMaxWidth().height(headerHeight).statusBarsPadding().padding(top = 24.dp)
                    .graphicsLayer {
                        val scale = (1f - zoom).coerceAtLeast(0.001f)
                        scaleX = scale; scaleY = scale
                    },
                contentAlignment = Alignment.Center,
            ) {
                BannerColumn(
                    headline, headlineKind, subtitle, showSubtitle = subtitle != null,
                    showButton = true, textAlpha = { 0f }, buttonAlpha = { (1f - zoom * 4f).coerceIn(0f, 1f) },
                    onSkip = { if (ringing != null) onStopRinging(ringing) else nextAlarm?.let(onSkipUpcoming) },
                    buttonLabel = buttonLabel,
                )
            }
        }

        // + and Edit rise with the list, then dock in the top bar to the left of the gear: +, Edit, gear.
        // dock: 0 while they sit above the first card, 1 once they have reached the top bar.
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(end = 16.dp)
                .offset {
                    val dock = ((statusTopPx + rowPx - actionsTop) / rowPx).coerceIn(0f, 1f)
                    IntOffset(
                        (-40.dp.toPx() * dock).roundToInt(), // clear of the gear
                        maxOf(actionsTop, statusTopPx.toFloat()).roundToInt(),
                    )
                }
                .drawBehind {
                    val dock = ((statusTopPx + rowPx - actionsTop) / rowPx).coerceIn(0f, 1f)
                    // a soft backing so the icons stay readable over cards that slide underneath
                    drawRoundRect(barColor.copy(alpha = 0.9f * dock), cornerRadius = CornerRadius(size.height / 2))
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
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

        IconButton(
            onClick = onSettings,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(horizontal = 8.dp),
        ) { Icon(Icons.Outlined.Settings, "Settings") }

        SnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 172.dp),
        )

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
    ringing: Boolean = false,
) {
    val ctx = LocalContext.current
    val (time, ampm) = timeParts(alarm.hour, alarm.minute, is24)
    val tags = buildList {
        if (ringing) add("Ringing now")
        if (alarm.enabled && alarm.skipUntil > now) add("Skipping ${dayShort(alarm.skipUntil)} · next ${dayShort(alarm.nextTriggerAt)}")
        if (alarm.quick) add("Quick")
        if (alarm.snoozeEnabled) add("Snooze ${alarm.snoozeMinutes} min")
        if (alarm.autoRepeatEnabled) add("Repeats every ${alarm.autoRepeatMinutes} min")
        if (alarm.sessionActive && alarm.nextAgainAt > now)
            add("Rings again ${DateFormat.getTimeFormat(ctx).format(Date(alarm.nextAgainAt))}")
    }
    val dim by animateFloatAsState(if (alarm.enabled) 1f else 0.5f, tween(250), label = "dim")
    val elevation by animateDpAsState(if (dragging) 14.dp else 0.dp, label = "lift")
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier.fillMaxWidth()
            .then(if (editMode) handleModifier else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
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
                        Text(time, fontSize = 44.sp, fontWeight = FontWeight.Normal)
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
                        }
                    } else {
                        Switch(checked = alarm.enabled, onCheckedChange = onToggle)
                    }
                }
            }
        }
    }
}

/** The banner. Drawn twice with identical layout: text behind the list, button in front of it. */
@Composable
private fun BannerColumn(
    headline: String,
    headlineKind: Int,
    subtitle: String?,
    showSubtitle: Boolean,
    showButton: Boolean,
    textAlpha: () -> Float,
    buttonAlpha: () -> Float,
    onSkip: () -> Unit,
    buttonLabel: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 24.dp)) {
        Column(Modifier.graphicsLayer { alpha = textAlpha() }, horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(
                targetState = headlineKind,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                label = "headline",
            ) { _ ->
                Text(
                    headline, fontSize = 32.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            AnimatedVisibility(visible = showSubtitle) {
                Text(
                    subtitle ?: "", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        AnimatedVisibility(
            visible = showButton,
            enter = fadeIn(tween(200)) + expandVertically(tween(200)),
            exit = fadeOut(tween(150)) + shrinkVertically(tween(150)),
        ) {
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier.padding(top = 18.dp).graphicsLayer { alpha = buttonAlpha() },
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            ) { Text(buttonLabel) }
        }
    }
}
