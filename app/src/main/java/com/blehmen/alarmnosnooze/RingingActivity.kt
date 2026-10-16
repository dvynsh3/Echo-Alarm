package com.blehmen.alarmnosnooze

import android.os.Bundle
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.blehmen.alarmnosnooze.data.AppSettings
import com.blehmen.alarmnosnooze.service.AlarmService
import com.blehmen.alarmnosnooze.ui.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class RingingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent {
            val settings by app.settings.flow.collectAsState(AppSettings())
            AppTheme(settings.themeMode, settings.accent) {
                RingingScreen(Color(settings.accent)) { finish() }
            }
        }
    }
}

@Composable
private fun RingingScreen(accent: Color, onFinish: () -> Unit) {
    val ctx = LocalContext.current
    val alarm by AlarmService.state.collectAsState()
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(alarm) {
        if (alarm != null) seen = true else if (seen) onFinish()
    }
    LaunchedEffect(Unit) { delay(6000); if (AlarmService.state.value == null) onFinish() }

    val is24 = remember { DateFormat.is24HourFormat(ctx) }
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalTime.now(); delay(1000) } }
    val hour = if (is24) now.hour else (now.hour + 11) % 12 + 1

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF14003A), accent.copy(alpha = 0.55f), Color(0xFF9C7B6B)))),
    ) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Text("%02d".format(hour), fontSize = 120.sp, fontWeight = FontWeight.Medium, color = Color.White, lineHeight = 120.sp)
            Text("%02d".format(now.minute), fontSize = 120.sp, fontWeight = FontWeight.Medium, color = Color.White, lineHeight = 120.sp)
            Spacer(Modifier.height(8.dp))
            Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, MMMM d")), fontSize = 18.sp, color = Color.White)
            alarm?.let {
                Text(it.label.ifBlank { "Alarm" }, fontSize = 26.sp, color = Color.White, modifier = Modifier.padding(top = 12.dp), textAlign = TextAlign.Center)
            }
            Spacer(Modifier.weight(1.1f))

            alarm?.let { a ->
                // Keyed on the alarm so a fresh ring always starts with a fresh, un-dragged control.
                key(a.id) {
                    if (a.autoRepeatEnabled) {
                        SwipeAction(Icons.Filled.VolumeOff, "Swipe to silence") {
                            AlarmService.send(ctx, AlarmService.ACTION_SILENCE, a.id)
                        }
                        Text("Rings again in ${a.autoRepeatMinutes} mins", color = Color.White.copy(alpha = 0.85f),
                            fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
                    } else {
                        SwipeAction(Icons.Filled.Close, "Swipe to turn off") {
                            AlarmService.send(ctx, AlarmService.ACTION_TURN_OFF, a.id)
                        }
                        if (a.snoozeEnabled) {
                            Spacer(Modifier.height(20.dp))
                            Box(
                                Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.2f))
                                    .clickable { AlarmService.send(ctx, AlarmService.ACTION_SNOOZE, a.id) }
                                    .padding(horizontal = 40.dp, vertical = 18.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text("Snooze ${a.snoozeMinutes} mins", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * Drag the button in any direction. A translucent circle grows behind it as you pull, and letting go
 * past the threshold floods the screen and fires the action. Let go early and it springs back.
 */
@Composable
private fun SwipeAction(icon: ImageVector, hint: String, onTrigger: () -> Unit) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val buttonSize = 104.dp
    val thresholdPx = with(density) { 120.dp.toPx() }
    val restRadius = with(density) { (buttonSize / 2 + 16.dp).toPx() }
    val floodRadius = with(density) { 800.dp.toPx() }

    var drag by remember { mutableStateOf(Offset.Zero) }
    val follow = remember { Animatable(1f) }   // 1 = under the finger, 0 = back at rest
    val flood = remember { Animatable(0f) }    // 0 -> 1 when the action fires
    var fired by remember { mutableStateOf(false) }
    var pastThreshold by remember { mutableStateOf(false) }

    val shown = drag * follow.value
    val dist = shown.getDistance()
    val progress = (dist / thresholdPx).coerceIn(0f, 1f)

    fun springBack() {
        scope.launch {
            follow.animateTo(0f, spring(dampingRatio = 0.65f, stiffness = 300f))
            drag = Offset.Zero
            follow.snapTo(1f)
            pastThreshold = false
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(buttonSize + 32.dp)
                .drawBehind {
                    val radius = lerp(restRadius + dist, floodRadius, flood.value)
                    val alpha = (0.16f + 0.30f * progress + 0.30f * flood.value).coerceAtMost(0.8f)
                    drawCircle(Color.White.copy(alpha = alpha), radius = radius)
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            if (!fired) {
                                drag = drag * follow.value // pick up from wherever it currently is
                                scope.launch { follow.snapTo(1f) }
                            }
                        },
                        onDragEnd = {
                            if (!fired) {
                                if (drag.getDistance() >= thresholdPx) {
                                    fired = true
                                    scope.launch {
                                        flood.animateTo(1f, tween(240))
                                        onTrigger()
                                    }
                                } else springBack()
                            }
                        },
                        onDragCancel = { if (!fired) springBack() },
                        onDrag = { change, amount ->
                            if (!fired) {
                                change.consume()
                                drag += amount
                                val past = drag.getDistance() >= thresholdPx
                                if (past && !pastThreshold) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                pastThreshold = past
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .offset { IntOffset(shown.x.roundToInt(), shown.y.roundToInt()) }
                    .size(buttonSize)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.28f + 0.2f * progress)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, hint, tint = Color.White, modifier = Modifier.size(44.dp)) }
        }
        Text(
            hint,
            color = Color.White.copy(alpha = (1f - progress * 1.5f).coerceIn(0f, 1f)),
            fontSize = 16.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
