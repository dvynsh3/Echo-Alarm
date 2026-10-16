package com.blehmen.alarmnosnooze.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.IntentCompat
import com.blehmen.alarmnosnooze.data.Alarm
import com.blehmen.alarmnosnooze.data.Days
import com.blehmen.alarmnosnooze.engine.nextOccurrence
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private enum class EditorDialog { Snooze, Repeat, EndTime }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditorScreen(
    initial: Alarm,
    isNew: Boolean,
    onSave: (Alarm) -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val ctx = LocalContext.current
    val is24 = remember { DateFormat.is24HourFormat(ctx) }
    var a by remember { mutableStateOf(initial) }
    var dialog by remember { mutableStateOf<EditorDialog?>(null) }

    val soundName = remember(a.soundUri) {
        val uri = a.soundUri?.let { Uri.parse(it) } ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        try { RingtoneManager.getRingtone(ctx, uri)?.getTitle(ctx) ?: "Default" } catch (_: Exception) { "Default" }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val uri = r.data?.let { IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) }
            a = a.copy(soundUri = uri?.toString())
        }
    }

    val dateText = if (a.repeatDays == 0) {
        val d = nextOccurrence(a.hour, a.minute, 0).toLocalDate()
        (if (d == LocalDate.now()) "Today" else "Tomorrow") + " - " + d.format(DateTimeFormatter.ofPattern("EEE, d MMM"))
    } else Days.summary(a.repeatDays)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth()) {
                TimeWheels(a.hour, a.minute, is24) { h, m -> a = a.copy(hour = h, minute = m) }
                if (!isNew) {
                    IconButton(onClick = onDelete, modifier = Modifier.align(Alignment.TopEnd).padding(end = 8.dp)) {
                        Icon(Icons.Outlined.Delete, "Delete alarm", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            Surface(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
                        .padding(top = 24.dp, bottom = if (isNew) 120.dp else 180.dp)
                ) {
                    Text(dateText, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(20.dp))
                    DayChips(a.repeatDays) { a = a.copy(repeatDays = it) }
                    Spacer(Modifier.height(16.dp))

                    TextField(
                        value = a.label,
                        onValueChange = { a = a.copy(label = it.take(40)) },
                        placeholder = { Text("Alarm name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        ),
                    )
                    Spacer(Modifier.height(8.dp))

                    SettingRow("Sound", soundName) {
                        picker.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            a.soundUri?.let { putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it)) }
                        })
                    }

                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text("Volume", fontSize = 18.sp)
                        Slider(value = a.volume / 100f, onValueChange = { a = a.copy(volume = (it * 100).toInt().coerceAtLeast(5)) })
                    }

                    ToggleRow("Vibration", null, a.vibrate, { a = a.copy(vibrate = it) })

                    ToggleRow("Gradual volume", if (a.rampEnabled) "Ramps up over ${a.rampSeconds} seconds" else null,
                        a.rampEnabled, { a = a.copy(rampEnabled = it) })
                    AnimatedVisibility(a.rampEnabled, enter = expandVertically(tween(220)) + fadeIn(), exit = shrinkVertically(tween(180)) + fadeOut()) {
                        Slider(
                            value = a.rampSeconds.toFloat(),
                            onValueChange = { a = a.copy(rampSeconds = it.toInt()) },
                            valueRange = 5f..120f,
                        )
                    }

                    ToggleRow(
                        "Snooze", if (a.snoozeEnabled) "${a.snoozeMinutes} minutes" else "Off",
                        a.snoozeEnabled,
                        { on -> a = a.copy(snoozeEnabled = on, autoRepeatEnabled = if (on) false else a.autoRepeatEnabled) },
                        onClick = { dialog = EditorDialog.Snooze },
                    )
                    ToggleRow(
                        "Auto repeat",
                        if (a.autoRepeatEnabled) "Every ${a.autoRepeatMinutes} minutes. Turn off in app to stop" else "Off",
                        a.autoRepeatEnabled,
                        { on -> a = a.copy(autoRepeatEnabled = on, snoozeEnabled = if (on) false else a.snoozeEnabled) },
                        onClick = { dialog = EditorDialog.Repeat },
                    )
                    AnimatedVisibility(a.autoRepeatEnabled, enter = expandVertically(tween(220)) + fadeIn(), exit = shrinkVertically(tween(180)) + fadeOut()) {
                        val sub = a.endMinutes?.let { "%02d:%02d".format(it / 60, it % 60) } ?: "Not set (uses max repeats)"
                        SettingRow("End time", sub) { dialog = EditorDialog.EndTime }
                    }
                }
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!isNew) {
                // Narrower than the Cancel | Save pill below it.
                Surface(
                    onClick = onDelete,
                    modifier = Modifier.width(150.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Box(Modifier.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Text("Delete", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onCancel, modifier = Modifier.width(120.dp)) { Text("Cancel", fontWeight = FontWeight.Bold) }
                    Box(Modifier.width(1.dp).height(28.dp).background(MaterialTheme.colorScheme.outline))
                    TextButton(onClick = { onSave(a) }, modifier = Modifier.width(120.dp)) { Text("Save", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }

    when (dialog) {
        EditorDialog.Snooze -> DurationDialog("Snooze duration", a.snoozeMinutes, { dialog = null }) {
            a = a.copy(snoozeMinutes = it, snoozeEnabled = true, autoRepeatEnabled = false); dialog = null
        }
        EditorDialog.Repeat -> DurationDialog("Repeat every", a.autoRepeatMinutes, { dialog = null }) {
            a = a.copy(autoRepeatMinutes = it, autoRepeatEnabled = true, snoozeEnabled = false); dialog = null
        }
        EditorDialog.EndTime -> {
            val start = a.endMinutes ?: 8 * 60
            val st = rememberTimePickerState(start / 60, start % 60, is24)
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("End time") },
                text = { TimePicker(state = st) },
                confirmButton = { TextButton(onClick = { a = a.copy(endMinutes = st.hour * 60 + st.minute); dialog = null }) { Text("Set") } },
                dismissButton = { TextButton(onClick = { a = a.copy(endMinutes = null); dialog = null }) { Text("Clear") } },
            )
        }
        null -> {}
    }
}

@Composable
private fun DayChips(mask: Int, onChange: (Int) -> Unit) {
    val letters = listOf("M", "T", "W", "T", "F", "S", "S")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        letters.forEachIndexed { i, l ->
            val on = (mask and (1 shl i)) != 0
            val bg by animateColorAsState(if (on) MaterialTheme.colorScheme.primary else Color.Transparent, tween(200), label = "day")
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(bg).clickable { onChange(mask xor (1 shl i)) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    l, fontSize = 16.sp,
                    color = when {
                        on -> MaterialTheme.colorScheme.onPrimary
                        i == 6 -> Color(0xFFE5472E)
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp)) {
        Text(title, fontSize = 18.sp)
        if (subtitle != null) Text(subtitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier.weight(1f).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 8.dp)
        ) {
            Text(title, fontSize = 18.sp)
            if (subtitle != null) Text(subtitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onClick != null) {
            Box(Modifier.padding(horizontal = 12.dp).width(1.dp).height(32.dp).background(MaterialTheme.colorScheme.outlineVariant))
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Hand-built dialog so the gap between the options and the buttons is under our control
 * (AlertDialog leaves a large empty band when the confirm button is hidden).
 */
@Composable
fun DurationDialog(title: String, current: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val presets = listOf(2, 5, 10, 30)
    var custom by remember { mutableStateOf(current !in presets) }
    var text by remember { mutableStateOf(current.toString()) }
    val value = text.toIntOrNull()
    val valid = value != null && value in 1..720
    val focus = remember { FocusRequester() }

    LaunchedEffect(custom) {
        if (custom) { delay(120); runCatching { focus.requestFocus() } }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 22.dp, bottom = 6.dp)) {
                Text(title, fontSize = 22.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
                Spacer(Modifier.height(8.dp))
                presets.forEach { m -> RadioLine("$m minutes", !custom && current == m) { onPick(m) } }
                RadioLine("Custom", custom) { custom = true }
                AnimatedVisibility(custom, enter = expandVertically(tween(200)) + fadeIn(), exit = shrinkVertically(tween(150)) + fadeOut()) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.filter(Char::isDigit).take(3) },
                        suffix = { Text("min") },
                        singleLine = true,
                        isError = text.isNotEmpty() && !valid,
                        supportingText = { Text("1 to 720 minutes") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (valid) onPick(value!!) }),
                        modifier = Modifier.padding(horizontal = 12.dp).padding(top = 4.dp).fillMaxWidth().focusRequester(focus),
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    if (custom) TextButton(onClick = { onPick(value!!) }, enabled = valid) { Text("Set") }
                }
            }
        }
    }
}

@Composable
private fun RadioLine(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(14.dp))
        Text(label, fontSize = 16.sp)
    }
}
