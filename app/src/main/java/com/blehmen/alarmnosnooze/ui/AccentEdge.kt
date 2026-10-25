package com.blehmen.alarmnosnooze.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Accent edge along the left side of a rounded card, top to bottom: the card's outline minus the same outline shifted
 * right by [thickness]. It hugs the corners and thins to a point where they flatten out. Only that crescent is drawn,
 * so nothing sits under the rest of the card edge to leave a thin fringe.
 */
@Composable
fun AccentEdge(
    modifier: Modifier = Modifier.fillMaxSize(),
    corner: Dp = 28.dp,
    thickness: Dp = 6.dp,
) {
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val radius = CornerRadius(corner.toPx())
        val t = thickness.toPx()
        val card = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, radius)) }
        val shifted = Path().apply { addRoundRect(RoundRect(t, 0f, size.width + t, size.height, radius)) }
        drawPath(Path.combine(PathOperation.Difference, card, shifted), accent)
    }
}
