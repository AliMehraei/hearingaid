package com.hearingaid.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.hearingaid.app.audio.AudioEngine
import com.hearingaid.app.model.HearingSettings
import com.hearingaid.app.model.SettingsRepository
import com.hearingaid.app.update.UpdateManager
import kotlinx.coroutines.flow.StateFlow

/** Process-wide singletons shared by the UI and the foreground service. */
object AppGraph {
    lateinit var engine: AudioEngine
        private set
    lateinit var repository: SettingsRepository
        private set
    lateinit var updates: UpdateManager
        private set

    val settings: StateFlow<HearingSettings> get() = repository.settings

    fun updateSettings(transform: (HearingSettings) -> HearingSettings) {
        engine.applySettings(repository.update(transform))
    }

    internal fun init(app: Application) {
        AppInfo.init(app)
        repository = SettingsRepository(app)
        engine = AudioEngine(app)
        updates = UpdateManager(app, repository)
        engine.applySettings(repository.settings.value)
    }
}

class HearingAidApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        createChannel(this)
    }

    companion object {
        const val CHANNEL_ID = "hearing"

        /** Re-creating an existing channel just renames it, so this is also called after a language change. */
        fun createChannel(context: Context) {
            val name = AppLocale.wrap(context.applicationContext).getString(R.string.channel_name)
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW),
            )
        }
    }
}
