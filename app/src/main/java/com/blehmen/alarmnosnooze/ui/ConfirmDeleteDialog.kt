package com.blehmen.alarmnosnooze.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/** Same look as an alarm card: rounded surface with the small accent tab on the left. */
@Composable
fun ConfirmDeleteDialog(onCancel: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onCancel) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp,
        ) {
            Box {
                Box(
                    Modifier.align(Alignment.CenterStart).width(6.dp).height(56.dp)
                        .clip(RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
                Column(Modifier.padding(start = 28.dp, end = 22.dp, top = 24.dp, bottom = 20.dp)) {
                    Text("Delete alarm?", fontSize = 24.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "This alarm will be permanently deleted. This can't be undone.",
                        fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(22.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) { Text("Cancel", fontWeight = FontWeight.Bold) }
                        Button(
                            onClick = onConfirm,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFD93025),
                                contentColor = Color.White,
                            ),
                        ) { Text("Delete", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}
