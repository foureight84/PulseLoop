package com.pulseloop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Simple line chart composable — draws a polyline from data points.
 * No external charting library needed.
 */
@Composable
fun SimpleLineChart(
    points: List<Double>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    lineWidth: Float = 2f,
    showDots: Boolean = true,
) {
    if (points.isEmpty()) return
    val min = points.min()
    val max = points.max()
    val range = if (max == min) 1.0 else max - min

    Canvas(modifier = modifier.fillMaxWidth().height(100.dp)) {
        val w = size.width
        val h = size.height
        val pad = 8f
        val stepX = (w - pad * 2) / maxOf(1, points.size - 1)

        val path = Path()
        points.forEachIndexed { i, value ->
            val x = pad + i * stepX
            val y = pad + (h - pad * 2) * (1f - ((value - min) / range).toFloat())
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)

            if (showDots) {
                drawCircle(color, 3f, Offset(x, y))
            }
        }
        drawPath(path, color, style = Stroke(width = lineWidth, cap = StrokeCap.Round))
    }
}

/**
 * Metric card with embedded mini sparkline — ported from MiniSparkline in DesignSystem.
 */
@Composable
fun MetricWithSparkline(
    label: String,
    value: String,
    unit: String,
    sparkline: List<Double>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    androidx.compose.material3.Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp))
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                androidx.compose.foundation.layout.Spacer(Modifier.width(4.dp))
                Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 2.dp))
            }
            if (sparkline.isNotEmpty()) {
                androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                SimpleLineChart(points = sparkline, modifier = Modifier.fillMaxWidth(), color = color, showDots = false)
            }
        }
    }
}
