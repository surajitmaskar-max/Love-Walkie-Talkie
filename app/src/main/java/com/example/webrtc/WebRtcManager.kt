package com.example.webrtc

import android.content.Context
import android.util.Log
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule

class WebRtcManager(private val context: Context) {

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null

    var onLocalIceCandidate: ((IceCandidate) -> Unit)? = null
    var onConnectionChange: ((PeerConnection.PeerConnectionState) -> Unit)? = null
    var onRemoteAudioTrackReceived: ((AudioTrack) -> Unit)? = null

    init {
        initializeFactory()
    }

    private fun initializeFactory() {
        try {
            val initOptions = PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(initOptions)

            val audioDeviceModule = JavaAudioDeviceModule.builder(context)
                .setUseHardwareAcousticEchoCanceler(true)
                .setUseHardwareNoiseSuppressor(true)
                .createAudioDeviceModule()

            val options = PeerConnectionFactory.Options()

            peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setAudioDeviceModule(audioDeviceModule)
                .createPeerConnectionFactory()

            createLocalAudioTrack()
            Log.d(TAG, "WebRTC Factory and local audio track initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize WebRTC factory", e)
        }
    }

    private fun createLocalAudioTrack() {
        val factory = peerConnectionFactory ?: return

        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        }

        localAudioSource = factory.createAudioSource(audioConstraints)
        localAudioTrack = factory.createAudioTrack(LOCAL_AUDIO_TRACK_ID, localAudioSource).apply {
            // Default muted for Push-to-Talk; only active when button pressed or hands-free
            setEnabled(false)
        }
    }

    fun setupPeerConnection(
        customTurnUrl: String = "",
        turnUser: String = "",
        turnPass: String = ""
    ) {
        closePeerConnection()

        val factory = peerConnectionFactory ?: run {
            Log.e(TAG, "PeerConnectionFactory is null")
            return
        }

        val iceServers = mutableListOf<PeerConnection.IceServer>(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer()
        )

        if (customTurnUrl.isNotBlank()) {
            val turnBuilder = PeerConnection.IceServer.builder(customTurnUrl)
            if (turnUser.isNotBlank()) {
                turnBuilder.setUsername(turnUser)
            }
            if (turnPass.isNotBlank()) {
                turnBuilder.setPassword(turnPass)
            }
            iceServers.add(turnBuilder.createIceServer())
        }

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                Log.d(TAG, "New local ICE candidate discovered: ${candidate.sdpMid}")
                onLocalIceCandidate?.invoke(candidate)
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                Log.d(TAG, "PeerConnection state changed: $newState")
                onConnectionChange?.invoke(newState)
            }

            override fun onTrack(transceiver: org.webrtc.RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                if (track is AudioTrack) {
                    Log.d(TAG, "Remote AudioTrack received!")
                    track.setEnabled(true)
                    onRemoteAudioTrackReceived?.invoke(track)
                }
            }

            override fun onSignalingChange(state: PeerConnection.SignalingState) {
                Log.d(TAG, "SignalingState: $state")
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                Log.d(TAG, "IceConnectionState: $state")
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {}

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
                Log.d(TAG, "IceGatheringState: $state")
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

            override fun onAddStream(stream: MediaStream) {
                if (stream.audioTracks.isNotEmpty()) {
                    val track = stream.audioTracks[0]
                    track.setEnabled(true)
                    onRemoteAudioTrackReceived?.invoke(track)
                }
            }

            override fun onRemoveStream(stream: MediaStream) {}

            override fun onDataChannel(dataChannel: DataChannel) {}

            override fun onRenegotiationNeeded() {
                Log.d(TAG, "Renegotiation needed")
            }

            override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {
                val track = receiver.track()
                if (track is AudioTrack) {
                    track.setEnabled(true)
                    onRemoteAudioTrackReceived?.invoke(track)
                }
            }
        })

        // Add local audio track to connection
        localAudioTrack?.let { track ->
            peerConnection?.addTrack(track, listOf("love_audio_stream"))
            Log.d(TAG, "Added local audio track to peer connection")
        }
    }

    fun createOffer(onSdpReady: (SessionDescription) -> Unit) {
        val pc = peerConnection ?: return
        val sdpConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        pc.createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(desc: SessionDescription) {
                Log.d(TAG, "Offer created successfully. Setting local description...")
                pc.setLocalDescription(object : SdpObserverAdapter() {
                    override fun onSetSuccess() {
                        Log.d(TAG, "Local description (Offer) set successfully")
                        onSdpReady(desc)
                    }
                }, desc)
            }
        }, sdpConstraints)
    }

    fun handleRemoteOffer(offerSdp: String, onAnswerReady: (SessionDescription) -> Unit) {
        val pc = peerConnection ?: return
        val desc = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
        pc.setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() {
                Log.d(TAG, "Remote description (Offer) set successfully. Creating Answer...")
                val sdpConstraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
                }
                pc.createAnswer(object : SdpObserverAdapter() {
                    override fun onCreateSuccess(desc: SessionDescription) {
                        Log.d(TAG, "Answer created. Setting local description...")
                        pc.setLocalDescription(object : SdpObserverAdapter() {
                            override fun onSetSuccess() {
                                Log.d(TAG, "Local description (Answer) set successfully")
                                onAnswerReady(desc)
                            }
                        }, desc)
                    }
                }, sdpConstraints)
            }
        }, desc)
    }

    fun handleRemoteAnswer(answerSdp: String) {
        val pc = peerConnection ?: return
        val desc = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
        pc.setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() {
                Log.d(TAG, "Remote description (Answer) applied successfully")
            }
        }, desc)
    }

    fun addRemoteIceCandidate(sdpMid: String?, sdpMLineIndex: Int?, candidate: String) {
        val pc = peerConnection ?: return
        try {
            val iceCandidate = IceCandidate(sdpMid ?: "0", sdpMLineIndex ?: 0, candidate)
            pc.addIceCandidate(iceCandidate)
            Log.d(TAG, "Remote ICE candidate added successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add remote ICE candidate", e)
        }
    }

    fun setMicrophoneEnabled(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
        Log.d(TAG, "Microphone transmit enabled: $enabled")
    }

    fun closePeerConnection() {
        try {
            peerConnection?.dispose()
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing peer connection", e)
        }
        peerConnection = null
    }

    fun release() {
        closePeerConnection()
        try {
            localAudioTrack?.dispose()
            localAudioSource?.dispose()
            peerConnectionFactory?.dispose()
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing WebRTC components", e)
        }
        localAudioTrack = null
        localAudioSource = null
        peerConnectionFactory = null
    }

    private open class SdpObserverAdapter : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {
            Log.e(TAG, "SDP onCreateFailure: $error")
        }
        override fun onSetFailure(error: String?) {
            Log.e(TAG, "SDP onSetFailure: $error")
        }
    }

    companion object {
        private const val TAG = "WebRtcManager"
        private const val LOCAL_AUDIO_TRACK_ID = "love_walkie_talkie_audio_track"
    }
}
