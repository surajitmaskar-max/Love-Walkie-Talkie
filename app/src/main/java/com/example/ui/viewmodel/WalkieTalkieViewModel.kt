package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.SessionState
import com.example.data.model.SignalingPayload
import com.example.data.model.SpeakingState
import com.example.data.model.WalkieTalkieUiState
import com.example.data.repository.WalkieTalkieRepository
import com.example.service.WalkieTalkieService
import com.example.webrtc.AudioDeviceManager
import com.example.webrtc.SignalingClient
import com.example.webrtc.WebRtcManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.webrtc.PeerConnection
import java.util.UUID

class WalkieTalkieViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val database = AppDatabase.getDatabase(context)
    private val repository = WalkieTalkieRepository(database.pairingDao())

    private val audioDeviceManager = AudioDeviceManager(context)
    private val webRtcManager = WebRtcManager(context)
    private val signalingClient = SignalingClient(viewModelScope)

    private val myUserId = UUID.randomUUID().toString().substring(0, 8)

    private val _uiState = MutableStateFlow(WalkieTalkieUiState())
    val uiState: StateFlow<WalkieTalkieUiState> = _uiState.asStateFlow()

    init {
        setupSignalingListeners()
        setupWebRtcListeners()
        observeStoredPairingSession()
    }

    private fun observeStoredPairingSession() {
        viewModelScope.launch {
            repository.pairingSession.collect { session ->
                if (session != null && session.isPaired && !session.roomCode.isNullOrBlank()) {
                    _uiState.update { current ->
                        current.copy(
                            isPaired = true,
                            roomCode = session.roomCode,
                            role = session.role,
                            userName = session.userName,
                            partnerName = session.partnerName,
                            serverUrl = session.serverUrl,
                            isHandsFreeMode = session.handsFreeEnabled,
                            isSpeakerphoneOn = session.speakerphoneEnabled,
                            isVibrationEnabled = session.vibrationEnabled,
                            isRogerBeepEnabled = session.rogerBeepEnabled
                        )
                    }
                    audioDeviceManager.configureAudioForVoiceCall(session.speakerphoneEnabled)
                    // If paired, automatically initiate signaling connection
                    connectToSignalingServer(session.serverUrl, session.roomCode, session.role ?: "HOST")
                } else {
                    _uiState.update { current ->
                        current.copy(
                            isPaired = false,
                            sessionState = SessionState.NOT_PAIRED,
                            roomCode = null,
                            role = null
                        )
                    }
                }
            }
        }
    }

    private fun setupSignalingListeners() {
        viewModelScope.launch {
            signalingClient.isConnected.collect { connected ->
                if (connected) {
                    val code = _uiState.value.roomCode
                    val role = _uiState.value.role
                    if (!code.isNullOrBlank()) {
                        // Re-join room on connect
                        signalingClient.send(
                            SignalingPayload(
                                type = SignalingPayload.TYPE_JOIN_ROOM,
                                roomCode = code,
                                senderId = myUserId,
                                displayName = _uiState.value.userName
                            )
                        )
                    }
                } else {
                    if (_uiState.value.isPaired) {
                        _uiState.update { it.copy(sessionState = SessionState.RECONNECTING, isPartnerOnline = false) }
                    }
                }
            }
        }

        viewModelScope.launch {
            signalingClient.messages.collect { payload ->
                handleSignalingMessage(payload)
            }
        }
    }

    private fun setupWebRtcListeners() {
        webRtcManager.onLocalIceCandidate = { candidate ->
            signalingClient.send(
                SignalingPayload(
                    type = SignalingPayload.TYPE_ICE_CANDIDATE,
                    roomCode = _uiState.value.roomCode,
                    senderId = myUserId,
                    sdpMid = candidate.sdpMid,
                    sdpMLineIndex = candidate.sdpMLineIndex,
                    candidate = candidate.sdp
                )
            )
        }

        webRtcManager.onConnectionChange = { state ->
            Log.d(TAG, "WebRTC connection state: $state")
            when (state) {
                PeerConnection.PeerConnectionState.CONNECTED -> {
                    _uiState.update { it.copy(sessionState = SessionState.VOICE_CONNECTED, errorMessage = null) }
                    WalkieTalkieService.startService(context, _uiState.value.partnerName)
                }
                PeerConnection.PeerConnectionState.DISCONNECTED,
                PeerConnection.PeerConnectionState.FAILED -> {
                    _uiState.update { it.copy(sessionState = SessionState.RECONNECTING) }
                }
                PeerConnection.PeerConnectionState.CONNECTING -> {
                    _uiState.update { it.copy(sessionState = SessionState.CONNECTING_VOICE) }
                }
                else -> {}
            }
        }
    }

    private fun connectToSignalingServer(url: String, roomCode: String, role: String) {
        _uiState.update { it.copy(sessionState = SessionState.CONNECTING_SERVER, errorMessage = null) }
        signalingClient.connect(url)
    }

    fun createPrivateRoom() {
        val newCode = WalkieTalkieRepository.generateSecureRoomCode()
        val url = _uiState.value.serverUrl
        _uiState.update {
            it.copy(
                sessionState = SessionState.ROOM_CREATED_WAITING,
                roomCode = newCode,
                role = "HOST",
                errorMessage = null
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            repository.savePairingSession(
                roomCode = newCode,
                role = "HOST",
                userName = _uiState.value.userName,
                partnerName = _uiState.value.partnerName
            )
        }

        signalingClient.connect(url)
        viewModelScope.launch {
            signalingClient.send(
                SignalingPayload(
                    type = SignalingPayload.TYPE_CREATE_ROOM,
                    roomCode = newCode,
                    senderId = myUserId,
                    displayName = _uiState.value.userName
                )
            )
        }
    }

    fun joinPrivateRoom(inputCode: String) {
        val formattedCode = inputCode.trim().uppercase()
        if (formattedCode.length < 4) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid pairing code (e.g. LV-8429)") }
            return
        }

        val url = _uiState.value.serverUrl
        _uiState.update {
            it.copy(
                sessionState = SessionState.ROOM_JOINING,
                roomCode = formattedCode,
                role = "GUEST",
                errorMessage = null
            )
        }

        signalingClient.connect(url)
        viewModelScope.launch {
            signalingClient.send(
                SignalingPayload(
                    type = SignalingPayload.TYPE_JOIN_ROOM,
                    roomCode = formattedCode,
                    senderId = myUserId,
                    displayName = _uiState.value.userName
                )
            )
        }
    }

    private fun handleSignalingMessage(payload: SignalingPayload) {
        when (payload.type) {
            SignalingPayload.TYPE_ROOM_CREATED -> {
                Log.d(TAG, "Room created on server: ${payload.roomCode}")
                _uiState.update { it.copy(sessionState = SessionState.ROOM_CREATED_WAITING) }
            }

            SignalingPayload.TYPE_ROOM_JOINED -> {
                Log.d(TAG, "Successfully joined room: ${payload.roomCode}")
                val code = payload.roomCode ?: _uiState.value.roomCode ?: ""
                viewModelScope.launch(Dispatchers.IO) {
                    repository.savePairingSession(
                        roomCode = code,
                        role = "GUEST",
                        userName = _uiState.value.userName,
                        partnerName = _uiState.value.partnerName
                    )
                }
                _uiState.update {
                    it.copy(
                        isPaired = true,
                        sessionState = SessionState.CONNECTING_VOICE,
                        roomCode = code,
                        isPartnerOnline = true
                    )
                }
                initiateWebRtcConnection(isCaller = false)
            }

            SignalingPayload.TYPE_PARTNER_JOINED -> {
                Log.d(TAG, "Partner joined the room!")
                _uiState.update {
                    it.copy(
                        isPartnerOnline = true,
                        sessionState = SessionState.CONNECTING_VOICE
                    )
                }
                // Host initiates the WebRTC offer
                if (_uiState.value.role == "HOST") {
                    initiateWebRtcConnection(isCaller = true)
                }
            }

            SignalingPayload.TYPE_PARTNER_LEFT -> {
                Log.d(TAG, "Partner left or disconnected")
                _uiState.update {
                    it.copy(
                        isPartnerOnline = false,
                        sessionState = SessionState.READY_WAITING_PARTNER,
                        speakingState = SpeakingState.IDLE
                    )
                }
                webRtcManager.closePeerConnection()
            }

            SignalingPayload.TYPE_OFFER -> {
                Log.d(TAG, "Received WebRTC Offer from partner")
                val offerSdp = payload.sdp ?: return
                webRtcManager.handleRemoteOffer(offerSdp) { answerDesc ->
                    signalingClient.send(
                        SignalingPayload(
                            type = SignalingPayload.TYPE_ANSWER,
                            roomCode = _uiState.value.roomCode,
                            senderId = myUserId,
                            sdp = answerDesc.description
                        )
                    )
                }
            }

            SignalingPayload.TYPE_ANSWER -> {
                Log.d(TAG, "Received WebRTC Answer from partner")
                val answerSdp = payload.sdp ?: return
                webRtcManager.handleRemoteAnswer(answerSdp)
            }

            SignalingPayload.TYPE_ICE_CANDIDATE -> {
                val candidate = payload.candidate ?: return
                webRtcManager.addRemoteIceCandidate(
                    sdpMid = payload.sdpMid,
                    sdpMLineIndex = payload.sdpMLineIndex,
                    candidate = candidate
                )
            }

            SignalingPayload.TYPE_PTT_STATUS -> {
                val partnerSpeaking = payload.isSpeaking ?: false
                if (partnerSpeaking) {
                    _uiState.update { it.copy(speakingState = SpeakingState.PARTNER_IS_SPEAKING) }
                    WalkieTalkieService.updateService(context, _uiState.value.partnerName, isSpeaking = false)
                } else {
                    if (_uiState.value.speakingState == SpeakingState.PARTNER_IS_SPEAKING) {
                        _uiState.update { it.copy(speakingState = SpeakingState.IDLE) }
                        if (_uiState.value.isRogerBeepEnabled) {
                            audioDeviceManager.playRogerBeep()
                        }
                    }
                }
            }

            SignalingPayload.TYPE_ERROR -> {
                val msg = when (payload.errorCode) {
                    "ROOM_FULL" -> "This private room is already full! Love Walkie Talkie allows exactly two people."
                    "ROOM_NOT_FOUND" -> "Room code not found. Please double-check the code with your partner."
                    else -> payload.errorMessage ?: "Communication error occurred"
                }
                _uiState.update { it.copy(errorMessage = msg, sessionState = SessionState.ERROR) }
            }
        }
    }

    private fun initiateWebRtcConnection(isCaller: Boolean) {
        webRtcManager.setupPeerConnection()
        if (isCaller) {
            webRtcManager.createOffer { offerDesc ->
                signalingClient.send(
                    SignalingPayload(
                        type = SignalingPayload.TYPE_OFFER,
                        roomCode = _uiState.value.roomCode,
                        senderId = myUserId,
                        sdp = offerDesc.description
                    )
                )
            }
        }
    }

    fun onPttPressed() {
        if (!_uiState.value.isMicrophonePermissionGranted) {
            _uiState.update { it.copy(errorMessage = "Microphone permission is required to talk.") }
            return
        }

        // Half-duplex rule: Do not allow talking over partner unless hands-free
        if (_uiState.value.speakingState == SpeakingState.PARTNER_IS_SPEAKING && !_uiState.value.isHandsFreeMode) {
            audioDeviceManager.triggerHapticFeedback(60)
            return
        }

        _uiState.update { it.copy(speakingState = SpeakingState.YOU_ARE_SPEAKING) }
        webRtcManager.setMicrophoneEnabled(true)
        if (_uiState.value.isRogerBeepEnabled) {
            audioDeviceManager.playStartTransmitChirp()
        }
        if (_uiState.value.isVibrationEnabled) {
            audioDeviceManager.triggerHapticFeedback(40)
        }

        signalingClient.send(
            SignalingPayload(
                type = SignalingPayload.TYPE_PTT_STATUS,
                roomCode = _uiState.value.roomCode,
                senderId = myUserId,
                isSpeaking = true
            )
        )
        WalkieTalkieService.updateService(context, _uiState.value.partnerName, isSpeaking = true)
    }

    fun onPttReleased() {
        if (_uiState.value.isHandsFreeMode) return // In hands-free, mic stays on

        if (_uiState.value.speakingState == SpeakingState.YOU_ARE_SPEAKING) {
            webRtcManager.setMicrophoneEnabled(false)
            _uiState.update { it.copy(speakingState = SpeakingState.IDLE) }
            if (_uiState.value.isRogerBeepEnabled) {
                audioDeviceManager.playRogerBeep()
            }
            if (_uiState.value.isVibrationEnabled) {
                audioDeviceManager.triggerHapticFeedback(25)
            }

            signalingClient.send(
                SignalingPayload(
                    type = SignalingPayload.TYPE_PTT_STATUS,
                    roomCode = _uiState.value.roomCode,
                    senderId = myUserId,
                    isSpeaking = false
                )
            )
            WalkieTalkieService.updateService(context, _uiState.value.partnerName, isSpeaking = false)
        }
    }

    fun toggleHandsFree(enabled: Boolean) {
        _uiState.update { it.copy(isHandsFreeMode = enabled) }
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateHandsFree(enabled)
        }
        webRtcManager.setMicrophoneEnabled(enabled)
    }

    fun toggleSpeakerphone(enabled: Boolean) {
        _uiState.update { it.copy(isSpeakerphoneOn = enabled) }
        audioDeviceManager.setSpeakerphone(enabled)
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateSpeakerphone(enabled)
        }
    }

    fun toggleVibration(enabled: Boolean) {
        _uiState.update { it.copy(isVibrationEnabled = enabled) }
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateVibration(enabled)
        }
    }

    fun toggleRogerBeep(enabled: Boolean) {
        _uiState.update { it.copy(isRogerBeepEnabled = enabled) }
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateRogerBeep(enabled)
        }
    }

    fun updateNicknames(userName: String, partnerName: String) {
        _uiState.update { it.copy(userName = userName, partnerName = partnerName) }
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateUserName(userName)
            repository.updatePartnerName(partnerName)
        }
    }

    fun updateServerUrl(url: String) {
        _uiState.update { it.copy(serverUrl = url.trim()) }
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateServerUrl(url.trim())
        }
        val code = _uiState.value.roomCode
        val role = _uiState.value.role ?: "HOST"
        if (!code.isNullOrBlank()) {
            connectToSignalingServer(url.trim(), code, role)
        }
    }

    fun unpairRoom() {
        signalingClient.send(
            SignalingPayload(
                type = SignalingPayload.TYPE_UNPAIR,
                roomCode = _uiState.value.roomCode,
                senderId = myUserId
            )
        )
        webRtcManager.closePeerConnection()
        signalingClient.disconnect()
        WalkieTalkieService.stopService(context)

        viewModelScope.launch(Dispatchers.IO) {
            repository.unpair()
        }

        _uiState.update {
            WalkieTalkieUiState(
                isMicrophonePermissionGranted = it.isMicrophonePermissionGranted,
                isNotificationPermissionGranted = it.isNotificationPermissionGranted,
                serverUrl = it.serverUrl
            )
        }
    }

    fun onMicrophonePermissionResult(granted: Boolean) {
        _uiState.update { it.copy(isMicrophonePermissionGranted = granted) }
        if (!granted) {
            _uiState.update { it.copy(errorMessage = "Microphone access is needed for live push-to-talk.") }
        }
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(isNotificationPermissionGranted = granted) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        WalkieTalkieService.stopService(context)
        audioDeviceManager.release()
        webRtcManager.release()
        signalingClient.disconnect()
    }

    companion object {
        private const val TAG = "WalkieTalkieViewModel"
    }
}
