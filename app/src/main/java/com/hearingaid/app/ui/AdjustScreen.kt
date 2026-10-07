package com.hearingaid.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hearingaid.app.AppGraph
import com.hearingaid.app.R
import com.hearingaid.app.dsp.Bands
import com.hearingaid.app.model.HearingSettings
import com.hearingaid.app.model.Prescription
import kotlin.math.roundToInt

private val PITCH_NAMES = intArrayOf(
    R.string.pitch_0, R.string.pitch_1, R.string.pitch_2, R.string.pitch_3, R.string.pitch_4, R.string.pitch_5,
)

@Composable
fun AdjustScreen() {
    val settings by AppGraph.settings.collectAsState()
    var linked by rememberSaveable { mutableStateOf(settings.gainsLeft == settings.gainsRight) }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(stringResource(R.string.adjust_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.adjust_intro),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        SwitchRow(stringResource(R.string.same_both_ears), null, linked) { on ->
            linked = on
            if (on) AppGraph.updateSettings { it.copy(gainsLeft = it.gainsRight) }
        }

        for (band in 0 until Bands.COUNT) {
            SectionTitle(stringResource(R.string.band_title, bandLabel(band), stringResource(PITCH_NAMES[band])))
            if (linked) {
                BandSlider(stringResource(R.string.both_ears), settings.gainsRight[band]) { v ->
                    AppGraph.updateSettings { it.copy(gainsLeft = it.gainsLeft.with(band, v), gainsRight = it.gainsRight.with(band, v)) }
                }
            } else {
                BandSlider(stringResource(R.string.left), settings.gainsLeft[band]) { v ->
                    AppGraph.updateSettings { it.copy(gainsLeft = it.gainsLeft.with(band, v)) }
                }
                BandSlider(stringResource(R.string.right), settings.gainsRight[band]) { v ->
                    AppGraph.updateSettings { it.copy(gainsRight = it.gainsRight.with(band, v)) }
                }
            }
        }

        SectionTitle(stringResource(R.string.section_comfort))
        LabeledSlider(
            label = stringResource(R.string.compression),
            value = settings.compression,
            range = 0f..2f,
            valueText = stringResource(
                when {
                    settings.compression < 0.3f -> R.string.comp_off
                    settings.compression < 1.3f -> R.string.comp_normal
                    else -> R.string.comp_strong
                },
            ),
            onChange = { v -> AppGraph.updateSettings { it.copy(compression = v) } },
        )
        LabeledSlider(
            label = stringResource(R.string.max_loudness),
            value = settings.maxOutputDb,
            range = -24f..-1f,
            valueText = stringResource(R.string.value_db, settings.maxOutputDb.roundToInt()),
            onChange = { v -> AppGraph.updateSettings { it.copy(maxOutputDb = v) } },
        )
        SwitchRow(
            label = stringResource(R.string.voice_processing),
            detail = stringResource(R.string.voice_processing_detail),
            checked = settings.voiceProcessing,
            onChange = { on -> AppGraph.updateSettings { it.copy(voiceProcessing = on) } },
        )

        val audiogram = settings.audiogram
        OutlinedButton(
            onClick = {
                AppGraph.updateSettings {
                    if (audiogram != null) {
                        val fit = Prescription.fit(audiogram)
                        it.copy(gainsLeft = fit.gainsLeft, gainsRight = fit.gainsRight, compression = 1f)
                    } else {
                        it.copy(gainsLeft = HearingSettings.DEFAULT_GAINS, gainsRight = HearingSettings.DEFAULT_GAINS, compression = 1f)
                    }
                }
                linked = audiogram == null
            },
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        ) {
            Text(stringResource(if (audiogram != null) R.string.reset_test else R.string.reset_default))
        }

    }
}

@Composable
private fun BandSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    LabeledSlider(
        label = label,
        value = value,
        range = 0f..Prescription.MAX_BAND_GAIN_DB,
        valueText = stringResource(R.string.band_gain, value.roundToInt()),
        onChange = onChange,
    )
}

private fun List<Float>.with(index: Int, value: Float) = toMutableList().also { it[index] = value }
