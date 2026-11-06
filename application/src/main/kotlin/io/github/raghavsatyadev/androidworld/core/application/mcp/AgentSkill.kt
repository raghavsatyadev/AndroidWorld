package io.github.raghavsatyadev.androidworld.core.application.mcp

/** Folder and `name` of the skill agents install (task 066). */
const val AGENT_SKILL_NAME = "androidworld"

/** The name the server reports and the setup snippets register it under. */
const val MCP_SERVER_NAME = "androidworld"

/**
 * `SKILL.md` in the Agent Skills format (Claude Code, Codex CLI, Gemini CLI) that tells an agent when
 * to use the AndroidWorld MCP server and how to use it cheaply (task 066). Generated from [tools], so
 * it lists exactly what the server offers.
 */
fun agentSkill(tools: List<McpTool>): String = buildString {
    appendLine("---")
    appendLine("name: $AGENT_SKILL_NAME")
    appendLine(
        "description: See and drive the Android device or emulator selected in the AndroidWorld desktop app " +
            "through its MCP server androidworld: screenshots, UI tree, tap/type/swipe, device settings, apps, logcat, " +
            "SharedPreferences and databases. Use to test, debug or verify an Android app on a device or walk through a user flow.",
    )
    appendLine("---")
    appendLine()
    appendLine("# AndroidWorld device control")
    appendLine()
    appendLine("The `androidworld` MCP server runs inside the AndroidWorld desktop app.")
    appendLine("If its tools are missing, ask the user to turn it on in")
    appendLine("AndroidWorld › Settings › MCP server and to click Connect Claude Code (or add the URL and token by hand).")
    appendLine()
    appendLine("## Workflow")
    appendLine()
    appendLine("- Tools act on the device selected in AndroidWorld. Use `list_devices` and pass `serial` for another one.")
    appendLine("- Read the screen with `get_ui` (`interactive_only=true` or `query=` keep it short) before tapping; prefer it to")
    appendLine("  `screenshot`, which costs far more tokens. Use `screenshot` to check visuals.")
    appendLine("- Tap by `ref` from the last `get_ui`, or by `text`. Positions are dp, never pixels.")
    appendLine("- Pass `return_ui=true` to `tap`, `swipe`, `type_text` and `press_key` to get the next screen in the same call.")
    appendLine("- When the UI tree does not show what you see (games, canvas, Flutter, some WebViews), call")
    appendLine("  `screenshot annotate=true` and tap the numbered shapes with `mark=N`.")
    appendLine("- If the UI cannot be read because the screen keeps animating, `set_device_settings animations=false`.")
    appendLine("- After a crash or wrong behaviour, read `logcat` (`package=`, `level=E`, or `crash=true`).")
    appendLine("- `manage_app` clear_data / uninstall wait for the user to confirm in AndroidWorld; a refusal is final, do not retry.")
    appendLine("- Restore device settings you changed (locale `reset`, animations, font scale) when you are done.")
    appendLine()
    appendLine("## Tools")
    appendLine()
    tools.forEach { tool ->
        val level = when {
            tool.destructive -> "full control, asks first"
            tool.readOnly -> "read only"
            else -> "full control"
        }
        appendLine("- `${tool.name}` ($level): ${tool.description}")
    }
}
