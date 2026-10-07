package com.hearingaid.app.update

import android.content.Context
import com.hearingaid.app.AppInfo
import com.hearingaid.app.BuildConfig
import com.hearingaid.app.R
import com.hearingaid.app.model.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val percent: Int) : UpdateState
    /** Downloaded and verified; waiting for the person to allow installs or confirm Android's prompt. */
    data class Ready(val release: Release, val apk: File) : UpdateState
    data class Failed(val error: UpdateException, val release: Release?) : UpdateState
}

/** Background checks (at most every 12 hours) plus the on-demand check and install from About. */
class UpdateManager(private val context: Context, private val repository: SettingsRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val enabled: Boolean get() = BuildConfig.SELF_UPDATE

    /** Called when the app opens. Quiet: failures are not shown, only a found update is. */
    fun checkInBackground() {
        if (!enabled || !repository.settings.value.autoCheckUpdates) return
        if (System.currentTimeMillis() - repository.lastUpdateCheck < CHECK_INTERVAL_MS) return
        check(quiet = true)
    }

    fun check(quiet: Boolean = false) {
        if (!enabled || job?.isActive == true) return
        job = scope.launch {
            if (!quiet) _state.value = UpdateState.Checking
            _state.value = try {
                val release = Updater.fetchLatest()
                repository.lastUpdateCheck = System.currentTimeMillis()
                if (Versions.isNewer(release.version, AppInfo.version)) UpdateState.Available(release)
                else if (quiet) UpdateState.Idle else UpdateState.UpToDate
            } catch (e: UpdateException) {
                if (quiet) UpdateState.Idle else UpdateState.Failed(e, null)
            } catch (e: Exception) {
                if (quiet) UpdateState.Idle else UpdateState.Failed(UpdateException(R.string.upd_err_network, e.toString()), null)
            }
        }
    }

    fun install(release: Release) {
        val current = _state.value
        if (current is UpdateState.Ready && current.release == release && current.apk.exists()) {
            Updater.install(context, current.apk)
            return
        }
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = UpdateState.Downloading(release, 0)
            try {
                val apk = Updater.download(context, release) { _state.value = UpdateState.Downloading(release, it) }
                _state.value = UpdateState.Ready(release, apk)
                Updater.install(context, apk)
            } catch (e: UpdateException) {
                _state.value = UpdateState.Failed(e, release)
            } catch (e: Exception) {
                _state.value = UpdateState.Failed(UpdateException(R.string.upd_err_network, e.toString()), release)
            }
        }
    }

    private val _dismissed = MutableStateFlow(repository.dismissedUpdate)

    /** The version whose banner the person closed; the banner comes back for the next release. */
    val dismissed: StateFlow<String> = _dismissed.asStateFlow()

    fun dismissBanner(release: Release) {
        repository.dismissedUpdate = release.version
        _dismissed.value = release.version
    }

    private companion object {
        const val CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L
    }
}
