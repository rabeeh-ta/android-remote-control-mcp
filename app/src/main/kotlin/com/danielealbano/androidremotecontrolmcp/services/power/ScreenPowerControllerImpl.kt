package com.danielealbano.androidremotecontrolmcp.services.power

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** [ScreenPowerController] backed by [PowerManager] and [KeyguardManager]. */
class ScreenPowerControllerImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ScreenPowerController {
        private val powerManager: PowerManager
            get() = checkNotNull(context.getSystemService(PowerManager::class.java)) { "No PowerManager" }

        private val keyguardManager: KeyguardManager
            get() = checkNotNull(context.getSystemService(KeyguardManager::class.java)) { "No KeyguardManager" }

        override fun lockState(): ScreenLockState {
            val keyguard = keyguardManager
            return ScreenLockState(
                screenOn = powerManager.isInteractive,
                lockScreenShowing = keyguard.isKeyguardLocked,
                deviceLocked = keyguard.isDeviceLocked,
                deviceSecure = keyguard.isDeviceSecure,
            )
        }

        override suspend fun wakeScreen(): WakeScreenResult {
            val power = powerManager
            if (power.isInteractive) return WakeScreenResult.ALREADY_ON

            val wakeLock = newScreenWakeLock(power)
            wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS)
            try {
                withTimeoutOrNull(WAIT_FOR_SCREEN_ON_MS) {
                    while (!power.isInteractive) delay(POLL_INTERVAL_MS)
                }
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
            }

            val result = if (power.isInteractive) WakeScreenResult.TURNED_ON else WakeScreenResult.STAYED_OFF
            Log.i(TAG, "wakeScreen: $result")
            return result
        }

        /**
         * A short wake lock that turns the screen on. SCREEN_BRIGHT_WAKE_LOCK and ACQUIRE_CAUSES_WAKEUP are
         * deprecated in favour of Activity.setTurnScreenOn, which needs a visible activity; this runs from a
         * background service with no activity, so a wake lock is the only non-root way to turn the screen on.
         * ON_AFTER_RELEASE keeps the screen on for the normal timeout after the lock is released.
         */
        @Suppress("DEPRECATION")
        private fun newScreenWakeLock(power: PowerManager): PowerManager.WakeLock =
            power
                .newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                    WAKE_LOCK_TAG,
                ).apply { setReferenceCounted(false) }

        companion object {
            private const val TAG = "MCP:ScreenPower"
            private const val WAKE_LOCK_TAG = "AndroidRemoteControlMcp:WakeScreen"
            private const val WAKE_LOCK_TIMEOUT_MS = 3_000L
            private const val WAIT_FOR_SCREEN_ON_MS = 2_000L
            private const val POLL_INTERVAL_MS = 50L
        }
    }
