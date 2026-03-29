package com.di2media.mapping

import android.content.Context
import android.media.AudioManager
import android.util.Log
import android.view.KeyEvent
import kotlinx.coroutines.*

class ActionDispatcher(private val context: Context) {

    companion object {
        const val TAG = "ActionDispatcher"
        const val RAMP_INTERVAL_MS = 300L
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val activeHolds = mutableMapOf<Int, Job>()

    private val audioManager: AudioManager
        get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun dispatch(action: InstantAction) {
        Log.i(TAG, "Dispatch: ${action.label}")
        when (action) {
            InstantAction.NEXT -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            InstantAction.PREVIOUS -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            InstantAction.PLAY_PAUSE -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            InstantAction.VOLUME_UP -> adjustVolume(AudioManager.ADJUST_RAISE)
            InstantAction.VOLUME_DOWN -> adjustVolume(AudioManager.ADJUST_LOWER)
            InstantAction.NONE -> {}
        }
    }

    fun onHoldStart(channel: Int, action: HoldAction) {
        Log.i(TAG, "Hold start CH$channel: ${action.label}")
        activeHolds[channel]?.cancel()

        when (action) {
            HoldAction.PLAY_PAUSE -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            HoldAction.NEXT -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            HoldAction.PREVIOUS -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            HoldAction.VOLUME_RAMP_UP -> startRamp(channel) { adjustVolume(AudioManager.ADJUST_RAISE) }
            HoldAction.VOLUME_RAMP_DOWN -> startRamp(channel) { adjustVolume(AudioManager.ADJUST_LOWER) }
            HoldAction.NONE -> {}
        }
    }

    fun onHoldStop(channel: Int) {
        Log.i(TAG, "Hold stop CH$channel")
        activeHolds.remove(channel)?.cancel()
    }

    fun destroy() {
        scope.cancel()
    }

    private fun startRamp(channel: Int, action: () -> Unit) {
        action()
        activeHolds[channel] = scope.launch {
            while (isActive) {
                delay(RAMP_INTERVAL_MS)
                action()
            }
        }
    }

    private fun sendMediaKey(keyCode: Int) {
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private fun adjustVolume(direction: Int) {
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
    }
}
