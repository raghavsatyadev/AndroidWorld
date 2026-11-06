package io.github.raghavsatyadev.androidworld.core.application.mcp.tools

import io.github.raghavsatyadev.androidworld.core.application.mcp.McpTool

/** Every MCP tool, grouped See / Act / Device / Apps / Data (design §11). */
fun mcpTools(env: McpToolEnvironment): List<McpTool> = screenAndInputTools(env) + deviceAppDataTools(env)

/** Tool names and metadata only (Settings' "Exposed tools"), without a device or any use case behind them. */
fun mcpCatalog(): List<McpTool> = mcpTools(catalogEnvironment)

private val catalogEnvironment: McpToolEnvironment by lazy {
    val transport = io.github.raghavsatyadev.androidworld.core.domain.adb.FakeAdbTransport()
    McpToolEnvironment(
        transport = transport,
        devices = io.github.raghavsatyadev.androidworld.core.domain.device.FakeDeviceRepository(),
        selected = kotlinx.coroutines.flow.MutableStateFlow(io.github.raghavsatyadev.androidworld.core.domain.device.SelectedDeviceState.None),
        layout = io.github.raghavsatyadev.androidworld.core.application.layout.CaptureLayoutUseCase(transport),
        imageScaler = { _, _, _ -> null },
        marker = object : io.github.raghavsatyadev.androidworld.core.domain.capture.ScreenMarker {
            override fun detect(png: ByteArray) = emptyList<io.github.raghavsatyadev.androidworld.core.domain.capture.MarkBox>()
            override fun draw(png: ByteArray, marks: List<io.github.raghavsatyadev.androidworld.core.domain.capture.ScreenMark>): ByteArray? = null
        },
        lifecycle = io.github.raghavsatyadev.androidworld.core.application.apps.AppLifecycleUseCase(transport),
        clearData = io.github.raghavsatyadev.androidworld.core.application.apps.ClearDataUseCase(transport),
        uninstall = io.github.raghavsatyadev.androidworld.core.application.apps.UninstallUseCase(transport),
        currentApp = io.github.raghavsatyadev.androidworld.core.application.currentapp.CurrentAppUseCase(transport),
        locale = io.github.raghavsatyadev.androidworld.core.application.locale.DeviceLocaleUseCase(
            object : io.github.raghavsatyadev.androidworld.core.domain.locale.DeviceLocalePort {
                override suspend fun run(serial: io.github.raghavsatyadev.androidworld.core.domain.adb.DeviceSerial, action: io.github.raghavsatyadev.androidworld.core.domain.locale.LocaleAction) =
                    io.github.raghavsatyadev.androidworld.core.domain.locale.LocaleRead.Failed("catalog")
            },
            io.github.raghavsatyadev.androidworld.core.application.locale.InMemoryOriginalLocaleStore(),
        ),
        location = io.github.raghavsatyadev.androidworld.core.application.locale.EmulatorLocationUseCase(transport),
        appData = io.github.raghavsatyadev.androidworld.core.application.appdetails.AppDataReader(
            transport,
            object : io.github.raghavsatyadev.androidworld.core.domain.appdata.AppDatabaseTransfer {
                override suspend fun pull(serial: io.github.raghavsatyadev.androidworld.core.domain.adb.DeviceSerial, access: io.github.raghavsatyadev.androidworld.core.domain.appdata.AppDataAccess, packageName: String, fileName: String) =
                    io.github.raghavsatyadev.androidworld.core.domain.appdata.DatabaseCopy.Failed("catalog")

                override suspend fun push(serial: io.github.raghavsatyadev.androidworld.core.domain.adb.DeviceSerial, access: io.github.raghavsatyadev.androidworld.core.domain.appdata.AppDataAccess, packageName: String, fileName: String, localPath: String): String? = null
            },
            object : io.github.raghavsatyadev.androidworld.core.domain.appdata.SqliteEngine {
                override suspend fun open(localPath: String) = error("catalog")
            },
        ),
        confirmation = { _, _, _, _ -> false },
    )
}
