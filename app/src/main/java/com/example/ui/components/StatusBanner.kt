package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SessionState
import com.example.data.model.SpeakingState
import com.example.ui.theme.RosePrimary
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusConnecting
import com.example.ui.theme.StatusOffline
import com.example.ui.theme.StatusPartnerSpeaking

@Composable
fun StatusBanner(
    sessionState: SessionState,
    speakingState: SpeakingState,
    partnerName: String,
    modifier: Modifier = Modifier
) {
    val statusColor by animateColorAsState(
        targetValue = when {
            speakingState == SpeakingState.YOU_ARE_SPEAKING -> RosePrimary
            speakingState == SpeakingState.PARTNER_IS_SPEAKING -> StatusPartnerSpeaking
            sessionState == SessionState.VOICE_CONNECTED -> StatusConnected
            sessionState == SessionState.CONNECTING_VOICE ||
                sessionState == SessionState.CONNECTING_SERVER ||
                sessionState == SessionState.RECONNECTING -> StatusConnecting
            else -> StatusOffline
        },
        label = "status_color"
    )

    val (primaryText, secondaryText) = when {
        speakingState == SpeakingState.YOU_ARE_SPEAKING ->
            Pair("You are speaking", "তুমি কথা বলছো...")
        speakingState == SpeakingState.PARTNER_IS_SPEAKING ->
            Pair("$partnerName is speaking", "$partnerName কথা বলছে...")
        sessionState == SessionState.VOICE_CONNECTED ->
            Pair("Connected with $partnerName", "সংযুক্ত • প্রস্তুত")
        sessionState == SessionState.CONNECTING_VOICE ->
            Pair("Connecting Voice...", "ভয়েস লিংক তৈরি হচ্ছে...")
        sessionState == SessionState.CONNECTING_SERVER ->
            Pair("Connecting to Server...", "সার্ভারে কানেক্ট হচ্ছে...")
        sessionState == SessionState.READY_WAITING_PARTNER ->
            Pair("Waiting for $partnerName to open app", "পার্টনারের অপেক্ষায়...")
        sessionState == SessionState.ROOM_CREATED_WAITING ->
            Pair("Waiting for partner to join code", "কোড দিয়ে যুক্ত হওয়ার অপেক্ষা")
        sessionState == SessionState.RECONNECTING ->
            Pair("Reconnecting...", "পুনরায় চেষ্টা করা হচ্ছে...")
        else ->
            Pair("Offline", "অফলাইন")
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("status_banner")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Pulsing dot indicator
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = primaryText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text = secondaryText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }

            // Right status icon
            Icon(
                imageVector = when {
                    speakingState != SpeakingState.IDLE -> Icons.Default.GraphicEq
                    sessionState == SessionState.VOICE_CONNECTED -> Icons.Default.Favorite
                    sessionState == SessionState.ERROR -> Icons.Default.WifiOff
                    else -> Icons.Default.Wifi
                },
                contentDescription = "Status Icon",
                tint = statusColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
