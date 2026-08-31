package com.phonediagnostic.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Minimal filled line chart. No axes and no interaction — it exists to show the
 * shape of a trend next to the exact current value, not to be read off precisely.
 */
@Composable
fun Sparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    /** Fixed bounds, for series with a natural range such as battery percent. */
    minValue: Float? = null,
    maxValue: Float? = null
) {
    val fillColor = lineColor.copy(alpha = 0.16f)
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.20f)

    Canvas(modifier = modifier) {
        // A single point has no slope to draw; the caller shows the value as text.
        if (values.size < 2 || size.width <= 0f || size.height <= 0f) return@Canvas

        val low = minValue ?: values.min()
        val high = maxValue ?: values.max()
        // Flat series would divide by zero; give them a band to sit in the middle of.
        val span = (high - low).takeIf { it > 0.0001f } ?: 1f

        val stepX = size.width / (values.size - 1)
        fun yFor(value: Float): Float {
            val normalized = ((value - low) / span).coerceIn(0f, 1f)
            return size.height - (normalized * size.height)
        }

        // Midline for a sense of scale.
        drawLine(
            color = gridColor,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 1f
        )

        val line = Path()
        values.forEachIndexed { index, value ->
            val x = index * stepX
            val y = yFor(value)
            if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }

        val area = Path()
        area.addPath(line)
        area.lineTo(size.width, size.height)
        area.lineTo(0f, size.height)
        area.close()

        drawPath(path = area, color = fillColor)
        drawPath(
            path = line,
            color = lineColor,
            style = Stroke(width = 2.5f.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/**
 * A [Sparkline] with the framing every chart on the History screen needs:
 * title, the latest reading, and the range covered.
 */
@Composable
fun MetricChartCard(
    title: String,
    currentLabel: String,
    rangeLabel: String,
    values: List<Float>,
    lineColor: Color,
    minValue: Float? = null,
    maxValue: Float? = null,
    emptyLabel: String = "Not enough samples yet"
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = currentLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            if (values.size < 2) {
                Text(
                    text = emptyLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Sparkline(
                    values = values,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    lineColor = lineColor,
                    minValue = minValue,
                    maxValue = maxValue
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = rangeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
