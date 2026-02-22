package com.drivest.navigation.backend

import java.util.concurrent.atomic.AtomicLong

object BackendRateLimitBackoff {
    private const val BACKOFF_DURATION_MS = 10L * 60L * 1000L
    private val backoffUntilEpochMs = AtomicLong(0L)

    fun record429(nowEpochMs: Long = System.currentTimeMillis()) {
        backoffUntilEpochMs.set(nowEpochMs + BACKOFF_DURATION_MS)
    }

    fun isActive(nowEpochMs: Long = System.currentTimeMillis()): Boolean {
        return nowEpochMs < backoffUntilEpochMs.get()
    }

    fun remainingMs(nowEpochMs: Long = System.currentTimeMillis()): Long {
        return (backoffUntilEpochMs.get() - nowEpochMs).coerceAtLeast(0L)
    }
}
