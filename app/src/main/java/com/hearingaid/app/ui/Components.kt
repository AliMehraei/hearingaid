package com.hearingaid.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.hearingaid.app.Language
import com.hearingaid.app.R
import com.hearingaid.app.audio.Headphones
import com.hearingaid.app.dsp.Bands

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 20.dp, bottom = 4.dp),
    )
}

@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onChange: (Float) -> Unit,
    steps: Int = 0,
    /** For sliders whose ends mean physical left/right, which must not mirror in RTL languages. */
    forceLtrSlider: Boolean = false,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.bodyMedium)
        }
        val direction = if (forceLtrSlider) LayoutDirection.Ltr else LocalLayoutDirection.current
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
        }
    }
}

@Composable
fun SwitchRow(label: String, detail: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun bandLabel(band: Int): String {
    val hz = Bands.CENTERS_HZ[band]
    return if (hz >= 1000) stringResource(R.string.khz, hz / 1000) else stringResource(R.string.hz, hz)
}

/** Human name for an output device type ([Headphones.NONE] when nothing is connected). */
@Composable
fun outputLabel(type: Int, name: String): String = when {
    type == Headphones.NONE -> stringResource(R.string.no_earphones_connected)
    Headphones.isWired(type) -> stringResource(R.string.out_wired)
    type == AudioDeviceInfo.TYPE_HEARING_AID -> stringResource(R.string.out_hearing_aid, name)
    Headphones.isWireless(type) -> stringResource(R.string.out_bluetooth, name)
    type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> stringResource(R.string.out_speaker)
    type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> stringResource(R.string.out_earpiece)
    else -> name
}

@Composable
fun inputLabel(type: Int, name: String): String = when (type) {
    Headphones.NONE, AudioDeviceInfo.TYPE_BUILTIN_MIC -> stringResource(R.string.in_phone)
    AudioDeviceInfo.TYPE_WIRED_HEADSET -> stringResource(R.string.in_headset)
    AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> stringResource(R.string.in_usb)
    else -> name
}

/** A button showing the current choice that opens a menu of languages, each named in its own script. */
@Composable
fun LanguagePicker(
    label: String,
    selectedTag: String,
    defaultLabel: String,
    languages: List<Language>,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val current = languages.firstOrNull { it.tag == selectedTag }?.nativeName ?: defaultLabel
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                Text("🌐  $current", modifier = Modifier.weight(1f))
                Text("▾")
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text(defaultLabel) }, onClick = { open = false; onSelect("") })
                languages.forEach { language ->
                    DropdownMenuItem(
                        text = { Text(language.nativeName) },
                        onClick = { open = false; onSelect(language.tag) },
                    )
                }
            }
        }
    }
}

/** The currently connected earphones, updated live as devices come and go. */
@Composable
fun rememberHeadphones(): AudioDeviceInfo? {
    val audioManager = LocalContext.current.getSystemService(AudioManager::class.java)
    var device by remember { mutableStateOf(Headphones.find(audioManager)) }
    DisposableEffect(audioManager) {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) {
                device = Headphones.find(audioManager)
            }

            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) {
                device = Headphones.find(audioManager)
            }
        }
        audioManager.registerAudioDeviceCallback(callback, null)
        onDispose { audioManager.unregisterAudioDeviceCallback(callback) }
    }
    return device
}

/** Runs [action] right away if the mic permission is granted, otherwise asks for it first. */
@Composable
fun rememberWithMicPermission(): ((() -> Unit) -> Unit) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pending?.invoke()
        pending = null
    }
    return { action ->
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            pending = action
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}
