package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.model.SpeakingState
import com.example.ui.theme.RosePrimary
import com.example.ui.theme.StatusPartnerSpeaking
import kotlin.math.sin

@Composable
fun WaveformVisualizer(
    speakingState: SpeakingState,
    modifier: Modifier = Modifier
) {
    val isActive = speakingState != SpeakingState.IDLE
    val waveColor = if (speakingState == SpeakingState.YOU_ARE_SPEAKING) {
        RosePrimary
    } else {
        StatusPartnerSpeaking
    }

    val infiniteTransition = rememberInfiniteTransition(label = "wave_anim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
    ) {
        val barCount = 28
        val spacing = size.width / (barCount * 1.5f)
        val barWidth = spacing * 0.7f
        val centerY = size.height / 2f

        for (i in 0 until barCount) {
            val x = i * (spacing + barWidth) + spacing
            val barHeight = if (isActive) {
                val wave = (sin(phase + (i * 0.45f)) + 1f) / 2f
                (8.dp.toPx() + wave * (size.height - 12.dp.toPx()))
            } else {
                4.dp.toPx()
            }

            drawRoundRect(
                color = if (isActive) waveColor.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.15f),
                topLeft = Offset(x, centerY - barHeight / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
        }
    }
}
