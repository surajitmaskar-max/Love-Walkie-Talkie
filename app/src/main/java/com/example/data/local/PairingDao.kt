package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PairingDao {

    @Query("SELECT * FROM pairing_session WHERE id = 1 LIMIT 1")
    fun getPairingSession(): Flow<PairingSessionEntity?>

    @Query("SELECT * FROM pairing_session WHERE id = 1 LIMIT 1")
    suspend fun getPairingSessionSync(): PairingSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(session: PairingSessionEntity)

    @Query("UPDATE pairing_session SET isPaired = 0, roomCode = NULL, role = NULL WHERE id = 1")
    suspend fun unpair()

    @Query("UPDATE pairing_session SET handsFreeEnabled = :enabled WHERE id = 1")
    suspend fun updateHandsFree(enabled: Boolean)

    @Query("UPDATE pairing_session SET speakerphoneEnabled = :enabled WHERE id = 1")
    suspend fun updateSpeakerphone(enabled: Boolean)

    @Query("UPDATE pairing_session SET vibrationEnabled = :enabled WHERE id = 1")
    suspend fun updateVibration(enabled: Boolean)

    @Query("UPDATE pairing_session SET rogerBeepEnabled = :enabled WHERE id = 1")
    suspend fun updateRogerBeep(enabled: Boolean)

    @Query("UPDATE pairing_session SET userName = :name WHERE id = 1")
    suspend fun updateUserName(name: String)

    @Query("UPDATE pairing_session SET partnerName = :name WHERE id = 1")
    suspend fun updatePartnerName(name: String)

    @Query("UPDATE pairing_session SET serverUrl = :url WHERE id = 1")
    suspend fun updateServerUrl(url: String)
}
