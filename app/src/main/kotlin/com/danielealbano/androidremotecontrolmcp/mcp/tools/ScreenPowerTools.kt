package com.danielealbano.androidremotecontrolmcp.mcp.tools

import com.danielealbano.androidremotecontrolmcp.data.model.ToolPermissionsConfig
import com.danielealbano.androidremotecontrolmcp.mcp.McpToolException
import com.danielealbano.androidremotecontrolmcp.services.power.ScreenLockState
import com.danielealbano.androidremotecontrolmcp.services.power.ScreenPowerController
import com.danielealbano.androidremotecontrolmcp.services.power.WakeScreenResult
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject

/** One `key=value` line per [ScreenLockState] field, in a fixed order. */
internal fun formatScreenLockState(state: ScreenLockState): String =
    listOf(
        "screen_on=${state.screenOn}",
        "lock_screen_showing=${state.lockScreenShowing}",
        "device_locked=${state.deviceLocked}",
        "device_secure=${state.deviceSecure}",
    ).joinToString("\n")

// ─────────────────────────────────────────────────────────────────────────────
// get_lock_state
// ─────────────────────────────────────────────────────────────────────────────

/**
 * MCP tool handler for `get_lock_state`.
 *
 * **Input**: `{}` (no parameters)
 * **Output**: text with `screen_on`, `lock_screen_showing`, `device_locked`, `device_secure` as `key=value` lines.
 */
class GetLockStateHandler
    @Inject
    constructor(
        private val screenPowerController: ScreenPowerController,
    ) {
        @Suppress("UnusedParameter")
        fun execute(arguments: JsonObject?): CallToolResult =
            McpToolUtils.textResult(formatScreenLockState(screenPowerController.lockState()))

        fun register(
            registrar: LoggedToolRegistrar,
            toolNamePrefix: String,
        ) {
            registrar.addTool(
                toolName = TOOL_NAME,
                name = "$toolNamePrefix$TOOL_NAME",
                description =
                    "Reports whether the screen is on (screen_on), the lock screen is showing " +
                        "(lock_screen_showing), a PIN/pattern/password is needed to unlock now (device_locked) " +
                        "and one is set at all (device_secure). Works while the phone is locked.",
                inputSchema =
                    ToolSchema(
                        properties = buildJsonObject {},
                        required = listOf(),
                    ),
            ) { request -> execute(request.arguments) }
        }

        companion object {
            const val TOOL_NAME = "get_lock_state"
        }
    }

// ─────────────────────────────────────────────────────────────────────────────
// wake_screen
// ─────────────────────────────────────────────────────────────────────────────

/**
 * MCP tool handler for `wake_screen`.
 *
 * Turns the screen on without unlocking. Idempotent: an already-on screen is left as it is.
 *
 * **Input**: `{}` (no parameters)
 * **Output**: a status line followed by the [formatScreenLockState] lines.
 * **Errors**:
 *   - ActionFailed if the screen is still off after the wake attempt
 */
class WakeScreenHandler
    @Inject
    constructor(
        private val screenPowerController: ScreenPowerController,
    ) {
        @Suppress("UnusedParameter")
        suspend fun execute(arguments: JsonObject?): CallToolResult {
            val status =
                when (screenPowerController.wakeScreen()) {
                    WakeScreenResult.ALREADY_ON -> {
                        "Screen was already on"
                    }

                    WakeScreenResult.TURNED_ON -> {
                        "Screen turned on"
                    }

                    WakeScreenResult.STAYED_OFF -> {
                        throw McpToolException.ActionFailed(
                            "Screen did not turn on. On Android 15 and later, allow \"Turn screen on\" for this " +
                                "app in Settings > Apps > Special app access.",
                        )
                    }
                }
            return McpToolUtils.textResult("$status\n${formatScreenLockState(screenPowerController.lockState())}")
        }

        fun register(
            registrar: LoggedToolRegistrar,
            toolNamePrefix: String,
        ) {
            registrar.addTool(
                toolName = TOOL_NAME,
                name = "$toolNamePrefix$TOOL_NAME",
                description =
                    "Turns the screen on without unlocking the phone, then returns the same state as " +
                        "$toolNamePrefix${GetLockStateHandler.TOOL_NAME}. If the lock screen is showing, unlock it " +
                        "with the regular tools: swipe up, then tap the PIN keys found with " +
                        "${toolNamePrefix}get_screen_state.",
                inputSchema =
                    ToolSchema(
                        properties = buildJsonObject {},
                        required = listOf(),
                    ),
            ) { request -> execute(request.arguments) }
        }

        companion object {
            const val TOOL_NAME = "wake_screen"
        }
    }

// ─────────────────────────────────────────────────────────────────────────────
// Registration function
// ─────────────────────────────────────────────────────────────────────────────

/** Registers the screen power tools. They do not need the accessibility service. */
fun registerScreenPowerTools(
    registrar: LoggedToolRegistrar,
    screenPowerController: ScreenPowerController,
    toolNamePrefix: String,
    perms: ToolPermissionsConfig,
) {
    if (perms.isToolEnabled(GetLockStateHandler.TOOL_NAME)) {
        GetLockStateHandler(screenPowerController).register(registrar, toolNamePrefix)
    }
    if (perms.isToolEnabled(WakeScreenHandler.TOOL_NAME)) {
        WakeScreenHandler(screenPowerController).register(registrar, toolNamePrefix)
    }
}
