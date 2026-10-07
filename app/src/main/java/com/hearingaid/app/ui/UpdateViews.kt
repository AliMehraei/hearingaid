package com.hearingaid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hearingaid.app.AppGraph
import com.hearingaid.app.R
import com.hearingaid.app.update.Release
import com.hearingaid.app.update.UpdateState

/** The release an update card is about, if the state concerns one. */
fun UpdateState.release(): Release? = when (this) {
    is UpdateState.Available -> release
    is UpdateState.Downloading -> release
    is UpdateState.Ready -> release
    is UpdateState.Failed -> release
    else -> null
}

/** Status line plus the action that fits it; shared by the Listen banner and About. */
@Composable
fun UpdateStatus(state: UpdateState, onLater: (() -> Unit)? = null) {
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (state) {
            UpdateState.Idle -> Unit
            UpdateState.Checking -> Text(stringResource(R.string.checking))
            UpdateState.UpToDate -> Text(stringResource(R.string.up_to_date))
            is UpdateState.Available -> Text(stringResource(R.string.update_available, state.release.version))
            is UpdateState.Downloading -> {
                Text(stringResource(R.string.downloading, state.percent))
                LinearProgressIndicator(progress = { state.percent / 100f }, modifier = Modifier.fillMaxWidth())
            }
            is UpdateState.Ready -> Text(stringResource(R.string.update_ready))
            is UpdateState.Failed -> {
                Text(stringResource(state.error.messageRes), color = MaterialTheme.colorScheme.error)
                if (state.error.detail.isNotBlank()) {
                    Text(state.error.detail, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        val release = state.release()
        if (release != null && state !is UpdateState.Downloading) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { AppGraph.updates.install(release) }) {
                    Text(stringResource(R.string.install_update, release.version))
                }
                TextButton(onClick = { runCatching { uriHandler.openUri(release.pageUrl) } }) {
                    Text(stringResource(R.string.release_page))
                }
                if (onLater != null && state is UpdateState.Available) {
                    TextButton(onClick = onLater) { Text(stringResource(R.string.update_later)) }
                }
            }
        }
    }
}

/** Shown at the top of Listen when a newer release is out, until the person closes it for that version. */
@Composable
fun UpdateBanner(state: UpdateState, dismissedVersion: String) {
    val release = state.release() ?: return
    if (state is UpdateState.Available && release.version == dismissedVersion) return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.update_banner, release.version), style = MaterialTheme.typography.titleMedium)
            UpdateStatus(state, onLater = { AppGraph.updates.dismissBanner(release) })
        }
    }
}
