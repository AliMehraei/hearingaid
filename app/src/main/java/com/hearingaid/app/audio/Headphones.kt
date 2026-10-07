package com.hearingaid.app.audio

import android.media.AudioDeviceInfo
import android.media.AudioManager

/** Which outputs count as "in the ears". Anything else (the loudspeaker) would cause howling feedback. */
object Headphones {
    /** Placeholder device type for "no device". */
    const val NONE = -1

    private val WIRED = setOf(
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_LINE_DIGITAL,
    )
    private val WIRELESS = setOf(
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_HEARING_AID,
        26, // AudioDeviceInfo.TYPE_BLE_HEADSET (API 31)
        27, // AudioDeviceInfo.TYPE_BLE_SPEAKER (API 31)
    )

    fun isHeadphones(device: AudioDeviceInfo?) = device != null && (device.type in WIRED || device.type in WIRELESS)

    fun isWired(type: Int) = type in WIRED

    fun isWireless(type: Int) = type in WIRELESS

    /** Wired is preferred because it has no Bluetooth codec delay. */
    fun find(audioManager: AudioManager): AudioDeviceInfo? {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return outputs.firstOrNull { it.type in WIRED } ?: outputs.firstOrNull { it.type in WIRELESS }
    }
}
