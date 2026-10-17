package com.blehmen.alarmnosnooze

import android.os.Bundle
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.positionChange
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
import kotlinx.coroutines.Job
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
 * The X button never moves. A translucent circle sits behind it and follows your finger, growing as you pull.
 * Idle, that circle breathes in and out from behind the button and the button gives a small shake now and then,
 * both pausing while you touch and resuming a couple of seconds after you let go.
 * Release past the threshold and the circle floods the screen and fires the action; otherwise it springs back.
 */
@Composable
private fun SwipeAction(icon: ImageVector, hint: String, onTrigger: () -> Unit) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val buttonSize = 104.dp
    val buttonRadius = with(density) { (buttonSize / 2).toPx() }
    val thresholdPx = with(density) { 120.dp.toPx() }
    val idleExtraPx = with(density) { 30.dp.toPx() }
    val grabExtraPx = with(density) { 26.dp.toPx() }
    val floodRadius = with(density) { 900.dp.toPx() }
    val shakePx = with(density) { 9.dp.toPx() }

    var pull by remember { mutableStateOf(Offset.Zero) }       // where the finger has taken the circle
    val follow = remember { Animatable(1f) }                    // 1 = under the finger, 0 = back home
    val flood = remember { Animatable(0f) }                     // 0 -> 1 when the action fires
    val shake = remember { Animatable(0f) }
    var touched by remember { mutableStateOf(false) }
    var idle by remember { mutableStateOf(true) }
    var fired by remember { mutableStateOf(false) }
    var past by remember { mutableStateOf(false) }
    val idleJob = remember { arrayOfNulls<Job>(1) }

    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    val amp by animateFloatAsState(if (idle) 1f else 0f, tween(350), label = "amp")
    val grab by animateFloatAsState(if (touched) 1f else 0f, tween(200), label = "grab")

    // Occasional shake of the X button while untouched.
    LaunchedEffect(idle) {
        if (!idle) { shake.snapTo(0f); return@LaunchedEffect }
        while (true) {
            delay(4200)
            for (v in listOf(-1f, 1f, -0.8f, 0.8f, -0.4f, 0f)) shake.animateTo(v, tween(70))
        }
    }

    val shown = pull * follow.value
    val dist = shown.getDistance()
    val progress = (dist / thresholdPx).coerceIn(0f, 1f)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(buttonSize + 40.dp)
                .drawBehind {
                    val radius = lerp(
                        buttonRadius * 0.9f + idleExtraPx * pulse * amp + (grabExtraPx + dist * 0.8f) * grab,
                        floodRadius,
                        flood.value,
                    )
                    val alpha = (0.18f + 0.28f * progress + 0.30f * flood.value).coerceAtMost(0.8f)
                    // Never painted over the button itself, so at rest the circle is hidden "behind" it.
                    val button = Path().apply { addOval(Rect(center, buttonRadius)) }
                    clipPath(button, ClipOp.Difference) {
                        drawCircle(Color.White.copy(alpha = alpha), radius = radius, center = center + shown)
                    }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (fired) return@awaitEachGesture
                        idleJob[0]?.cancel()
                        touched = true
                        idle = false
                        pull = pull * follow.value        // pick up from wherever it currently is
                        scope.launch { follow.snapTo(1f) }

                        val finished = drag(down.id) { change ->
                            change.consume()
                            pull += change.positionChange()
                            val nowPast = pull.getDistance() >= thresholdPx
                            if (nowPast && !past) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            past = nowPast
                        }
                        touched = false
                        if (finished && pull.getDistance() >= thresholdPx) {
                            fired = true
                            scope.launch {
                                flood.animateTo(1f, tween(240))
                                onTrigger()
                            }
                        } else {
                            scope.launch {
                                follow.animateTo(0f, spring(dampingRatio = 0.65f, stiffness = 300f))
                                pull = Offset.Zero
                                follow.snapTo(1f)
                                past = false
                            }
                            idleJob[0] = scope.launch { delay(2000); idle = true }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .graphicsLayer { translationX = shake.value * shakePx; rotationZ = shake.value * 6f }
                    .size(buttonSize)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.28f)),
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
