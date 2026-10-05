package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SpeakingState
import com.example.ui.theme.RosePrimary
import com.example.ui.theme.StatusPartnerSpeaking

@Composable
fun PttButton(
    speakingState: SpeakingState,
    isHandsFreeMode: Boolean,
    isEnabled: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isTransmitting = speakingState == SpeakingState.YOU_ARE_SPEAKING
    val isPartnerSpeaking = speakingState == SpeakingState.PARTNER_IS_SPEAKING

    val infiniteTransition = rememberInfiniteTransition(label = "ptt_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_alpha"
    )

    val currentBrush = when {
        !isEnabled -> Brush.linearGradient(listOf(Color(0xFF424242), Color(0xFF212121)))
        isTransmitting -> Brush.radialGradient(listOf(Color(0xFFFF528E), RosePrimary, Color(0xFFC2185B)))
        isPartnerSpeaking -> Brush.radialGradient(listOf(Color(0xFF18FFFF), StatusPartnerSpeaking, Color(0xFF0091EA)))
        isHandsFreeMode -> Brush.radialGradient(listOf(Color(0xFF69F0AE), Color(0xFF00C853), Color(0xFF1B5E20)))
        else -> Brush.radialGradient(listOf(Color(0xFFFF4081), Color(0xFFE91E63), Color(0xFF880E4F)))
    }

    val glowColor = when {
        isTransmitting -> RosePrimary
        isPartnerSpeaking -> StatusPartnerSpeaking
        isHandsFreeMode -> Color(0xFF00E676)
        else -> RosePrimary.copy(alpha = 0.5f)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(240.dp)
    ) {
        // Outer pulsing ring during active transmission or partner speaking
        if (isTransmitting || isPartnerSpeaking) {
            Box(
                modifier = Modifier
                    .size(230.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(glowColor.copy(alpha = ringAlpha))
            )
        }

        // Secondary ambient glow ring
        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(CircleShape)
                .background(glowColor.copy(alpha = 0.15f))
        )

        // Core Interactive Touch Surface
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(175.dp)
                .shadow(
                    elevation = if (isTransmitting) 24.dp else 12.dp,
                    shape = CircleShape,
                    spotColor = glowColor
                )
                .clip(CircleShape)
                .background(currentBrush)
                .testTag("ptt_button")
                .pointerInput(isEnabled, isHandsFreeMode) {
                    if (isEnabled) {
                        if (isHandsFreeMode) {
                            detectTapGestures(
                                onTap = {
                                    if (speakingState == SpeakingState.YOU_ARE_SPEAKING) {
                                        onRelease()
                                    } else {
                                        onPress()
                                    }
                                }
                            )
                        } else {
                            detectTapGestures(
                                onPress = {
                                    onPress()
                                    tryAwaitRelease()
                                    onRelease()
                                }
                            )
                        }
                    }
                }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = when {
                        !isEnabled -> Icons.Default.MicOff
                        isPartnerSpeaking -> Icons.AutoMirrored.Filled.VolumeUp
                        isTransmitting -> Icons.Default.Mic
                        else -> Icons.Default.Mic
                    },
                    contentDescription = "Push to Talk Microphone",
                    tint = Color.White,
                    modifier = Modifier.size(54.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = when {
                        !isEnabled -> "DISABLED"
                        isTransmitting -> "TRANSMITTING"
                        isPartnerSpeaking -> "PARTNER SPEAKING"
                        isHandsFreeMode -> "HANDS FREE"
                        else -> "HOLD TO TALK"
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = when {
                        !isEnabled -> "Offline"
                        isTransmitting -> "Release to listen"
                        isPartnerSpeaking -> "Partner on air"
                        isHandsFreeMode -> "Tap to toggle"
                        else -> "চাপ দিয়ে কথা বলো"
                    },
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
