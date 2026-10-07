package com.hearingaid.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.hearingaid.app.audio.Headphones
import com.hearingaid.app.audio.HearingService
import com.hearingaid.app.ui.AdjustScreen
import com.hearingaid.app.ui.CaptionsScreen
import com.hearingaid.app.ui.HearingAidTheme
import com.hearingaid.app.ui.LanguagePicker
import com.hearingaid.app.ui.ListenScreen
import com.hearingaid.app.ui.MoreScreen
import com.hearingaid.app.ui.TestScreen

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        AppGraph.updates.checkInBackground()
        if (savedInstanceState == null) startIfWanted()
        setContent {
            val settings by AppGraph.settings.collectAsState()
            HearingAidTheme(settings.themeMode, settings.skin, settings.textSize) { App() }
        }
    }

    /** "Start when the app opens": only with mic permission and earphones, so it never surprises anyone. */
    private fun startIfWanted() {
        if (!AppGraph.settings.value.autoStart || AppGraph.engine.state.value.running) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        if (Headphones.find(getSystemService(AudioManager::class.java)) == null) return
        HearingService.start(this)
    }
}

private enum class Tab(@StringRes val label: Int, val icon: ImageVector) {
    LISTEN(R.string.tab_listen, Icons.Filled.Home),
    TEST(R.string.tab_test, Icons.Filled.Face),
    CAPTIONS(R.string.tab_captions, Icons.AutoMirrored.Filled.List),
    ADJUST(R.string.tab_adjust, Icons.Filled.Build),
    MORE(R.string.tab_more, Icons.Filled.Menu),
}

@Composable
private fun App() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    var showSafety by remember { mutableStateOf(!AppGraph.repository.safetyNoticeAccepted) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t.ordinal,
                        onClick = { tab = t.ordinal },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = {
                            Text(
                                stringResource(t.label),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (Tab.entries[tab]) {
                Tab.LISTEN -> ListenScreen()
                Tab.TEST -> TestScreen()
                Tab.CAPTIONS -> CaptionsScreen()
                Tab.ADJUST -> AdjustScreen()
                Tab.MORE -> MoreScreen()
            }
        }
    }

    if (showSafety) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.safety_title)) },
            text = {
                // Language first, so people who can't read the default can switch before reading.
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    LanguagePicker(
                        label = stringResource(R.string.app_language),
                        selectedTag = AppLocale.current(context),
                        defaultLabel = stringResource(R.string.phone_default),
                        languages = AppLocale.UI_LANGUAGES,
                        onSelect = { tag -> (context as? Activity)?.let { AppLocale.set(it, tag) } },
                    )
                    Text(stringResource(R.string.safety_body))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    AppGraph.repository.safetyNoticeAccepted = true
                    showSafety = false
                }) { Text(stringResource(R.string.safety_accept)) }
            },
        )
    }
}
