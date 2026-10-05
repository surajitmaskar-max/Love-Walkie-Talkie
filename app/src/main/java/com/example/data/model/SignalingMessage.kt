package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SignalingPayload(
    val type: String,
    val roomCode: String? = null,
    val senderId: String? = null,
    val displayName: String? = null,
    val sdp: String? = null,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int? = null,
    val candidate: String? = null,
    val isSpeaking: Boolean? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYPE_CREATE_ROOM = "create_room"
        const val TYPE_ROOM_CREATED = "room_created"
        const val TYPE_JOIN_ROOM = "join_room"
        const val TYPE_ROOM_JOINED = "room_joined"
        const val TYPE_PARTNER_JOINED = "partner_joined"
        const val TYPE_PARTNER_LEFT = "partner_left"
        const val TYPE_OFFER = "offer"
        const val TYPE_ANSWER = "answer"
        const val TYPE_ICE_CANDIDATE = "ice_candidate"
        const val TYPE_PTT_STATUS = "ptt_status"
        const val TYPE_PING = "ping"
        const val TYPE_PONG = "pong"
        const val TYPE_ERROR = "error"
        const val TYPE_UNPAIR = "unpair"
    }
}
