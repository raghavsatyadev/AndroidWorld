package io.github.raghavsatyadev.androidworld.core.domain.files

import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbDeviceRequest
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbOperation
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbShellCommand
import io.github.raghavsatyadev.androidworld.core.domain.adb.DeviceSerial
import io.github.raghavsatyadev.androidworld.core.domain.adb.ShellToken
import io.github.raghavsatyadev.androidworld.core.domain.adb.ShellValue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Whose files the browser shows. [Device]: the device file system as the adb shell user. [App]: a
 * debuggable app's private data, reached through `run-as`, which runs each command as the app's uid.
 */
sealed interface FileScope {
    val home: String

    data object Device : FileScope {
        override val home: String = "/sdcard"
    }

    data class App(val packageName: String) : FileScope {
        init {
            require(PACKAGE_NAME.matches(packageName)) { "not a package name: '$packageName'" }
        }

        override val home: String = "/data/data/$packageName"
    }

    companion object {
        val PACKAGE_NAME = Regex("""^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$""")
    }
}

/**
 * Request builders for the file browser. Every path goes through a [ShellToken.Value], so names with
 * spaces or quotes are quoted once by [AdbShellCommand.render]. In [FileScope.App] each command is
 * prefixed with `run-as <package>`. Push and pull use the adb sync protocol ([AdbOperation.Host]),
 * whose argv is never shell-parsed.
 */
object FileCommands {
    val LIST_TIMEOUT: Duration = 20.seconds
    val EDIT_TIMEOUT: Duration = 60.seconds
    val TRANSFER_TIMEOUT: Duration = 60.minutes

    /** Lists `path/`, so a symlinked folder such as `/sdcard` shows its contents, not the link. */
    fun list(serial: DeviceSerial, scope: FileScope, path: String): AdbDeviceRequest {
        val target = RemotePath.normalize(path).let { if (it == RemotePath.ROOT) it else "$it/" }
        return shell(serial, scope, LIST_TIMEOUT, literal("ls"), literal("-la"), literal("--"), value(target))
    }

    fun makeFolder(serial: DeviceSerial, scope: FileScope, path: String): AdbDeviceRequest =
        shell(serial, scope, EDIT_TIMEOUT, literal("mkdir"), literal("-p"), literal("--"), value(guarded(path)))

    fun rename(serial: DeviceSerial, scope: FileScope, from: String, to: String): AdbDeviceRequest =
        shell(serial, scope, EDIT_TIMEOUT, literal("mv"), literal("--"), value(guarded(from)), value(guarded(to)))

    fun delete(serial: DeviceSerial, scope: FileScope, path: String): AdbDeviceRequest =
        shell(serial, scope, EDIT_TIMEOUT, literal("rm"), literal("-rf"), literal("--"), value(guarded(path)))

    /** `adb pull <remote> <localFolder>`: files and whole folders, into the local folder. */
    fun pull(serial: DeviceSerial, remote: String, localFolder: String): AdbDeviceRequest =
        AdbDeviceRequest(serial, AdbOperation.Host(listOf("pull", RemotePath.normalize(remote), localFolder)), TRANSFER_TIMEOUT)

    /** `adb push <local> <remoteFolder>/`: a file or a folder, into the remote folder. */
    fun push(serial: DeviceSerial, local: String, remoteFolder: String): AdbDeviceRequest {
        val folder = RemotePath.normalize(remoteFolder).let { if (it == RemotePath.ROOT) it else "$it/" }
        return AdbDeviceRequest(serial, AdbOperation.Host(listOf("push", local, folder)), TRANSFER_TIMEOUT)
    }

    /** `adb push <local> <remoteFile>`: one file to an exact device path (used for staging). */
    fun pushFile(serial: DeviceSerial, local: String, remoteFile: String): AdbDeviceRequest =
        AdbDeviceRequest(serial, AdbOperation.Host(listOf("push", local, RemotePath.normalize(remoteFile))), TRANSFER_TIMEOUT)

    /**
     * `adb exec-out run-as <pkg> cat <path>`: an app-private file's bytes. The command line is one
     * pre-quoted argument: adb 35+ escapes each exec-out argument itself when given several (so
     * pre-quoted parts would arrive with literal quotes), and older adb joins them unescaped. A
     * single argument reaches the device shell as written on both.
     */
    fun readPrivate(serial: DeviceSerial, scope: FileScope.App, path: String): AdbDeviceRequest =
        AdbDeviceRequest(
            serial,
            AdbOperation.Exec(listOf("run-as ${quote(scope.packageName)} cat ${quote(RemotePath.normalize(path))}")),
            TRANSFER_TIMEOUT,
        )

    /** Copies a staged file (pushed to `/data/local/tmp`) into the app's data as the app's uid. */
    fun copyIntoApp(serial: DeviceSerial, scope: FileScope.App, staged: String, target: String): AdbDeviceRequest =
        shell(serial, scope, EDIT_TIMEOUT, literal("cp"), literal("--"), value(staged), value(guarded(target)))

    fun removeStaged(serial: DeviceSerial, staged: String): AdbDeviceRequest {
        require(staged.startsWith(STAGING_DIR)) { "not a staged path: '$staged'" }
        return shell(serial, FileScope.Device, EDIT_TIMEOUT, literal("rm"), literal("-f"), literal("--"), value(staged))
    }

    const val STAGING_DIR = "/data/local/tmp/"

    private fun guarded(path: String): String {
        val normalized = RemotePath.normalize(path)
        require(!RemotePath.isProtected(normalized)) { "refusing to change '$normalized'" }
        return normalized
    }

    private fun quote(text: String): String = "'" + text.replace("'", "'\\''") + "'"

    private fun literal(text: String) = ShellToken.Literal(text)

    private fun value(text: String) = ShellToken.Value(ShellValue.of(text))

    private fun shell(serial: DeviceSerial, scope: FileScope, timeout: Duration, vararg tokens: ShellToken): AdbDeviceRequest {
        val prefix = when (scope) {
            FileScope.Device -> emptyList()
            is FileScope.App -> listOf(literal("run-as"), value(scope.packageName))
        }
        return AdbDeviceRequest(serial, AdbOperation.Shell(AdbShellCommand(prefix + tokens.toList())), timeout)
    }
}
