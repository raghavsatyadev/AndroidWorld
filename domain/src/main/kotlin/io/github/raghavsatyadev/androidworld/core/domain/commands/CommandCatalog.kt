package io.github.raghavsatyadev.androidworld.core.domain.commands

import io.github.raghavsatyadev.androidworld.core.domain.commands.CommandCategory.Apps
import io.github.raghavsatyadev.androidworld.core.domain.commands.CommandCategory.Battery
import io.github.raghavsatyadev.androidworld.core.domain.commands.CommandCategory.Connectivity
import io.github.raghavsatyadev.androidworld.core.domain.commands.CommandCategory.Diagnostics
import io.github.raghavsatyadev.androidworld.core.domain.commands.CommandCategory.Display
import io.github.raghavsatyadev.androidworld.core.domain.commands.CommandCategory.Input
import io.github.raghavsatyadev.androidworld.core.domain.commands.CommandCategory.Sound
import io.github.raghavsatyadev.androidworld.core.domain.commands.CommandCategory.System
import io.github.raghavsatyadev.androidworld.core.domain.commands.CommandRisk.Destructive
import io.github.raghavsatyadev.androidworld.core.domain.commands.CommandRisk.Read
import io.github.raghavsatyadev.androidworld.core.domain.commands.CommandRisk.Reversible
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The command browser's catalog: shell commands worth having one click away, grouped by category.
 *
 * Entries were checked against an API 37
 * emulator and reworked: commands Android removed (`wm overscan`) or settings that no longer do
 * anything when written (`install_non_market_apps`, `usb_function`, raw volume and zen settings) are
 * replaced by the `cmd` service calls that do; commands other screens already cover (density, font
 * scale, proxy, deep links, screenshots, recording, files) are left out; and every entry carries a
 * risk so destructive ones are confirmed.
 */
object CommandCatalog {
    private val onOff = FieldKind.Choices(listOf(Choice("0", "Off"), Choice("1", "On")))
    private val enableDisable = FieldKind.Choices(listOf(Choice("enable", "On"), Choice("disable", "Off")))
    private val packageName = FieldKind.Text(Regex("""[A-Za-z][\w]*(\.[A-Za-z_][\w]*)+"""), "A package name, like com.example.app")
    private val packageField = CommandField("Package", packageName, "com.example.app")
    private val coordinate = FieldKind.Integer(0, 20_000)

    val entries: List<CommandEntry> = listOf(
        // Display
        entry("wm-size", Display, "Screen size", "Override the screen resolution, or reset it.", "wm size {0}", Destructive,
            fields = listOf(CommandField("Size", FieldKind.Text(Regex("""\d{2,5}x\d{2,5}|reset"""), "Like 1080x2400, or reset"), "1080x2400 or reset")),
            current = "wm size", warning = "A wrong size can make the screen hard to use. Run it with reset to go back."),
        entry("user-rotation", Display, "Rotation", "Lock the screen to one rotation, or let it follow the sensor.",
            "wm user-rotation lock {0}", Reversible,
            fields = listOf(CommandField("Rotation", FieldKind.Choices(listOf(Choice("0", "Portrait"), Choice("1", "Landscape"), Choice("2", "Upside down"), Choice("3", "Landscape, flipped"))))),
            current = "wm user-rotation"),
        setting("auto-rotate", Display, "Auto-rotate", "Rotate the screen with the device.", "system", "accelerometer_rotation", onOff),
        setting("brightness", Display, "Brightness", "Screen brightness, 0 to 255.", "system", "screen_brightness",
            FieldKind.Integer(0, 255), hint = "0–255"),
        setting("screen-timeout", Display, "Screen timeout", "How long the screen stays on without input.", "system", "screen_off_timeout",
            FieldKind.Choices(listOf(Choice("15000", "15 seconds"), Choice("30000", "30 seconds"), Choice("60000", "1 minute"), Choice("120000", "2 minutes"), Choice("300000", "5 minutes"), Choice("600000", "10 minutes"), Choice("1800000", "30 minutes")))),
        entry("night-mode", Display, "Dark theme", "Switch the system dark theme.", "cmd uimode night {0}", Reversible,
            fields = listOf(CommandField("Mode", FieldKind.Choices(listOf(Choice("yes", "Dark"), Choice("no", "Light"), Choice("auto", "Automatic"))))),
            current = "cmd uimode night"),
        setting("force-rtl", Display, "Right-to-left layout", "Mirror every layout as if the language were right-to-left.", "global", "debug.force_rtl", onOff),

        // System
        entry("airplane", System, "Airplane mode", "Turn every radio off or on.", "cmd connectivity airplane-mode {0}", Destructive,
            fields = listOf(CommandField("Airplane mode", FieldKind.Choices(listOf(Choice("enable", "On"), Choice("disable", "Off"))))),
            current = "cmd connectivity airplane-mode",
            warning = "A device connected to adb over Wi-Fi drops off until airplane mode is turned off on the device."),
        entry("location", System, "Location", "Turn location services on or off.", "cmd location set-location-enabled {0}", Reversible,
            fields = listOf(CommandField("Location", FieldKind.Choices(listOf(Choice("true", "On"), Choice("false", "Off"))))),
            current = "cmd location is-location-enabled"),
        entry("dnd", System, "Do not disturb", "Silence notifications, or allow only some.", "cmd notification set_dnd {0}", Reversible,
            fields = listOf(CommandField("Mode", FieldKind.Choices(listOf(Choice("off", "Off"), Choice("priority", "Priority only"), Choice("alarms", "Alarms only"), Choice("on", "Total silence"))))),
            current = "settings get global zen_mode"),
        setting("auto-time", System, "Automatic date and time", "Take the time from the network.", "global", "auto_time", onOff),
        setting("haptics", System, "Touch vibration", "Vibrate on taps and key presses.", "system", "haptic_feedback_enabled", onOff),

        // Input
        entry("keyevent", Input, "Press a key", "Send a key press.", "input keyevent {0}", Reversible,
            fields = listOf(CommandField("Key", FieldKind.Choices(listOf(Choice("3", "Home"), Choice("4", "Back"), Choice("187", "Recent apps"), Choice("82", "Menu"), Choice("66", "Enter"), Choice("61", "Tab"), Choice("67", "Delete"), Choice("24", "Volume up"), Choice("25", "Volume down"), Choice("164", "Mute"), Choice("26", "Power"), Choice("223", "Sleep"), Choice("224", "Wake up"), Choice("85", "Play/pause"), Choice("27", "Camera"))))),),
        entry("tap", Input, "Tap", "Tap a point on the screen, in pixels.", "input tap {0} {1}", Reversible,
            fields = listOf(CommandField("X", coordinate, "500"), CommandField("Y", coordinate, "1200"))),
        entry("swipe", Input, "Swipe", "Swipe from one point to another.", "input swipe {0} {1} {2} {3} {4}", Reversible,
            fields = listOf(CommandField("From X", coordinate), CommandField("From Y", coordinate), CommandField("To X", coordinate), CommandField("To Y", coordinate),
                CommandField("Duration (ms)", FieldKind.Integer(1, 10_000), default = "300"))),
        entry("long-press", Input, "Long press", "Hold a point on the screen.", "input swipe {0} {1} {0} {1} {2}", Reversible,
            fields = listOf(CommandField("X", coordinate), CommandField("Y", coordinate), CommandField("Duration (ms)", FieldKind.Integer(400, 10_000), default = "800"))),
        entry("text", Input, "Type text", "Type into the focused field.", "input text {0}", Reversible,
            fields = listOf(CommandField("Text", FieldKind.Text(), "Hello"))),
        setting("show-touches", Input, "Show taps", "Draw a dot where the screen is touched.", "system", "show_touches", onOff),
        setting("pointer-location", Input, "Pointer location", "Overlay the touch coordinates.", "system", "pointer_location", onOff),

        // Battery
        entry("battery-level", Battery, "Battery level", "Pretend the battery is at a level.", "dumpsys battery set level {0}", Reversible,
            fields = listOf(CommandField("Level (%)", FieldKind.Integer(0, 100), "0–100")), current = "dumpsys battery", currentLine = "level:"),
        entry("battery-status", Battery, "Charging status", "Pretend the battery is in a state.", "dumpsys battery set status {0}", Reversible,
            fields = listOf(CommandField("Status", FieldKind.Choices(listOf(Choice("2", "Charging"), Choice("3", "Discharging"), Choice("4", "Not charging"), Choice("5", "Full"), Choice("1", "Unknown"))))),
            current = "dumpsys battery", currentLine = "status:"),
        entry("battery-temp", Battery, "Battery temperature", "Pretend a temperature, in tenths of a degree Celsius.", "dumpsys battery set temp {0}", Reversible,
            fields = listOf(CommandField("Temperature", FieldKind.Integer(-200, 1000), "350 is 35.0 °C")), current = "dumpsys battery", currentLine = "temperature:"),
        entry("battery-unplug", Battery, "Unplug", "Pretend the charger is unplugged.", "dumpsys battery unplug", Reversible,
            current = "dumpsys battery", currentLine = "AC powered:"),
        entry("battery-reset", Battery, "Reset battery", "Go back to the real battery readings.", "dumpsys battery reset", Reversible),
        entry("battery-saver", Battery, "Battery saver", "Turn battery saver on or off.", "cmd power set-mode {0}", Reversible,
            fields = listOf(CommandField("Battery saver", onOff)), current = "settings get global low_power"),

        // Connectivity
        entry("wifi", Connectivity, "Wi-Fi", "Turn Wi-Fi on or off.", "svc wifi {0}", Destructive,
            fields = listOf(CommandField("Wi-Fi", enableDisable)), current = "settings get global wifi_on",
            warning = "A device connected to adb over Wi-Fi drops off when Wi-Fi goes off and can only be reconnected on the device."),
        entry("mobile-data", Connectivity, "Mobile data", "Turn mobile data on or off.", "svc data {0}", Reversible,
            fields = listOf(CommandField("Mobile data", enableDisable)), current = "settings get global mobile_data"),
        entry("bluetooth", Connectivity, "Bluetooth", "Turn Bluetooth on or off.", "svc bluetooth {0}", Reversible,
            fields = listOf(CommandField("Bluetooth", enableDisable)), current = "settings get global bluetooth_on"),
        entry("nfc", Connectivity, "NFC", "Turn NFC on or off.", "svc nfc {0}", Reversible, fields = listOf(CommandField("NFC", enableDisable))),
        setting("roaming", Connectivity, "Data roaming", "Use mobile data while roaming.", "global", "data_roaming", onOff),
        entry("wifi-country", Connectivity, "Wi-Fi country", "The country code Wi-Fi uses for its channels.", "cmd wifi get-country-code", Read),

        // Sound
        entry("volume", Sound, "Volume", "Set the volume of one stream.", "cmd media_session volume --stream {0} --set {1}", Reversible,
            fields = listOf(streamField(), CommandField("Volume", FieldKind.Integer(0, 30), "0–15 on most devices")),
            current = "cmd media_session volume --stream {0} --get", currentLine = "volume is"),

        // Apps
        entry("list-packages", Apps, "List packages", "List installed packages.", "pm list packages {0}", Read,
            fields = listOf(CommandField("Which", FieldKind.Choices(listOf(Choice("", "All"), Choice("-3", "Downloaded"), Choice("-s", "System"), Choice("-d", "Disabled"), Choice("-e", "Enabled"))), optional = true))),
        entry("apk-path", Apps, "APK path", "Where a package's APKs are.", "pm path {0}", Read, fields = listOf(packageField)),
        entry("package-info", Apps, "Package details", "Everything the package manager knows about a package.", "dumpsys package {0}", Read,
            fields = listOf(packageField), timeout = 30.seconds),
        entry("app-ops", Apps, "App ops", "A package's app-op modes.", "appops get {0}", Read, fields = listOf(packageField)),
        entry("enable-app", Apps, "Enable app", "Enable a disabled package.", "pm enable {0}", Reversible, fields = listOf(packageField)),
        entry("disable-app", Apps, "Disable app", "Disable a package for the current user.", "pm disable-user --user 0 {0}", Destructive,
            fields = listOf(packageField), warning = "Disabling a system package can break the device until it is enabled again."),
        entry("grant", Apps, "Grant permission", "Grant a runtime permission.", "pm grant {0} {1}", Reversible,
            fields = listOf(packageField, permissionField())),
        entry("revoke", Apps, "Revoke permission", "Revoke a runtime permission.", "pm revoke {0} {1}", Destructive,
            fields = listOf(packageField, permissionField()), warning = "The app is stopped when a permission is revoked."),
        entry("has-feature", Apps, "Has feature", "Whether the device declares a hardware or software feature.", "pm has-feature {0}", Read,
            fields = listOf(CommandField("Feature", FieldKind.Text(Regex("""[\w.]+"""), "Like android.hardware.camera"), "android.hardware.camera"))),
        entry("dial", Apps, "Open dialer", "Open the dialer with a number filled in. Nothing is called.", "am start -a android.intent.action.DIAL -d tel:{0}", Reversible,
            fields = listOf(CommandField("Number", FieldKind.Text(Regex("""[\d+*#\s-]+"""), "Digits, +, * and #"), "+15551234567"))),

        // Diagnostics
        entry("getprop", Diagnostics, "System property", "Read one system property, or all of them.", "getprop {0}", Read,
            fields = listOf(CommandField("Property", FieldKind.Text(Regex("""[\w.\-]+"""), "Like ro.build.version.sdk"), "Leave empty for all", optional = true))),
        entry("top", Diagnostics, "Busiest processes", "One snapshot of the processes using the most CPU.", "top -b -n 1 -m {0}", Read,
            fields = listOf(CommandField("How many", FieldKind.Integer(1, 100), default = "10"))),
        entry("meminfo", Diagnostics, "Memory", "Memory use of the device, or of one package.", "dumpsys meminfo {0}", Read,
            fields = listOf(CommandField("Package", packageName, "Leave empty for the whole device", optional = true)), timeout = 30.seconds),
        entry("pidof", Diagnostics, "Process ID", "The process ID of a running package.", "pidof {0}", Read, fields = listOf(packageField)),
        entry("proc-status", Diagnostics, "Process status", "The kernel's view of one process.", "cat /proc/{0}/status", Read,
            fields = listOf(CommandField("PID", FieldKind.Integer(1, 4_194_304)))),
        entry("df", Diagnostics, "Storage", "Free and used space on each partition.", "df -h", Read),
        entry("uptime", Diagnostics, "Uptime", "How long the device has been on, and its load.", "uptime", Read),
        entry("ifconfig", Diagnostics, "Network interfaces", "Addresses of every network interface.", "ifconfig", Read),
        entry("ping", Diagnostics, "Ping", "Ping a host from the device, four times.", "ping -c 4 {0}", Read,
            fields = listOf(CommandField("Host", FieldKind.Text(Regex("""[\w.\-:]+"""), "A host name or address"), "google.com")), timeout = 30.seconds),
    )

    private fun entry(
        id: String,
        category: CommandCategory,
        name: String,
        description: String,
        action: String,
        risk: CommandRisk,
        fields: List<CommandField> = emptyList(),
        current: String? = null,
        currentLine: String? = null,
        warning: String? = null,
        timeout: Duration = 15.seconds,
    ) = CommandEntry(
        id = id,
        category = category,
        name = name,
        description = description,
        action = CommandTemplate.parse(action),
        risk = risk,
        fields = fields,
        current = current?.let(CommandTemplate::parse),
        currentLine = currentLine,
        warning = warning,
        timeout = timeout,
    )

    /** An entry that reads and writes one `settings` key. */
    private fun setting(
        id: String,
        category: CommandCategory,
        name: String,
        description: String,
        namespace: String,
        key: String,
        kind: FieldKind,
        hint: String = "",
    ) = entry(id, category, name, description, "settings put $namespace $key {0}", Reversible,
        fields = listOf(CommandField(name, kind, hint)), current = "settings get $namespace $key")

    private fun streamField() = CommandField("Stream", FieldKind.Choices(listOf(Choice("3", "Media"), Choice("2", "Ring"), Choice("5", "Notifications"), Choice("4", "Alarm"), Choice("0", "Calls"))), default = "3")

    private fun permissionField() = CommandField("Permission", FieldKind.Text(Regex("""[\w.]+"""), "Like android.permission.CAMERA"), "android.permission.CAMERA")
}
