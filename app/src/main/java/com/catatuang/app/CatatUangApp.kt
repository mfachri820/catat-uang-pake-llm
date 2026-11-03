package com.catatuang.app

import android.app.Application
import com.catatuang.app.ai.JevCategorizer
import com.catatuang.app.data.AppDatabase
import com.catatuang.app.data.Repository
import com.catatuang.app.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Tiny manual DI container; the app is small enough not to need Hilt. */
class CatatUangApp : Application() {
    lateinit var repository: Repository
        private set
    lateinit var settings: Settings
        private set
    lateinit var categorizer: JevCategorizer
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        repository = Repository(this, AppDatabase.get(this))
        categorizer = JevCategorizer(settings)
        appScope.launch { repository.seedIfEmpty() }
    }
}
