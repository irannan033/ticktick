package com.ppm.momentum.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ppm.momentum.domain.ChartPoint

data class PieSlice(val label: String, val value: Float, val color: Color)

/** Donut chart with a legend; draws a neutral ring when there is no data. */
@Composable
fun PieChart(slices: List<PieSlice>, centerText: String, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 22.dp.toPx()
                val topLeft = Offset(stroke / 2, stroke / 2)
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val total = slices.sumOf { it.value.toDouble() }.toFloat()
                if (total <= 0f) {
                    drawArc(color = track, startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
                } else {
                    var start = -90f
                    slices.forEach { s ->
                        val sweep = s.value / total * 360f
                        if (sweep > 0f) {
                            drawArc(color = s.color, startAngle = start, sweepAngle = sweep, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
                        }
                        start += sweep
                    }
                }
            }
            Text(centerText, style = MaterialTheme.typography.titleMedium)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            slices.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(10.dp).background(s.color, CircleShape))
                    Text("${s.label}: ${s.value.toInt()}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun AxisLabels(points: List<ChartPoint>) {
    Row(Modifier.fillMaxWidth()) {
        points.forEachIndexed { i, p ->
            val show = points.size <= 8 || i % 2 == 0
            Text(
                if (show) p.label else "", Modifier.weight(1f), fontSize = 9.sp, maxLines = 1, softWrap = false,
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ValueLabels(points: List<ChartPoint>) {
    Row(Modifier.fillMaxWidth()) {
        points.forEach { p ->
            Text(
                if (p.value > 0) p.value.toString() else "", Modifier.weight(1f), fontSize = 9.sp, maxLines = 1,
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun BarChart(points: List<ChartPoint>, color: Color, modifier: Modifier = Modifier) {
    val max = (points.maxOfOrNull { it.value } ?: 0).coerceAtLeast(1)
    Column(modifier.fillMaxWidth()) {
        ValueLabels(points)
        Canvas(Modifier.fillMaxWidth().height(100.dp)) {
            val slot = size.width / points.size.coerceAtLeast(1)
            points.forEachIndexed { i, p ->
                val h = size.height * p.value / max
                if (h > 0f) {
                    drawRoundRect(
                        color = color, topLeft = Offset(i * slot + slot * 0.18f, size.height - h),
                        size = Size(slot * 0.64f, h), cornerRadius = CornerRadius(4.dp.toPx()),
                    )
                }
            }
        }
        AxisLabels(points)
    }
}

@Composable
fun LineChart(points: List<ChartPoint>, color: Color, modifier: Modifier = Modifier) {
    val max = (points.maxOfOrNull { it.value } ?: 0).coerceAtLeast(1)
    Column(modifier.fillMaxWidth()) {
        ValueLabels(points)
        Canvas(Modifier.fillMaxWidth().height(100.dp)) {
            val slot = size.width / points.size.coerceAtLeast(1)
            val pad = 6.dp.toPx()
            fun pt(i: Int): Offset =
                Offset(i * slot + slot / 2, pad + (size.height - 2 * pad) * (1f - points[i].value.toFloat() / max))
            if (points.size > 1) {
                val path = Path()
                points.indices.forEach { i -> if (i == 0) path.moveTo(pt(i).x, pt(i).y) else path.lineTo(pt(i).x, pt(i).y) }
                drawPath(path, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            }
            points.indices.forEach { i -> drawCircle(color, radius = 4.dp.toPx(), center = pt(i)) }
        }
        AxisLabels(points)
    }
}
