package com.example.data.repository

import com.example.data.local.PairingDao
import com.example.data.local.PairingSessionEntity
import kotlinx.coroutines.flow.Flow
import java.security.SecureRandom

class WalkieTalkieRepository(private val pairingDao: PairingDao) {

    val pairingSession: Flow<PairingSessionEntity?> = pairingDao.getPairingSession()

    suspend fun getPairingSessionSync(): PairingSessionEntity? = pairingDao.getPairingSessionSync()

    suspend fun savePairingSession(
        roomCode: String,
        role: String,
        userName: String = "Me",
        partnerName: String = "My Love"
    ) {
        val existing = pairingDao.getPairingSessionSync() ?: PairingSessionEntity()
        pairingDao.insertOrUpdate(
            existing.copy(
                roomCode = roomCode,
                role = role,
                isPaired = true,
                userName = userName,
                partnerName = partnerName,
                lastConnectedTimestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun unpair() {
        pairingDao.unpair()
    }

    suspend fun updateHandsFree(enabled: Boolean) {
        pairingDao.updateHandsFree(enabled)
    }

    suspend fun updateSpeakerphone(enabled: Boolean) {
        pairingDao.updateSpeakerphone(enabled)
    }

    suspend fun updateVibration(enabled: Boolean) {
        pairingDao.updateVibration(enabled)
    }

    suspend fun updateRogerBeep(enabled: Boolean) {
        pairingDao.updateRogerBeep(enabled)
    }

    suspend fun updateUserName(name: String) {
        pairingDao.updateUserName(name)
    }

    suspend fun updatePartnerName(name: String) {
        pairingDao.updatePartnerName(name)
    }

    suspend fun updateServerUrl(url: String) {
        pairingDao.updateServerUrl(url)
    }

    companion object {
        fun generateSecureRoomCode(): String {
            val random = SecureRandom()
            val number = 1000 + random.nextInt(9000)
            val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ"
            val char1 = alphabet[random.nextInt(alphabet.length)]
            val char2 = alphabet[random.nextInt(alphabet.length)]
            return "$char1$char2-$number"
        }
    }
}
