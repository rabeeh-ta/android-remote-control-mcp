package com.danielealbano.androidremotecontrolmcp.e2e

import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * E2E test: lock_screen, wake_screen and get_lock_state on a real Android system.
 *
 * The redroid device runs without a keyguard, so locking only turns the screen off and the lock screen never
 * shows; the lock screen fields are covered by the unit tests. [restoreScreen] wakes the device and dismisses any
 * keyguard after every test so the following test classes find an awake, unlocked device.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class E2EScreenPowerTest {
    companion object {
        private const val TOOL_PREFIX = AndroidContainerSetup.TOOL_NAME_PREFIX
        private const val STATE_TIMEOUT_MS = 15_000L
        private const val POLL_INTERVAL_MS = 500L
    }

    private val mcpClient = SharedAndroidContainer.mcpClient

    @BeforeEach
    fun setUp() {
        restoreScreen()
        SharedAndroidContainer.ensureAccessibilityService()
    }

    @AfterEach
    fun restoreScreen() {
        AndroidContainerSetup.execAdb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
        AndroidContainerSetup.execAdb("shell", "wm", "dismiss-keyguard")
    }

    private fun CallToolResult.text(): String = (content.firstOrNull() as? TextContent)?.text ?: ""

    private suspend fun lockState(): Map<String, String> {
        val result = mcpClient.callTool("${TOOL_PREFIX}get_lock_state")
        assertNotEquals(true, result.isError, "get_lock_state failed: ${result.text()}")
        return result
            .text()
            .lines()
            .filter { it.contains('=') }
            .associate { it.substringBefore('=') to it.substringAfter('=') }
    }

    private suspend fun awaitScreenOn(expected: Boolean): Map<String, String> {
        val deadline = System.currentTimeMillis() + STATE_TIMEOUT_MS
        var state = lockState()
        while (state["screen_on"] != expected.toString() && System.currentTimeMillis() < deadline) {
            delay(POLL_INTERVAL_MS)
            state = lockState()
        }
        return state
    }

    @Test
    fun `get_lock_state reports every field on an awake unlocked device`() =
        runBlocking {
            val state = awaitScreenOn(true)

            assertEquals(
                setOf("screen_on", "lock_screen_showing", "device_locked", "device_secure"),
                state.keys,
            )
            assertEquals("true", state["screen_on"])
            assertEquals("false", state["device_locked"])
        }

    @Test
    fun `wake_screen on an awake device reports it was already on`() =
        runBlocking {
            awaitScreenOn(true)

            val result = mcpClient.callTool("${TOOL_PREFIX}wake_screen")

            assertNotEquals(true, result.isError, "wake_screen failed: ${result.text()}")
            assertTrue(result.text().startsWith("Screen was already on"), result.text())
        }

    @Test
    fun `lock_screen turns the screen off and wake_screen turns it back on`() =
        runBlocking {
            awaitScreenOn(true)

            val lock = mcpClient.callTool("${TOOL_PREFIX}lock_screen")
            assertNotEquals(true, lock.isError, "lock_screen failed: ${lock.text()}")
            val locked = awaitScreenOn(false)
            assertEquals("false", locked["screen_on"], "screen still on after lock_screen: $locked")

            val wake = mcpClient.callTool("${TOOL_PREFIX}wake_screen")
            assertNotEquals(true, wake.isError, "wake_screen failed: ${wake.text()}")
            assertTrue(wake.text().startsWith("Screen turned on"), wake.text())
            val woken = lockState()
            assertEquals("true", woken["screen_on"], "screen off after wake_screen: $woken")
        }
}
