package com.example.vaanivideo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.vaanivideo.data.model.PageTurnItem
import com.example.vaanivideo.timeline.TimelineUtils

@Composable
fun TimelineVisualizer(
    pages: List<PageTurnItem>,
    currentPositionMs: Long,
    totalDurationMs: Long,
    selectedPageNumber: Int,
    onSeek: (Long) -> Unit,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val durationSafe = totalDurationMs.coerceAtLeast(1L)
    val colorScheme = MaterialTheme.colorScheme

    val pageColors = listOf(
        Color(0xFF3F51B5),
        Color(0xFF009688),
        Color(0xFFFF9800),
        Color(0xFFE91E63),
        Color(0xFF673AB7),
        Color(0xFF00BCD4),
        Color(0xFF4CAF50),
        Color(0xFFFF5722)
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .testTag("timeline_visualizer_canvas")
                .pointerInput(pages, totalDurationMs) {
                    detectTapGestures { offset ->
                        val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                        val seekMs = (ratio * durationSafe).toLong()
                        onSeek(seekMs)

                        // Also select corresponding page
                        val clickedPage = pages.find { seekMs in it.startMs..it.endMs }
                        if (clickedPage != null) {
                            onSelectPage(clickedPage.pageNumber)
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                // Draw time tick marks
                val tickStepMs = when {
                    durationSafe < 60_000L -> 10_000L
                    durationSafe < 300_000L -> 30_000L
                    durationSafe < 900_000L -> 60_000L
                    else -> 120_000L
                }

                var t = 0L
                while (t <= durationSafe) {
                    val x = (t.toFloat() / durationSafe.toFloat()) * canvasWidth
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.4f),
                        start = Offset(x, 0f),
                        end = Offset(x, 14f),
                        strokeWidth = 1.5f
                    )
                    t += tickStepMs
                }

                // Draw page segments
                val trackY = 18f
                val trackH = canvasHeight - 22f

                for (page in pages) {
                    val startX = (page.startMs.toFloat() / durationSafe.toFloat()) * canvasWidth
                    val endX = (page.endMs.toFloat() / durationSafe.toFloat()) * canvasWidth
                    val segW = (endX - startX).coerceAtLeast(2f)

                    val isSelected = page.pageNumber == selectedPageNumber
                    val baseColor = pageColors[(page.pageNumber - 1) % pageColors.size]
                    val fillColor = if (isSelected) baseColor else baseColor.copy(alpha = 0.7f)

                    drawRect(
                        color = fillColor,
                        topLeft = Offset(startX, trackY),
                        size = Size(segW - 1f, trackH)
                    )

                    // Page boundary divider
                    drawLine(
                        color = Color.White.copy(alpha = 0.9f),
                        start = Offset(startX, trackY),
                        end = Offset(startX, trackY + trackH),
                        strokeWidth = 2f
                    )

                    // Draw Page Label if segment is wide enough
                    if (segW > 35f) {
                        drawContext.canvas.nativeCanvas.apply {
                            val paint = android.graphics.Paint().apply {
                                color = android.graphics.Color.WHITE
                                textSize = 26f
                                isFakeBoldText = isSelected
                                textAlign = android.graphics.Paint.Align.CENTER
                            }
                            drawText(
                                "P${page.pageNumber}",
                                startX + segW / 2f,
                                trackY + trackH / 2f + 9f,
                                paint
                            )
                        }
                    }
                }

                // Draw Current Playhead Cursor
                val cursorX = (currentPositionMs.toFloat() / durationSafe.toFloat()) * canvasWidth
                drawLine(
                    color = Color.Red,
                    start = Offset(cursorX, 0f),
                    end = Offset(cursorX, canvasHeight),
                    strokeWidth = 3f
                )
                drawCircle(
                    color = Color.Red,
                    radius = 6f,
                    center = Offset(cursorX, 6f)
                )
            }
        }

        // Time indicator row
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = "00:00.000",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = TimelineUtils.formatMs(totalDurationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(androidx.compose.ui.Alignment.CenterEnd)
            )
        }
    }
}
