package com.hearingaid.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.hearingaid.app.R
import com.hearingaid.app.dsp.Bands
import com.hearingaid.app.model.Audiogram

private const val TOP_DBFS = -100f
private const val BOTTOM_DBFS = 0f

/** Clinical layout: better hearing at the top, right ear O in red, left ear X in blue. */
@Composable
fun AudiogramChart(audiogram: Audiogram, modifier: Modifier = Modifier) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(200.dp)) {
            val stepX = size.width / Bands.COUNT
            fun x(band: Int) = stepX * (band + 0.5f)
            fun y(db: Float) = ((db - TOP_DBFS) / (BOTTOM_DBFS - TOP_DBFS)).coerceIn(0f, 1f) * size.height

            for (db in TOP_DBFS.toInt()..BOTTOM_DBFS.toInt() step 20) {
                drawLine(grid, Offset(0f, y(db.toFloat())), Offset(size.width, y(db.toFloat())), 1f)
            }
            for (b in 0 until Bands.COUNT) drawLine(grid, Offset(x(b), 0f), Offset(x(b), size.height), 1f)

            drawEar(audiogram.right, RightEarColor, ::x, ::y) { center ->
                drawCircle(RightEarColor, 9.dp.toPx(), center, style = Stroke(2.5.dp.toPx()))
            }
            drawEar(audiogram.left, LeftEarColor, ::x, ::y) { center ->
                val r = 8.dp.toPx()
                val w = 2.5.dp.toPx()
                drawLine(LeftEarColor, center + Offset(-r, -r), center + Offset(r, r), w)
                drawLine(LeftEarColor, center + Offset(-r, r), center + Offset(r, -r), w)
            }
        }
        // Audiograms always run low to high pitch from left to right, whatever the reading direction.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.fillMaxWidth()) {
                for (b in 0 until Bands.COUNT) {
                    Text(
                        bandLabel(b),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.legend_right), color = RightEarColor, style = MaterialTheme.typography.labelMedium)
            Text(stringResource(R.string.legend_left), color = LeftEarColor, style = MaterialTheme.typography.labelMedium)
        }
        Text(
            stringResource(R.string.chart_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun DrawScope.drawEar(
    thresholds: List<Float>,
    color: Color,
    x: (Int) -> Float,
    y: (Float) -> Float,
    marker: DrawScope.(Offset) -> Unit,
) {
    var previous: Offset? = null
    thresholds.forEachIndexed { band, db ->
        if (db.isNaN()) {
            previous = null
            return@forEachIndexed
        }
        val point = Offset(x(band), y(db))
        previous?.let { drawLine(color, it, point, 2.dp.toPx()) }
        marker(point)
        previous = point
    }
}
