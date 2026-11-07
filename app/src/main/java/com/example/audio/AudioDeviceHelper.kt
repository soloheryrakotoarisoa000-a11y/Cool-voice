package com.example.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AudioDeviceState(
    val isHeadphonesConnected: Boolean = false,
    val isBluetooth: Boolean = false,
    val deviceName: String = "Haut-parleur du téléphone"
)

class AudioDeviceHelper(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val _deviceState = MutableStateFlow(detectCurrentDevice())
    val deviceState: StateFlow<AudioDeviceState> = _deviceState.asStateFlow()

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            _deviceState.value = detectCurrentDevice()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            _deviceState.value = detectCurrentDevice()
        }
    }

    fun startListening() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.registerAudioDeviceCallback(deviceCallback, null)
        }
        _deviceState.value = detectCurrentDevice()
    }

    fun stopListening() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.unregisterAudioDeviceCallback(deviceCallback)
        }
    }

    fun detectCurrentDevice(): AudioDeviceState {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (device in devices) {
                when (device.type) {
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_USB_HEADSET -> {
                        return AudioDeviceState(
                            isHeadphonesConnected = true,
                            isBluetooth = false,
                            deviceName = "Casque / Écouteurs filaires"
                        )
                    }
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> {
                        return AudioDeviceState(
                            isHeadphonesConnected = true,
                            isBluetooth = true,
                            deviceName = "Casque Bluetooth (${device.productName.ifEmpty { "Sans fil" }})"
                        )
                    }
                }
            }
        }
        return AudioDeviceState(
            isHeadphonesConnected = false,
            isBluetooth = false,
            deviceName = "Haut-parleur du téléphone"
        )
    }
}
