package com.hearingaid.app

import android.content.Context
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

    /**
     * The installed version, read from Android at startup. BuildConfig.VERSION_NAME is a compile-time
     * constant that incremental builds can leave stale, and the updater must compare the real version.
     */
    var version: String = BuildConfig.VERSION_NAME
        private set
    private var versionCode: Long = BuildConfig.VERSION_CODE.toLong()

    fun init(context: Context) {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.versionName?.let { version = it }
        versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
        else @Suppress("DEPRECATION") info.versionCode.toLong()
    }

    /** One line for bug reports: app, build, Android version and phone model. */
    val details: String
        get() = "Hearing Aid $version (${BuildConfig.FLAVOR}, $versionCode) · " +
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}"
}
