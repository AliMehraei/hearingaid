package com.hearingaid.app

import android.os.Build

/** Product details shown in the About screen. */
object AppInfo {
    const val AUTHOR = "Ali Mehraei"
    const val EMAIL = "ali.mehraei.dev@gmail.com"
    const val GITHUB_URL = "https://github.com/AliMehraei/hearingaid"
    const val WEBSITE = "https://www.argbyte.com"
    const val LICENSE = "PolyForm Noncommercial 1.0.0 + education"
    const val LICENSE_URL = "$GITHUB_URL/blob/main/LICENSE.md"
    const val HELP_URL = "$GITHUB_URL/blob/main/docs/HELP.md"

    val version: String get() = BuildConfig.VERSION_NAME

    /** One line for bug reports: app, build, Android version and phone model. */
    val details: String
        get() = "Hearing Aid $version (${BuildConfig.FLAVOR}, ${BuildConfig.VERSION_CODE}) · " +
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}"
}
