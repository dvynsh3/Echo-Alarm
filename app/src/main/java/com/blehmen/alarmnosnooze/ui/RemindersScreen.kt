package com.blehmen.alarmnosnooze.ui

import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blehmen.alarmnosnooze.data.Reminder
import com.blehmen.alarmnosnooze.engine.ReminderEngine
import com.blehmen.alarmnosnooze.engine.timeParts
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val reminderDateFmt = DateTimeFormatter.ofPattern("EEE, d MMM")

fun reminderWhen(r: Reminder, defaultMinutes: Int, is24: Boolean): String {
    val h = if (r.hasTime) r.hour else defaultMinutes / 60
    val m = if (r.hasTime) r.minute else defaultMinutes % 60
    val (t, ap) = timeParts(h, m, is24)
    return "${LocalDate.ofEpochDay(r.epochDay).format(reminderDateFmt)} · ${if (ap.isEmpty()) t else "$t $ap"}"
}

@Composable
fun RemindersScreen(
    reminders: List<Reminder>,
    defaultMinutes: Int,
    onOpen: (Reminder) -> Unit,
    onAdd: () -> Unit,
    onDelete: (Reminder) -> Unit,
    onTurnOff: (Reminder) -> Unit,
    onSettings: () -> Unit,
) {
    val ctx = LocalContext.current
    val is24 = remember { DateFormat.is24HourFormat(ctx) }
    var menu by remember { mutableStateOf(false) }
    val open = reminders.count { it.state != ReminderEngine.DONE }
    val headline = when (open) {
        0 -> "No upcoming reminders"
        1 -> "1 upcoming reminder"
        else -> "$open upcoming reminders"
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, "Add reminder") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Settings") }, onClick = { menu = false; onSettings() })
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            Text(headline, fontSize = 28.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(reminders, key = { it.id }) { r ->
                ReminderCard(r, reminderWhen(r, defaultMinutes, is24), { onOpen(r) }, { onDelete(r) }, { onTurnOff(r) })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReminderCard(
    r: Reminder,
    whenText: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onTurnOff: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val done = r.state == ReminderEngine.DONE
    Surface(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = { menu = true }),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 18.dp).alpha(if (done) 0.5f else 1f)) {
            Text(r.title, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            Text(whenText, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp))
            when (r.state) {
                ReminderEngine.ACTIVE -> Text("Active", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp))
                ReminderEngine.DONE -> Text("Turned off", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (r.state == ReminderEngine.ACTIVE)
                    DropdownMenuItem(text = { Text("Turn off") }, onClick = { menu = false; onTurnOff() })
                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
            }
        }
    }
}
