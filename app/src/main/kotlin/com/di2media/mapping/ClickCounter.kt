package com.di2media.mapping

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/**
 * Synthesizes gestures the Di2 unit does not report by itself:
 *  - triple press: counts short/double events (a double event counts as 2 clicks)
 *  - short-then-long: a single short press followed by a long press within a window
 *
 * Only channels that have one of these actions configured go through the wait window;
 * all others are dispatched immediately, so existing behaviour has no added latency.
 */
class ClickCounter(
    private val windowMs: () -> Long = { WINDOW_MS },
    private val shortLongWindowMs: () -> Long = { SHORT_LONG_WINDOW_MS },
    private val isTripleEnabled: (channel: Int) -> Boolean,
    private val isShortLongEnabled: (channel: Int) -> Boolean,
    private val onShort: (channel: Int) -> Unit,
    private val onDouble: (channel: Int) -> Unit,
    private val onTriple: (channel: Int) -> Unit,
) {
    companion object {
        const val TAG = "ClickCounter"
        const val WINDOW_MS = 700L
        const val SHORT_LONG_WINDOW_MS = 1000L
        private const val CHANNELS = 5 // index 1..4 used
    }

    private val handler = Handler(Looper.getMainLooper())
    private val counts = IntArray(CHANNELS)
    private val pending = arrayOfNulls<Runnable>(CHANNELS)
    private val lastEventAt = LongArray(CHANNELS)

    fun onShortPress(channel: Int) = add(channel, 1)

    fun onDoublePress(channel: Int) = add(channel, 2)

    /**
     * Call when a long press starts.
     * Returns true if it completes a short-then-long gesture; the caller should then
     * use the short-then-long hold action instead of the plain long-press action.
     * Otherwise any pending clicks are dispatched first so they are not lost.
     */
    fun onLongPress(channel: Int): Boolean {
        if (channel !in 1 until CHANNELS) return false
        logEvent(channel, "long press")
        val c = counts[channel]
        if (c == 1 && isShortLongEnabled(channel)) {
            reset(channel)
            return true
        }
        if (c > 0) flush(channel) else reset(channel)
        return false
    }

    fun cancelAll() {
        for (ch in 1 until CHANNELS) reset(ch)
    }

    private fun add(channel: Int, clicks: Int) {
        if (channel !in 1 until CHANNELS) return
        logEvent(channel, "+$clicks click(s)")

        val tripleOn = isTripleEnabled(channel)
        val shortLongOn = isShortLongEnabled(channel)

        if (!tripleOn && !shortLongOn) {
            reset(channel)
            if (clicks == 1) onShort(channel) else onDouble(channel)
            return
        }

        if (!tripleOn) {
            // Only waiting to see whether a long press follows a single short press.
            if (counts[channel] > 0) flush(channel)
            if (clicks == 2) {
                onDouble(channel)
                return
            }
            counts[channel] = 1
            schedule(channel)
            return
        }

        pending[channel]?.let { handler.removeCallbacks(it) }
        counts[channel] += clicks

        if (counts[channel] >= 3) {
            reset(channel)
            onTriple(channel)
            return
        }
        schedule(channel)
    }

    private fun schedule(channel: Int) {
        pending[channel]?.let { handler.removeCallbacks(it) }
        val r = Runnable { flush(channel) }
        pending[channel] = r
        handler.postDelayed(r, delayFor(channel))
    }

    private fun delayFor(channel: Int): Long {
        val triple = if (isTripleEnabled(channel)) windowMs() else 0L
        val shortLong = if (isShortLongEnabled(channel)) shortLongWindowMs() else 0L
        return maxOf(triple, shortLong)
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

    private fun logEvent(channel: Int, what: String) {
        val now = SystemClock.uptimeMillis()
        val gap = if (lastEventAt[channel] == 0L) -1L else now - lastEventAt[channel]
        lastEventAt[channel] = now
        Log.i(TAG, "CH$channel $what, gap since last event: ${gap}ms")
    }
}
