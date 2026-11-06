package io.github.raghavsatyadev.androidworld.desktop.mcp

import io.github.raghavsatyadev.androidworld.desktop.persistence.AppStore
import io.github.raghavsatyadev.androidworld.core.adapters.jvm.mcp.LoopbackMcpHttpServer
import io.github.raghavsatyadev.androidworld.core.adapters.jvm.mcp.McpHttpHandler
import io.github.raghavsatyadev.androidworld.core.adapters.jvm.mcp.McpHttpReply
import io.github.raghavsatyadev.androidworld.core.adapters.jvm.mcp.McpServerStart
import io.github.raghavsatyadev.androidworld.core.application.mcp.ClaudeCodeConnection
import io.github.raghavsatyadev.androidworld.core.application.mcp.ClaudeCodeConnector
import io.github.raghavsatyadev.androidworld.core.application.mcp.McpAccess
import io.github.raghavsatyadev.androidworld.core.application.mcp.McpServerCore
import io.github.raghavsatyadev.androidworld.core.application.mcp.McpSession
import io.github.raghavsatyadev.androidworld.core.application.mcp.McpTool
import io.github.raghavsatyadev.androidworld.core.application.mcp.tools.DestructiveAppAction
import io.github.raghavsatyadev.androidworld.core.application.mcp.tools.McpConfirmation
import io.github.raghavsatyadev.androidworld.core.domain.adb.DeviceSerial
import io.github.raghavsatyadev.androidworld.desktop.confirm.ConfirmRequest
import io.github.raghavsatyadev.androidworld.desktop.confirm.ConfirmationQueue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class McpStatus(
    val access: McpAccess = McpAccess.Off,
    val port: Int? = null,
    val error: String? = null,
    val sessions: List<McpSession> = emptyList(),
    val toolCount: Int = 0,
) {
    val running: Boolean get() = port != null
    val url: String? get() = port?.let { "http://127.0.0.1:$it/mcp" }
}

/**
 * The app's MCP server (minus the IDE): runs on loopback while access is not Off,
 * using the persisted port and bearer token, and asks the user before Clear data / Uninstall.
 */
class McpController(
    private val store: AppStore,
    private val version: String,
    private val connector: ClaudeCodeConnector,
    private val deviceLabel: (DeviceSerial) -> String,
    private val confirmations: ConfirmationQueue,
) : McpConfirmation {
    private var core: McpServerCore? = null
    private var tools: List<McpTool> = emptyList()

    private val _status = MutableStateFlow(McpStatus(access = access()))
    val status: StateFlow<McpStatus> = _status.asStateFlow()

    private val http = LoopbackMcpHttpServer(
        handler = object : McpHttpHandler {
            override suspend fun handle(body: String, sessionId: String?): McpHttpReply {
                val server = core
                    ?: return McpHttpReply("""{"jsonrpc":"2.0","id":null,"error":{"code":-32603,"message":"Server not ready"}}""")
                val reply = server.handle(body, sessionId)
                publish()
                return McpHttpReply(reply.body, reply.sessionId)
            }

            override fun endSession(sessionId: String) {
                core?.endSession(sessionId)
                publish()
            }
        },
        token = { store.mcpToken() },
    )

    fun access(): McpAccess = runCatching { McpAccess.valueOf(store.state.mcpAccess) }.getOrDefault(McpAccess.Off)

    @Synchronized
    fun install(tools: List<McpTool>) {
        this.tools = tools
        core = McpServerCore(tools, ::access, version)
        apply()
    }

    fun toolCatalog(): List<McpTool> = tools

    @Synchronized
    fun setAccess(access: McpAccess) {
        store.update { it.copy(mcpAccess = access.name) }
        apply()
    }

    /** Starts, keeps or stops the server for the stored access level. */
    @Synchronized
    fun apply() {
        val access = access()
        if (access == McpAccess.Off || core == null) {
            http.stop()
            core?.endAllSessions()
            _status.value = McpStatus(access = access, toolCount = toolCountFor(access))
            return
        }
        if (http.port == null) {
            when (val started = http.start(store.state.mcpPort)) {
                is McpServerStart.Listening -> {
                    store.update { it.copy(mcpPort = started.port) }
                    _status.value = McpStatus(access = access, port = started.port, toolCount = toolCountFor(access))
                }
                is McpServerStart.Failed -> _status.value = McpStatus(access = access, error = started.reason, toolCount = toolCountFor(access))
            }
        } else {
            publish()
        }
    }

    fun token(): String = store.mcpToken()

    fun regenerateToken(): String {
        core?.endAllSessions()
        return store.regenerateMcpToken().also { publish() }
    }

    suspend fun connectClaudeCode(): ClaudeCodeConnection {
        val url = status.value.url ?: return ClaudeCodeConnection.Failed("Turn the MCP server on first")
        return connector.connect(url, token())
    }

    override suspend fun confirm(action: DestructiveAppAction, serial: DeviceSerial, packageName: String, agent: String): Boolean {
        val verb = if (action == DestructiveAppAction.Uninstall) "uninstall" else "clear the data of"
        return confirmations.ask(
            ConfirmRequest(
                title = "Allow $agent?",
                message = "$agent wants to $verb $packageName on ${deviceLabel(serial)}.",
                confirmLabel = "Allow",
                note = "This can't be undone. Unanswered requests are refused after 60 s.",
            ),
            timeout = CONFIRM_TIMEOUT,
        )
    }

    fun stop() = http.stop()

    private fun toolCountFor(access: McpAccess): Int =
        if (access == McpAccess.ReadOnly) tools.count { it.readOnly } else tools.size

    private fun publish() {
        val cutoff = System.currentTimeMillis() - ACTIVE_WINDOW_MS
        _status.value = _status.value.copy(
            access = access(),
            toolCount = toolCountFor(access()),
            port = http.port,
            sessions = core?.sessions()?.filter { (it.lastCallAtMillis ?: System.currentTimeMillis()) >= cutoff }.orEmpty(),
        )
    }

    private companion object {
        const val ACTIVE_WINDOW_MS = 5 * 60 * 1000L
        val CONFIRM_TIMEOUT = 60.seconds
    }
}
