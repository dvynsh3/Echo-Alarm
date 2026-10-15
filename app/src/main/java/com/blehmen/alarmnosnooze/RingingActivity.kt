package com.blehmen.alarmnosnooze

import android.os.Bundle
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blehmen.alarmnosnooze.data.AppSettings
import com.blehmen.alarmnosnooze.service.AlarmService
import com.blehmen.alarmnosnooze.ui.AppTheme
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

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
            Spacer(Modifier.weight(1.2f))

            alarm?.let { a ->
                if (a.autoRepeatEnabled) {
                    RoundAction(Icons.Filled.VolumeOff, "Silence") { AlarmService.send(ctx, AlarmService.ACTION_SILENCE, a.id) }
                    Text("Rings again in ${a.autoRepeatMinutes} mins", color = Color.White.copy(alpha = 0.85f),
                        fontSize = 15.sp, modifier = Modifier.padding(top = 12.dp))
                } else {
                    RoundAction(Icons.Filled.Close, "Turn off") { AlarmService.send(ctx, AlarmService.ACTION_TURN_OFF, a.id) }
                    if (a.snoozeEnabled) {
                        Spacer(Modifier.height(24.dp))
                        Box(
                            Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.2f))
                                .clickable { AlarmService.send(ctx, AlarmService.ACTION_SNOOZE, a.id) }
                                .padding(horizontal = 40.dp, vertical = 18.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("Snooze ${a.snoozeMinutes} mins", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp) }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(104.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(44.dp)) }
        Text(label, color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
    }
}
