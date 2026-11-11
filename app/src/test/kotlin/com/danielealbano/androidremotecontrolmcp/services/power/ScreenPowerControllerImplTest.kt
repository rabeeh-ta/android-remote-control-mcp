package com.danielealbano.androidremotecontrolmcp.services.power

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import android.util.Log
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ScreenPowerControllerImpl")
class ScreenPowerControllerImplTest {
    private lateinit var powerManager: PowerManager
    private lateinit var keyguardManager: KeyguardManager
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var controller: ScreenPowerControllerImpl

    private val screenOnFlags =
        PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        powerManager = mockk()
        keyguardManager = mockk()
        wakeLock = mockk()
        every { wakeLock.setReferenceCounted(false) } just runs
        every { wakeLock.acquire(any()) } just runs
        every { wakeLock.isHeld } returns true
        every { wakeLock.release() } just runs
        every { powerManager.newWakeLock(screenOnFlags, any()) } returns wakeLock
        val context =
            mockk<Context> {
                every { getSystemService(PowerManager::class.java) } returns powerManager
                every { getSystemService(KeyguardManager::class.java) } returns keyguardManager
            }
        controller = ScreenPowerControllerImpl(context)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    @DisplayName("lockState maps PowerManager and KeyguardManager flags")
    fun lockStateMapsFlags() {
        every { powerManager.isInteractive } returns false
        every { keyguardManager.isKeyguardLocked } returns true
        every { keyguardManager.isDeviceLocked } returns true
        every { keyguardManager.isDeviceSecure } returns false

        assertEquals(
            ScreenLockState(screenOn = false, lockScreenShowing = true, deviceLocked = true, deviceSecure = false),
            controller.lockState(),
        )
    }

    @Test
    @DisplayName("wakeScreen leaves an already-on screen alone and creates no wake lock")
    fun wakeScreenAlreadyOn() =
        runTest {
            every { powerManager.isInteractive } returns true

            assertEquals(WakeScreenResult.ALREADY_ON, controller.wakeScreen())
            verify(exactly = 0) { powerManager.newWakeLock(any(), any()) }
        }

    @Test
    @DisplayName("wakeScreen acquires a screen-on wake lock, waits for the screen and releases the lock")
    fun wakeScreenTurnsOn() =
        runTest {
            every { powerManager.isInteractive } returnsMany listOf(false, false, true)

            assertEquals(WakeScreenResult.TURNED_ON, controller.wakeScreen())
            verify(exactly = 1) { wakeLock.acquire(any()) }
            verify(exactly = 1) { wakeLock.release() }
        }

    @Test
    @DisplayName("wakeScreen reports STAYED_OFF and still releases the lock when the screen never turns on")
    fun wakeScreenStaysOff() =
        runTest {
            every { powerManager.isInteractive } returns false

            assertEquals(WakeScreenResult.STAYED_OFF, controller.wakeScreen())
            verify(exactly = 1) { wakeLock.release() }
        }
}
