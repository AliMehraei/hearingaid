package com.hearingaid.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import com.hearingaid.app.audio.Headphones
import com.hearingaid.app.audio.HearingService

/**
 * Invisible trampoline for the Quick Settings tile: being in the foreground for a moment lets it
 * start the microphone service. Without permission or earphones it opens the app instead, which
 * explains what is missing.
 */
class TileLaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val hasMic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val hasEarphones = Headphones.find(getSystemService(AudioManager::class.java)) != null
        if (hasMic && hasEarphones) {
            HearingService.start(this)
        } else {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
    }
}
