package com.blehmen.alarmnosnooze.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Accent edge along the left side of a rounded card, top to bottom. It is the card's own outline minus the same
 * outline shifted right by [thickness], so it hugs the corners and thins to a point where they flatten out.
 * Place it first inside a Box in a clipped, rounded Surface of colour [surface], behind the card content.
 */
@Composable
fun AccentEdge(
    surface: Color,
    modifier: Modifier = Modifier.fillMaxSize(),
    corner: Dp = 28.dp,
    thickness: Dp = 6.dp,
) {
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val radius = CornerRadius(corner.toPx())
        drawRoundRect(accent, cornerRadius = radius)
        drawRoundRect(surface, topLeft = Offset(thickness.toPx(), 0f), size = size, cornerRadius = radius)
    }
}
