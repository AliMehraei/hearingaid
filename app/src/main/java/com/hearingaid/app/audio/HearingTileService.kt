package com.hearingaid.app.audio

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.hearingaid.app.AppGraph
import com.hearingaid.app.R
import com.hearingaid.app.TileLaunchActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Quick Settings tile: switch the hearing aid on or off from anywhere, and see its volume. */
class HearingTileService : TileService() {
    private var scope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        scope = CoroutineScope(Job() + Dispatchers.Main).also { s ->
            s.launch {
                AppGraph.engine.state.combine(AppGraph.settings) { state, settings -> state.running to settings.volumeDb }
                    .collect { (running, volume) -> render(running, volume.roundToInt()) }
            }
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        if (AppGraph.engine.state.value.running) {
            HearingService.stop(this)
        } else {
            // A microphone service may only start while the app is in the foreground.
            launchAndCollapse()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun launchAndCollapse() {
        val intent = Intent(this, TileLaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun render(running: Boolean, volumeDb: Int) {
        val tile = qsTile ?: return
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.channel_name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (running) getString(R.string.value_db, volumeDb) else null
        }
        tile.updateTile()
    }
}
