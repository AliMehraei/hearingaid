package com.hearingaid.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hearingaid.app.AppGraph
import com.hearingaid.app.R
import com.hearingaid.app.audio.EngineError
import com.hearingaid.app.audio.EngineState
import com.hearingaid.app.audio.Headphones
import com.hearingaid.app.audio.HearingService
import com.hearingaid.app.model.MicSource
import com.hearingaid.app.model.Preset
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ListenScreen() {
    val context = LocalContext.current
    val state by AppGraph.engine.state.collectAsState()
    val settings by AppGraph.settings.collectAsState()
    val headphones = rememberHeadphones()
    val withMic = rememberWithMicPermission()
    val updateState by AppGraph.updates.state.collectAsState()
    val dismissedUpdate by AppGraph.updates.dismissed.collectAsState()

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        UpdateBanner(updateState, dismissedUpdate)
        OutputCard(state, headphones?.type ?: Headphones.NONE, headphones?.productName?.toString().orEmpty())

        Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
            Button(
                onClick = {
                    if (state.running) HearingService.stop(context)
                    else withMic { HearingService.start(context) }
                },
                enabled = state.running || headphones != null,
                shape = CircleShape,
                modifier = Modifier.size(170.dp),
                colors = if (state.running) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                } else ButtonDefaults.buttonColors(),
            ) {
                Text(
                    stringResource(if (state.running) R.string.stop else R.string.start),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        state.error?.takeIf { !state.running }?.let { error ->
            Text(
                when (error) {
                    EngineError.MIC_PERMISSION -> stringResource(R.string.err_mic_permission)
                    EngineError.NO_EARPHONES -> stringResource(R.string.err_no_earphones)
                    EngineError.AUDIO_FAILED -> stringResource(R.string.err_audio, state.errorDetail)
                },
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        if (state.running) {
            LevelMeter(stringResource(R.string.meter_mic), state.inputLevelDb)
            LevelMeter(stringResource(R.string.meter_ears), state.outputLevelDb)
        }

        SectionTitle(stringResource(R.string.section_volume))
        LabeledSlider(
            label = stringResource(R.string.loudness),
            value = settings.volumeDb,
            range = -10f..40f,
            valueText = stringResource(R.string.value_db, settings.volumeDb.roundToInt()),
            onChange = { v -> AppGraph.updateSettings { it.copy(volumeDb = v) } },
        )
        LabeledSlider(
            label = stringResource(R.string.balance),
            value = settings.balance,
            range = -1f..1f,
            valueText = when {
                settings.balance < -0.05f -> stringResource(R.string.balance_left, (-settings.balance * 100).roundToInt())
                settings.balance > 0.05f -> stringResource(R.string.balance_right, (settings.balance * 100).roundToInt())
                else -> stringResource(R.string.balance_centre)
            },
            onChange = { v -> AppGraph.updateSettings { it.copy(balance = if (abs(v) < 0.05f) 0f else v) } },
            // The left end must stay the left ear even in right-to-left languages.
            forceLtrSlider = true,
        )

        SectionTitle(stringResource(R.string.section_situation))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Preset.entries.forEach { preset ->
                FilterChip(
                    selected = settings.preset == preset,
                    onClick = { AppGraph.updateSettings { it.copy(preset = preset) } },
                    label = { Text(stringResource(preset.titleRes)) },
                )
            }
        }
        SwitchRow(
            label = stringResource(R.string.noise_reduction),
            detail = stringResource(R.string.noise_reduction_detail),
            checked = settings.noiseReduction,
            onChange = { on -> AppGraph.updateSettings { it.copy(noiseReduction = on) } },
        )

        SectionTitle(stringResource(R.string.section_microphone))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MicSource.entries.forEach { source ->
                FilterChip(
                    selected = settings.micSource == source,
                    onClick = { AppGraph.updateSettings { it.copy(micSource = source) } },
                    label = { Text(stringResource(source.titleRes)) },
                )
            }
        }
        Text(
            stringResource(R.string.mic_tip),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )

        Text(
            stringResource(R.string.disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 24.dp),
        )
    }
}

@Composable
private fun OutputCard(state: EngineState, connectedType: Int, connectedName: String) {
    val connected = connectedType != Headphones.NONE
    val warning = when {
        state.running && state.mutedNoHeadphones -> stringResource(R.string.warn_disconnected)
        !connected && !state.running -> stringResource(R.string.warn_connect)
        (if (state.running) state.wireless else Headphones.isWireless(connectedType)) ->
            stringResource(R.string.warn_bluetooth)
        else -> null
    }
    val alarming = (!connected && !state.running) || state.mutedNoHeadphones
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (alarming) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (state.running) outputLabel(state.outputType, state.outputName)
                else outputLabel(connectedType, connectedName),
                style = MaterialTheme.typography.titleMedium,
            )
            if (state.running) {
                Text(
                    stringResource(R.string.latency_line, inputLabel(state.inputType, state.inputName), state.latencyMs),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            warning?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp)) }
        }
    }
}

@Composable
private fun LevelMeter(label: String, db: Float) {
    val fraction = ((db - EngineState.SILENCE_DB) / -EngineState.SILENCE_DB).coerceIn(0f, 1f)
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.35f))
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.weight(0.65f))
    }
}
