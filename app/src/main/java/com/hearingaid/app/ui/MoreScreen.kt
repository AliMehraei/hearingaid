package com.hearingaid.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.hearingaid.app.AppGraph
import com.hearingaid.app.AppInfo
import com.hearingaid.app.AppLocale
import com.hearingaid.app.R
import com.hearingaid.app.model.Skin
import com.hearingaid.app.model.TextSize
import com.hearingaid.app.model.ThemeMode
import com.hearingaid.app.update.UpdateState

private enum class Page { MENU, APPEARANCE, BACKGROUND, HELP, ABOUT }

@Composable
fun MoreScreen() {
    var page by rememberSaveable { mutableStateOf(Page.MENU) }
    BackHandler(enabled = page != Page.MENU) { page = Page.MENU }
    val back = { page = Page.MENU }
    when (page) {
        Page.MENU -> MoreMenu(onOpen = { page = it })
        Page.APPEARANCE -> SubPage(stringResource(R.string.more_appearance), back) { AppearancePage() }
        Page.BACKGROUND -> SubPage(stringResource(R.string.more_background), back) { BackgroundPage() }
        Page.HELP -> SubPage(stringResource(R.string.more_help), back) { HelpPage() }
        Page.ABOUT -> SubPage(stringResource(R.string.more_about), back) { AboutPage() }
    }
}

@Composable
private fun MoreMenu(onOpen: (Page) -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        MenuRow(stringResource(R.string.more_appearance)) { onOpen(Page.APPEARANCE) }
        MenuRow(stringResource(R.string.more_background)) { onOpen(Page.BACKGROUND) }
        MenuRow(stringResource(R.string.more_help)) { onOpen(Page.HELP) }
        MenuRow(stringResource(R.string.more_about)) { onOpen(Page.ABOUT) }

        SectionTitle(stringResource(R.string.section_language))
        LanguagePicker(
            label = stringResource(R.string.app_language),
            selectedTag = AppLocale.current(context),
            defaultLabel = stringResource(R.string.phone_default),
            languages = AppLocale.UI_LANGUAGES,
            onSelect = { tag -> (context as? Activity)?.let { AppLocale.set(it, tag) } },
        )
        Text(
            "${stringResource(R.string.app_name)} · ${stringResource(R.string.about_version, AppInfo.version)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 24.dp),
        )
    }
}

@Composable
private fun MenuRow(title: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
    HorizontalDivider()
}

@Composable
private fun SubPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 4.dp, top = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun <T> ChoiceChips(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearancePage() {
    val settings by AppGraph.settings.collectAsState()
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    SectionTitle(stringResource(R.string.mode_title))
    ChoiceChips(ThemeMode.entries, settings.themeMode, { stringResource(it.titleRes) }) { mode ->
        AppGraph.updateSettings { it.copy(themeMode = mode) }
    }

    SectionTitle(stringResource(R.string.skin_title))
    val skins = Skin.entries.filter { it != Skin.WALLPAPER || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        skins.forEach { skin ->
            val selected = settings.skin == skin
            Column(
                Modifier
                    .width(84.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { AppGraph.updateSettings { it.copy(skin = skin) } }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (skin == Skin.WALLPAPER) MaterialTheme.colorScheme.tertiary else skinSwatch(skin, dark),
                        )
                        .border(
                            BorderStroke(if (selected) 4.dp else 1.dp, MaterialTheme.colorScheme.onBackground),
                            CircleShape,
                        ),
                )
                Text(
                    stringResource(skin.titleRes),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 2,
                )
            }
        }
    }

    SectionTitle(stringResource(R.string.text_title))
    ChoiceChips(TextSize.entries, settings.textSize, { stringResource(it.titleRes) }) { size ->
        AppGraph.updateSettings { it.copy(textSize = size) }
    }
}

@Composable
private fun BackgroundPage() {
    val context = LocalContext.current
    val settings by AppGraph.settings.collectAsState()
    val power = context.getSystemService(PowerManager::class.java)
    // Re-read when coming back from the system battery screen.
    var resumes by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resumes++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val unrestricted = remember(resumes) { power.isIgnoringBatteryOptimizations(context.packageName) }

    SwitchRow(
        label = stringResource(R.string.auto_start),
        detail = stringResource(R.string.auto_start_detail),
        checked = settings.autoStart,
        onChange = { on -> AppGraph.updateSettings { it.copy(autoStart = on) } },
    )
    InfoBlock(stringResource(R.string.notif_hint_title), stringResource(R.string.notif_hint_detail))
    InfoBlock(stringResource(R.string.tile_title), stringResource(R.string.tile_detail))
    InfoBlock(
        stringResource(R.string.battery_title),
        stringResource(if (unrestricted) R.string.battery_ok else R.string.battery_detail),
    )
    if (!unrestricted) {
        OutlinedButton(
            onClick = {
                try {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } catch (_: ActivityNotFoundException) {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            },
            modifier = Modifier.padding(top = 8.dp),
        ) { Text(stringResource(R.string.battery_button)) }
    }
}

@Composable
private fun InfoBlock(title: String, body: String) {
    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val HELP_SECTIONS = listOf(
    R.string.help_start_t to R.string.help_start_b,
    R.string.help_test_t to R.string.help_test_b,
    R.string.help_earphones_t to R.string.help_earphones_b,
    R.string.help_noise_t to R.string.help_noise_b,
    R.string.help_whistle_t to R.string.help_whistle_b,
    R.string.help_captions_t to R.string.help_captions_b,
    R.string.help_background_t to R.string.help_background_b,
    R.string.help_safety_t to R.string.help_safety_b,
)

@Composable
private fun HelpPage() {
    val uriHandler = LocalUriHandler.current
    HELP_SECTIONS.forEach { (title, body) -> InfoBlock(stringResource(title), stringResource(body)) }
    TextButton(onClick = { runCatching { uriHandler.openUri(AppInfo.HELP_URL) } }, modifier = Modifier.padding(top = 12.dp)) {
        Text(stringResource(R.string.help_online))
    }
}

@Composable
private fun AboutPage() {
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    val settings by AppGraph.settings.collectAsState()
    val updateState by AppGraph.updates.state.collectAsState()
    var copied by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.about_version, AppInfo.version), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.about_tagline), style = MaterialTheme.typography.bodyMedium)
        }
    }

    AboutRow(stringResource(R.string.about_author), AppInfo.AUTHOR)
    AboutRow(stringResource(R.string.about_email), AppInfo.EMAIL) { runCatching { uriHandler.openUri("mailto:${AppInfo.EMAIL}") } }
    AboutRow(stringResource(R.string.about_source), AppInfo.GITHUB_URL.removePrefix("https://")) {
        runCatching { uriHandler.openUri(AppInfo.GITHUB_URL) }
    }
    AboutRow(stringResource(R.string.about_website), AppInfo.WEBSITE.removePrefix("https://")) {
        runCatching { uriHandler.openUri(AppInfo.WEBSITE) }
    }
    AboutRow(stringResource(R.string.about_license), AppInfo.LICENSE, stringResource(R.string.license_summary)) {
        runCatching { uriHandler.openUri(AppInfo.LICENSE_URL) }
    }

    OutlinedButton(
        onClick = {
            clipboard.setText(AnnotatedString(AppInfo.details))
            copied = true
        },
        modifier = Modifier.padding(top = 16.dp),
    ) { Text(stringResource(if (copied) R.string.copied else R.string.copy_details)) }

    SectionTitle(stringResource(R.string.updates_title))
    if (!AppGraph.updates.enabled) {
        Text(stringResource(R.string.updates_play))
        return
    }
    SwitchRow(
        label = stringResource(R.string.check_auto),
        detail = null,
        checked = settings.autoCheckUpdates,
        onChange = { on -> AppGraph.updateSettings { it.copy(autoCheckUpdates = on) } },
    )
    UpdateStatus(updateState)
    if (updateState.release() == null && updateState != UpdateState.Checking) {
        OutlinedButton(onClick = { AppGraph.updates.check() }, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.check_now))
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String, detail: String? = null, onClick: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(MaterialTheme.shapes.small)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer {
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall)
    }
}
