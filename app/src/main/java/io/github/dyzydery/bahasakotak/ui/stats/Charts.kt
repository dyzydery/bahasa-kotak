package io.github.dyzydery.bahasakotak.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Minimal single-series charts. Marks use the theme's primary color, text stays in text colors, grid and
 * baseline are recessive. Tapping a mark shows its exact value in the readout line above the plot (the
 * touch equivalent of a hover tooltip); with nothing selected the readout shows [summary].
 */
@Composable
fun BarChart(
    values: List<Int>,
    labelFor: (Int) -> String,
    summary: String,
    axisLabels: List<String>,
    height: Dp = 140.dp,
) {
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    val color = MaterialTheme.colorScheme.primary
    val selectedColor = MaterialTheme.colorScheme.tertiary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)

    Column {
        Readout(selected?.let(labelFor) ?: summary)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(values) {
                    detectTapGestures { offset ->
                        val index = (offset.x / (size.width.toFloat() / values.size)).toInt().coerceIn(values.indices)
                        selected = if (selected == index) null else index
                    }
                }
        ) {
            val slot = size.width / values.size
            val gap = 2.dp.toPx()
            val radius = 4.dp.toPx()
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            values.forEachIndexed { i, v ->
                if (v == 0) return@forEachIndexed
                val barHeight = size.height * v / max
                val left = i * slot + gap / 2
                val width = (slot - gap).coerceAtLeast(1f)
                // Round only the data end: draw a rounded bar, then clip off the rounding at the baseline.
                clipRect(left, size.height - barHeight, left + width, size.height) {
                    drawRoundRect(
                        if (i == selected) selectedColor else color,
                        topLeft = Offset(left, size.height - barHeight),
                        size = Size(width, barHeight + radius),
                        cornerRadius = CornerRadius(radius.coerceAtMost(width / 2)),
                    )
                }
            }
        }
        AxisLabels(axisLabels)
    }
}

@Composable
fun LineChart(
    values: List<Int>,
    labelFor: (Int) -> String,
    summary: String,
    axisLabels: List<String>,
    height: Dp = 140.dp,
) {
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    val color = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val grid = MaterialTheme.colorScheme.outlineVariant
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)

    Column {
        Readout(selected?.let(labelFor) ?: summary)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .padding(vertical = 6.dp)
                .pointerInput(values) {
                    detectTapGestures { offset ->
                        val step = size.width.toFloat() / (values.size - 1).coerceAtLeast(1)
                        val index = (offset.x / step).roundToInt().coerceIn(values.indices)
                        selected = if (selected == index) null else index
                    }
                }
        ) {
            val step = size.width / (values.size - 1).coerceAtLeast(1)
            fun point(i: Int) = Offset(i * step, size.height - size.height * values[i] / max)
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            val path = Path().apply {
                values.indices.forEach { i -> if (i == 0) moveTo(point(i).x, point(i).y) else lineTo(point(i).x, point(i).y) }
            }
            drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
            selected?.let { i ->
                drawLine(grid, Offset(point(i).x, 0f), Offset(point(i).x, size.height), strokeWidth = 1.dp.toPx())
                drawCircle(surface, radius = 6.dp.toPx(), center = point(i))
                drawCircle(color, radius = 4.dp.toPx(), center = point(i))
            }
        }
        AxisLabels(axisLabels)
    }
}

/** A thin single-value bar for proportions such as topic progress. */
@Composable
fun ProportionBar(fraction: Float) {
    val color = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(Modifier.fillMaxWidth().height(8.dp)) {
        val radius = CornerRadius(4.dp.toPx())
        drawRoundRect(track, cornerRadius = radius)
        if (fraction > 0f) drawRoundRect(color, size = Size(size.width * fraction.coerceIn(0f, 1f), size.height), cornerRadius = radius)
    }
}

@Composable
private fun Readout(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

/**
 * Labels spread evenly under the plot: one per bar when counts match, or first/middle/last for a time axis.
 */
@Composable
private fun AxisLabels(labels: List<String>) {
    if (labels.isEmpty()) return
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        labels.forEachIndexed { i, label ->
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = when {
                    labels.size > 3 -> TextAlign.Center
                    i == 0 -> TextAlign.Start
                    i == labels.lastIndex -> TextAlign.End
                    else -> TextAlign.Center
                },
            )
        }
    }
}
