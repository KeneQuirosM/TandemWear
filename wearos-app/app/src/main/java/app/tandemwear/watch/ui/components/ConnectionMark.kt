package app.tandemwear.watch.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * TandemWear identity: two interlocked rings (iPhone + watch). Used as the connection
 * indicator: [linked] draws both rings in [color]; otherwise the second ring is dimmed,
 * so the state reads without relying on color alone. Decorative: the caller provides the
 * text that TalkBack reads.
 */
@Composable
fun ConnectionMark(color: Color, linked: Boolean, modifier: Modifier = Modifier, size: Dp = 32.dp) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = this.size.minDimension * 0.11f
        val radius = this.size.minDimension * 0.3f
        val cy = this.size.height / 2f
        val left = Offset(this.size.width / 2f - radius * 0.6f, cy)
        val right = Offset(this.size.width / 2f + radius * 0.6f, cy)
        drawCircle(color = color, radius = radius, center = left, style = Stroke(width = stroke))
        drawCircle(
            color = if (linked) color else color.copy(alpha = 0.35f),
            radius = radius,
            center = right,
            style = Stroke(width = stroke)
        )
    }
}
