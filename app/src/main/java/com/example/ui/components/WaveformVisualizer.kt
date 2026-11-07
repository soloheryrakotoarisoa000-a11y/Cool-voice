package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan

@Composable
fun WaveformVisualizer(
    visualizerData: FloatArray,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "idle_wave")
    val idlePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "idle_phase"
    )

    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(surfaceColor)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val centerY = canvasHeight / 2f
            val count = visualizerData.size
            if (count == 0) return@Canvas

            val barSpacing = 4.dp.toPx()
            val totalSpacing = barSpacing * (count - 1)
            val barWidth = ((canvasWidth - totalSpacing) / count).coerceAtLeast(2.dp.toPx())

            val gradientBrush = Brush.verticalGradient(
                colors = listOf(
                    NeonCyan,
                    ElectricViolet,
                    primaryColor
                ),
                startY = 0f,
                endY = canvasHeight
            )

            for (i in 0 until count) {
                val x = i * (barWidth + barSpacing)

                val amplitude = if (isActive) {
                    visualizerData[i]
                } else {
                    // Gentle sinusoidal breathing wave when idle
                    0.08f + 0.05f * kotlin.math.sin(idlePhase + i * 0.25f).toFloat()
                }

                val barHeight = (canvasHeight * amplitude).coerceIn(4.dp.toPx(), canvasHeight - 8.dp.toPx())
                val topY = centerY - barHeight / 2f

                drawRoundRect(
                    brush = gradientBrush,
                    topLeft = Offset(x, topY),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                )
            }
        }
    }
}
