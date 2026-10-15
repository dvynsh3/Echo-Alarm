package com.blehmen.alarmnosnooze.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blehmen.alarmnosnooze.data.Reminder
import com.blehmen.alarmnosnooze.engine.reminderTriggerAt
import com.blehmen.alarmnosnooze.engine.timeParts
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderEditorScreen(
    initial: Reminder,
    isNew: Boolean,
    defaultMinutes: Int,
    onSave: (Reminder) -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val ctx = LocalContext.current
    val is24 = remember { DateFormat.is24HourFormat(ctx) }
    var r by remember { mutableStateOf(initial) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    val inPast = reminderTriggerAt(r, defaultMinutes) <= System.currentTimeMillis()
    val h = if (r.hasTime) r.hour else defaultMinutes / 60
    val m = if (r.hasTime) r.minute else defaultMinutes % 60
    val (t, ap) = timeParts(h, m, is24)
    val timeText = if (ap.isEmpty()) t else "$t $ap"

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Text(
                if (isNew) "New reminder" else "Edit reminder",
                fontSize = 28.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 24.dp, top = 32.dp, bottom = 24.dp),
            )
            Surface(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 24.dp, bottom = 120.dp)) {
                    TextField(
                        value = r.title,
                        onValueChange = { r = r.copy(title = it.take(80)) },
                        placeholder = { Text("What should it remind you of?") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        ),
                    )
                    Spacer(Modifier.height(16.dp))

                    Column(Modifier.fillMaxWidth().clickable { showDate = true }.padding(vertical = 14.dp)) {
                        Text("Date", fontSize = 18.sp)
                        Text(
                            LocalDate.ofEpochDay(r.epochDay).format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")),
                            fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            Modifier.weight(1f).clickable { if (r.hasTime) showTime = true }.padding(vertical = 8.dp)
                        ) {
                            Text("Time", fontSize = 18.sp)
                            Text(
                                if (r.hasTime) timeText else "Not set. Uses default time ($timeText)",
                                fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = r.hasTime,
                            onCheckedChange = { on ->
                                r = if (on) r.copy(hasTime = true, hour = defaultMinutes / 60, minute = defaultMinutes % 60)
                                else r.copy(hasTime = false)
                                if (on) showTime = true
                            },
                        )
                    }

                    if (inPast) {
                        Text("That time has already passed.", color = MaterialTheme.colorScheme.error,
                            fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
                    }
                    if (!isNew) {
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = onDelete) { Text("Delete reminder", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCancel, modifier = Modifier.width(120.dp)) { Text("Cancel", fontWeight = FontWeight.Bold) }
                Box(Modifier.width(1.dp).height(28.dp).background(MaterialTheme.colorScheme.outline))
                TextButton(onClick = { onSave(r) }, enabled = !inPast, modifier = Modifier.width(120.dp)) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showDate) {
        val todayUtc = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val st = rememberDatePickerState(
            initialSelectedDateMillis = r.epochDay * 86_400_000L,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayUtc
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { ms ->
                        r = r.copy(epochDay = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay())
                    }
                    showDate = false
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = st) }
    }

    if (showTime) {
        val st = rememberTimePickerState(h, m, is24)
        AlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text("Reminder time") },
            text = { TimePicker(state = st) },
            confirmButton = {
                TextButton(onClick = { r = r.copy(hasTime = true, hour = st.hour, minute = st.minute); showTime = false }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel") } },
        )
    }
}
