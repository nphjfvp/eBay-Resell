package de.dealcheck.app

import android.app.Application
import androidx.room.Room
import de.dealcheck.app.analysis.OpenRouterClient
import de.dealcheck.app.data.AnalysisRepository
import de.dealcheck.app.data.AppDatabase
import de.dealcheck.app.data.ImageStore
import de.dealcheck.app.data.SettingsRepository

class DealCheckApp : Application() {
    lateinit var settings: SettingsRepository
        private set
    lateinit var images: ImageStore
        private set
    lateinit var repository: AnalysisRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        images = ImageStore(this)
        val db = Room.databaseBuilder(this, AppDatabase::class.java, "dealcheck.db").build()
        repository = AnalysisRepository(db.analysisDao(), images, settings, OpenRouterClient())
    }
}
