package com.example.data.model

enum class SessionState {
    NOT_PAIRED,             // First launch / unshared
    CONNECTING_SERVER,      // Connecting to signaling server
    ROOM_CREATED_WAITING,   // Host waiting for partner to enter code
    ROOM_JOINING,           // Guest attempting to join code
    READY_WAITING_PARTNER,  // Connected to room, partner offline
    CONNECTING_VOICE,       // Exchanging WebRTC SDP & ICE
    VOICE_CONNECTED,        // WebRTC peer connection live & active
    RECONNECTING,           // Network lost, auto-reconnecting
    ERROR                   // Specific failure state
}

enum class SpeakingState {
    IDLE,
    YOU_ARE_SPEAKING,
    PARTNER_IS_SPEAKING
}

data class WalkieTalkieUiState(
    val sessionState: SessionState = SessionState.NOT_PAIRED,
    val speakingState: SpeakingState = SpeakingState.IDLE,
    val isPaired: Boolean = false,
    val roomCode: String? = null,
    val role: String? = null, // "HOST" or "GUEST"
    val userName: String = "Me",
    val partnerName: String = "My Love",
    val isPartnerOnline: Boolean = false,
    val isMicrophonePermissionGranted: Boolean = false,
    val isNotificationPermissionGranted: Boolean = false,
    val isHandsFreeMode: Boolean = false,
    val isSpeakerphoneOn: Boolean = true,
    val isVibrationEnabled: Boolean = true,
    val isRogerBeepEnabled: Boolean = true,
    val serverUrl: String = "wss://ais-pre-3fgzfrwrvzhlvtwu7dgi7h-530593896819.asia-east1.run.app/ws",
    val errorMessage: String? = null,
    val audioVolumeLevel: Float = 0f,
    val isServiceRunning: Boolean = false
)
