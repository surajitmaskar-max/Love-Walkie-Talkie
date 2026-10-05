package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pairing_session")
data class PairingSessionEntity(
    @PrimaryKey val id: Int = 1,
    val roomCode: String? = null,
    val role: String? = null, // "HOST" or "GUEST"
    val userName: String = "Me",
    val partnerName: String = "My Love",
    val isPaired: Boolean = false,
    val serverUrl: String = "wss://ais-pre-3fgzfrwrvzhlvtwu7dgi7h-530593896819.asia-east1.run.app/ws",
    val handsFreeEnabled: Boolean = false,
    val speakerphoneEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val rogerBeepEnabled: Boolean = true,
    val customTurnServer: String = "",
    val turnUsername: String = "",
    val turnPassword: String = "",
    val lastConnectedTimestamp: Long = 0L
)
