package com.hearingaid.app.audio

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.hearingaid.app.AppGraph
import com.hearingaid.app.AppLocale
import com.hearingaid.app.HearingAidApp
import com.hearingaid.app.MainActivity
import com.hearingaid.app.R
import com.hearingaid.app.model.HearingSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Keeps amplification running with the screen off or the app in the background. Its notification
 * shows the situation and volume, with Quieter / Louder / Turn off buttons.
 */
class HearingService : Service() {
    private var scope: CoroutineScope? = null

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocale.wrap(base))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                shutDown()
                return START_NOT_STICKY
            }
            ACTION_LOUDER, ACTION_QUIETER -> {
                val step = if (intent.action == ACTION_LOUDER) VOLUME_STEP_DB else -VOLUME_STEP_DB
                AppGraph.updateSettings { it.copy(volumeDb = (it.volumeDb + step).coerceIn(MIN_VOLUME_DB, MAX_VOLUME_DB)) }
                return START_NOT_STICKY
            }
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(AppGraph.settings.value),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0,
        )
        if (!AppGraph.engine.start(onFatalError = { stop(this) })) {
            shutDown()
            return START_NOT_STICKY
        }
        watchSettings()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope?.cancel()
        AppGraph.engine.stop()
        super.onDestroy()
    }

    /** Keeps the notification's volume and situation in step with changes made anywhere. */
    private fun watchSettings() {
        if (scope != null) return
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main).also { s ->
            s.launch {
                AppGraph.settings
                    .map { it.preset to it.volumeDb.roundToInt() }
                    .distinctUntilChanged()
                    .collect {
                        getSystemService(NotificationManager::class.java)
                            .notify(NOTIFICATION_ID, buildNotification(AppGraph.settings.value))
                    }
            }
        }
    }

    private fun shutDown() {
        AppGraph.engine.stop()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(settings: HearingSettings): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, HearingAidApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ear)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(
                getString(R.string.notif_status, getString(settings.preset.titleRes), settings.volumeDb.roundToInt()),
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.notif_quieter), actionIntent(ACTION_QUIETER, 2))
            .addAction(0, getString(R.string.notif_louder), actionIntent(ACTION_LOUDER, 3))
            .addAction(0, getString(R.string.notif_stop), actionIntent(ACTION_STOP, 1))
            .build()
    }

    private fun actionIntent(action: String, requestCode: Int) = PendingIntent.getService(
        this, requestCode, Intent(this, HearingService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val ACTION_STOP = "com.hearingaid.app.STOP"
        private const val ACTION_LOUDER = "com.hearingaid.app.LOUDER"
        private const val ACTION_QUIETER = "com.hearingaid.app.QUIETER"
        private const val NOTIFICATION_ID = 1
        private const val VOLUME_STEP_DB = 3f
        private const val MIN_VOLUME_DB = -10f
        private const val MAX_VOLUME_DB = 40f

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, HearingService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, HearingService::class.java).setAction(ACTION_STOP))
        }
    }
}
