package io.ferventio.app.performance

import io.ferventio.app.security.SafeLog
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class MainThreadWatchdog(
    private val thresholdMillis: Long = DEFAULT_THRESHOLD_MILLIS,
    private val checkIntervalMillis: Long = DEFAULT_CHECK_INTERVAL_MILLIS,
) {
    private val running = AtomicBoolean(false)
    private val lastMainAckMillis = AtomicLong(SystemClock.elapsedRealtime())
    private val lastReportedBlockMillis = AtomicLong(0L)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val heartbeat = object : Runnable {
        override fun run() {
            lastMainAckMillis.set(SystemClock.elapsedRealtime())
            if (running.get()) mainHandler.postDelayed(this, checkIntervalMillis)
        }
    }

    fun start() {
        if (!running.compareAndSet(false, true)) return
        mainHandler.post(heartbeat)
        Thread(::watchLoop, "ferventio-main-watchdog").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
            start()
        }
    }

    fun stop() {
        running.set(false)
        mainHandler.removeCallbacks(heartbeat)
    }

    fun lastDetectedBlockMillis(): Long = lastReportedBlockMillis.get()

    private fun watchLoop() {
        var reportedForCurrentBlock = false
        while (running.get()) {
            try {
                Thread.sleep(checkIntervalMillis)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
            val now = SystemClock.elapsedRealtime()
            val blockedFor = now - lastMainAckMillis.get()
            if (blockedFor >= thresholdMillis) {
                if (!reportedForCurrentBlock) {
                    lastReportedBlockMillis.set(blockedFor)
                    SafeLog.e(TAG, "Main thread has not responded for ${blockedFor}ms")
                    reportedForCurrentBlock = true
                }
            } else {
                reportedForCurrentBlock = false
            }
        }
    }

    private companion object {
        const val TAG = "FerventioANR"
        const val DEFAULT_THRESHOLD_MILLIS = 5_000L
        const val DEFAULT_CHECK_INTERVAL_MILLIS = 1_000L
    }
}
