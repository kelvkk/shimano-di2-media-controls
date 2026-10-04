package com.di2media.mapping

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/**
 * Synthesizes a triple press from the short/double events the Di2 unit reports
 * (the protocol has no triple-press flag). A double event counts as 2 clicks.
 *
 * Only channels with a triple action configured go through the wait window;
 * all others are dispatched immediately, so existing behaviour has no added latency.
 */
class ClickCounter(
    private val windowMs: () -> Long = { WINDOW_MS },
    private val isTripleEnabled: (channel: Int) -> Boolean,
    private val onShort: (channel: Int) -> Unit,
    private val onDouble: (channel: Int) -> Unit,
    private val onTriple: (channel: Int) -> Unit,
) {
    companion object {
        const val TAG = "ClickCounter"
        const val WINDOW_MS = 700L
        private const val CHANNELS = 5 // index 1..4 used
    }

    private val handler = Handler(Looper.getMainLooper())
    private val counts = IntArray(CHANNELS)
    private val pending = arrayOfNulls<Runnable>(CHANNELS)
    private val lastEventAt = LongArray(CHANNELS)

    fun onShortPress(channel: Int) = add(channel, 1)

    fun onDoublePress(channel: Int) = add(channel, 2)

    fun onLongPress(channel: Int) = reset(channel)

    fun cancelAll() {
        for (ch in 1 until CHANNELS) reset(ch)
    }

    private fun add(channel: Int, clicks: Int) {
        if (channel !in 1 until CHANNELS) return

        val now = SystemClock.uptimeMillis()
        val gap = if (lastEventAt[channel] == 0L) -1L else now - lastEventAt[channel]
        lastEventAt[channel] = now
        Log.i(TAG, "CH$channel +$clicks click(s), gap since last event: ${gap}ms")

        if (!isTripleEnabled(channel)) {
            reset(channel)
            if (clicks == 1) onShort(channel) else onDouble(channel)
            return
        }

        pending[channel]?.let { handler.removeCallbacks(it) }
        counts[channel] += clicks

        if (counts[channel] >= 3) {
            reset(channel)
            onTriple(channel)
            return
        }

        val r = Runnable { flush(channel) }
        pending[channel] = r
        handler.postDelayed(r, windowMs())
    }

    private fun flush(channel: Int) {
        val c = counts[channel]
        reset(channel)
        when (c) {
            1 -> onShort(channel)
            2 -> onDouble(channel)
        }
    }

    private fun reset(channel: Int) {
        pending[channel]?.let { handler.removeCallbacks(it) }
        pending[channel] = null
        counts[channel] = 0
    }
}
