package com.hearingaid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hearingaid.app.AppGraph
import com.hearingaid.app.AppLocale
import com.hearingaid.app.R
import com.hearingaid.app.audio.HearingService
import com.hearingaid.app.captions.LiveCaptioner

@Composable
fun CaptionsScreen() {
    val context = LocalContext.current
    val captioner = remember { LiveCaptioner(context) }
    val withMic = rememberWithMicPermission()
    var textSize by rememberSaveable { mutableFloatStateOf(30f) }
    var speechLanguage by remember { mutableStateOf(AppGraph.repository.captionLanguage) }
    val appLocale = LocalConfiguration.current.locales[0]
    val listState = rememberLazyListState()

    val view = LocalView.current
    DisposableEffect(Unit) {
        onDispose {
            captioner.stop()
            view.keepScreenOn = false
        }
    }
    LaunchedEffect(captioner.listening) { view.keepScreenOn = captioner.listening }
    LaunchedEffect(captioner.lines.size, captioner.partial) {
        listState.animateScrollToItem(captioner.lines.size)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (captioner.listening) {
                        captioner.stop()
                    } else withMic {
                        // Most phones give the microphone to one app at a time.
                        if (AppGraph.engine.state.value.running) HearingService.stop(context)
                        captioner.start(speechLanguage.ifEmpty { appLocale.toLanguageTag() })
                    }
                },
                colors = if (captioner.listening) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                } else ButtonDefaults.buttonColors(),
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                Text(
                    stringResource(if (captioner.listening) R.string.captions_stop else R.string.captions_start),
                    fontSize = 18.sp,
                )
            }
            OutlinedButton(onClick = captioner::clear, modifier = Modifier.height(52.dp)) {
                Text(stringResource(R.string.clear))
            }
        }
        if (!captioner.listening) {
            LanguagePicker(
                label = stringResource(R.string.speech_language),
                selectedTag = speechLanguage,
                defaultLabel = appLocale.getDisplayName(appLocale),
                languages = AppLocale.SPEECH_LANGUAGES,
                onSelect = {
                    speechLanguage = it
                    AppGraph.repository.captionLanguage = it
                },
            )
        }
        Row(Modifier.fillMaxWidth()) {
            Text("A", fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
            Slider(value = textSize, onValueChange = { textSize = it }, valueRange = 18f..56f, modifier = Modifier.weight(1f))
            Text("A", fontSize = 26.sp)
        }
        captioner.error?.let { Text(stringResource(it.message, it.code), color = MaterialTheme.colorScheme.error) }
        if (captioner.lines.isEmpty() && captioner.partial.isEmpty()) {
            Text(
                stringResource(R.string.captions_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(top = 8.dp)) {
            items(captioner.lines) { line ->
                Text(line, fontSize = textSize.sp, lineHeight = (textSize * 1.25f).sp, modifier = Modifier.padding(vertical = 6.dp))
            }
            item {
                Text(
                    captioner.partial,
                    fontSize = textSize.sp,
                    lineHeight = (textSize * 1.25f).sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
        }
    }
}
