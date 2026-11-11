package com.danielealbano.androidremotecontrolmcp.services.power

/**
 * Whether the screen is on and whether the lock screen guards the device.
 *
 * @property screenOn The display is on (the device is interactive).
 * @property lockScreenShowing The lock screen (keyguard) is showing, secure or not.
 * @property deviceLocked A PIN, pattern or password is needed to get past the lock screen right now.
 * @property deviceSecure A PIN, pattern or password is set on the device.
 */
data class ScreenLockState(
    val screenOn: Boolean,
    val lockScreenShowing: Boolean,
    val deviceLocked: Boolean,
    val deviceSecure: Boolean,
)

/** Outcome of [ScreenPowerController.wakeScreen]. */
enum class WakeScreenResult {
    ALREADY_ON,
    TURNED_ON,
    STAYED_OFF,
}

/** Reads the screen and lock state and turns the screen on. Never unlocks the device. */
interface ScreenPowerController {
    fun lockState(): ScreenLockState

    /**
     * Turns the screen on without dismissing the lock screen, then waits briefly for the display to
     * report that it is on. Safe to call repeatedly: an already-on screen is left untouched.
     */
    suspend fun wakeScreen(): WakeScreenResult
}
