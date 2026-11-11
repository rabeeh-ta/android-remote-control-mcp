package com.danielealbano.androidremotecontrolmcp.mcp.tools

import com.danielealbano.androidremotecontrolmcp.mcp.McpToolException
import com.danielealbano.androidremotecontrolmcp.services.power.ScreenLockState
import com.danielealbano.androidremotecontrolmcp.services.power.ScreenPowerController
import com.danielealbano.androidremotecontrolmcp.services.power.WakeScreenResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("Screen Power Tools")
class ScreenPowerToolsTest {
    private lateinit var controller: ScreenPowerController

    private val lockedState =
        ScreenLockState(screenOn = true, lockScreenShowing = true, deviceLocked = true, deviceSecure = true)

    private val lockedStateText =
        "screen_on=true\nlock_screen_showing=true\ndevice_locked=true\ndevice_secure=true"

    @BeforeEach
    fun setUp() {
        controller = mockk()
        every { controller.lockState() } returns lockedState
    }

    private fun CallToolResult.text(): String = (content.single() as TextContent).text

    @Test
    @DisplayName("formatScreenLockState writes every field as a key=value line in a fixed order")
    fun formatsEveryField() {
        val state =
            ScreenLockState(screenOn = false, lockScreenShowing = true, deviceLocked = false, deviceSecure = true)

        assertEquals(
            "screen_on=false\nlock_screen_showing=true\ndevice_locked=false\ndevice_secure=true",
            formatScreenLockState(state),
        )
    }

    @Nested
    @DisplayName("GetLockStateHandler")
    inner class GetLockStateTests {
        @Test
        @DisplayName("returns the controller's lock state")
        fun returnsLockState() {
            val result = GetLockStateHandler(controller).execute(null)

            assertEquals(lockedStateText, result.text())
            verify(exactly = 1) { controller.lockState() }
        }
    }

    @Nested
    @DisplayName("WakeScreenHandler")
    inner class WakeScreenTests {
        private lateinit var handler: WakeScreenHandler

        @BeforeEach
        fun setUp() {
            handler = WakeScreenHandler(controller)
        }

        @Test
        @DisplayName("reports a screen that was already on, followed by the lock state")
        fun reportsAlreadyOn() =
            runTest {
                coEvery { controller.wakeScreen() } returns WakeScreenResult.ALREADY_ON

                assertEquals("Screen was already on\n$lockedStateText", handler.execute(null).text())
            }

        @Test
        @DisplayName("reports a screen it turned on, followed by the lock state")
        fun reportsTurnedOn() =
            runTest {
                coEvery { controller.wakeScreen() } returns WakeScreenResult.TURNED_ON

                assertEquals("Screen turned on\n$lockedStateText", handler.execute(null).text())
            }

        @Test
        @DisplayName("throws ActionFailed pointing to the Turn screen on access when the screen stays off")
        fun throwsWhenScreenStaysOff() =
            runTest {
                coEvery { controller.wakeScreen() } returns WakeScreenResult.STAYED_OFF

                val exception = assertThrows<McpToolException.ActionFailed> { handler.execute(null) }
                assertTrue(exception.message!!.contains("Turn screen on"))
            }
    }
}
