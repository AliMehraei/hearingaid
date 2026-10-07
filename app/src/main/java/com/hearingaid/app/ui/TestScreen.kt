package com.hearingaid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hearingaid.app.AppGraph
import com.hearingaid.app.R
import com.hearingaid.app.audio.HearingService
import com.hearingaid.app.audio.HearingTest
import com.hearingaid.app.audio.TestProgress
import com.hearingaid.app.dsp.Ear
import com.hearingaid.app.model.Prescription
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun TestScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by AppGraph.settings.collectAsState()
    val headphones = rememberHeadphones()
    val test = remember { HearingTest(context) }
    var progress by remember { mutableStateOf<TestProgress?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    val view = LocalView.current
    DisposableEffect(job) {
        view.keepScreenOn = job != null
        onDispose { view.keepScreenOn = false }
    }

    val current = progress
    if (job != null && current != null) {
        RunningTest(current, onHeard = test::respond, onCancel = { job?.cancel() })
        return
    }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(stringResource(R.string.test_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.test_intro),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        Text(
            stringResource(R.string.test_tips),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                HearingService.stop(context)
                job = scope.launch {
                    try {
                        val audiogram = test.run { progress = it }
                        val fit = Prescription.fit(audiogram)
                        AppGraph.updateSettings {
                            it.copy(
                                audiogram = audiogram,
                                gainsLeft = fit.gainsLeft,
                                gainsRight = fit.gainsRight,
                                volumeDb = fit.volumeDb,
                            )
                        }
                    } finally {
                        job = null
                        progress = null
                    }
                }
            },
            enabled = headphones != null,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(stringResource(if (settings.audiogram == null) R.string.test_start else R.string.test_again), fontSize = 18.sp)
        }
        if (headphones == null) {
            Text(stringResource(R.string.connect_first), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }

        settings.audiogram?.let {
            SectionTitle(stringResource(R.string.your_result))
            AudiogramChart(it)
            Text(
                stringResource(R.string.result_applied),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun RunningTest(progress: TestProgress, onHeard: () -> Unit, onCancel: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LinearProgressIndicator(
            progress = { progress.step.toFloat() / progress.totalSteps },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(
                R.string.test_ear_freq,
                stringResource(if (progress.ear == Ear.LEFT) R.string.ear_left_caps else R.string.ear_right_caps),
                progress.frequencyHz,
            ),
            style = MaterialTheme.typography.titleLarge,
            color = if (progress.ear == Ear.LEFT) LeftEarColor else RightEarColor,
        )
        Button(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onHeard()
            },
            shape = RoundedCornerShape(32.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            Text(stringResource(R.string.i_hear_it), fontSize = 36.sp, fontWeight = FontWeight.Bold)
        }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel_test)) }
    }
}
