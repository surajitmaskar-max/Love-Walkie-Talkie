package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.repository.WalkieTalkieRepository

class LoveWalkieTalkieApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var repository: WalkieTalkieRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getDatabase(this)
        repository = WalkieTalkieRepository(database.pairingDao())
    }

    companion object {
        lateinit var instance: LoveWalkieTalkieApp
            private set
    }
}
