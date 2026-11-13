package io.github.raghavsatyadev.androidworld.core.domain.files

import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbOperation
import io.github.raghavsatyadev.androidworld.core.domain.adb.DeviceSerial
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class RemotePathAndCommandsTest {
    private val serial = DeviceSerial.of("emulator-5554")

    @Test
    fun `paths normalise`() {
        RemotePath.normalize("/sdcard//Download/./x/../") shouldBe "/sdcard/Download"
        RemotePath.normalize("/..") shouldBe "/"
        RemotePath.parent("/sdcard") shouldBe "/"
        RemotePath.parent("/") shouldBe "/"
        RemotePath.child("/", "data") shouldBe "/data"
        RemotePath.child("/sdcard", "my dir") shouldBe "/sdcard/my dir"
    }

    @Test
    fun `crumbs lead from the root`() {
        RemotePath.crumbs("/sdcard/Download").map { it.path } shouldContainExactly listOf("/", "/sdcard", "/sdcard/Download")
        RemotePath.crumbs("/").map { it.label } shouldContainExactly listOf("/")
    }

    @Test
    fun `names with slashes or dots are rejected`() {
        RemotePath.isValidName("a b") shouldBe true
        RemotePath.isValidName("a/b") shouldBe false
        RemotePath.isValidName("..") shouldBe false
        RemotePath.isValidName(" ") shouldBe false
    }

    @Test
    fun `listing adds a trailing slash and quotes the path`() {
        val request = FileCommands.list(serial, FileScope.Device, "/sdcard/my dir")
        (request.operation as AdbOperation.Shell).command.render() shouldBe "ls -la -- '/sdcard/my dir/'"
        (FileCommands.list(serial, FileScope.Device, "/").operation as AdbOperation.Shell).command.render() shouldBe "ls -la -- '/'"
    }

    @Test
    fun `app scope runs through run-as`() {
        val request = FileCommands.delete(serial, FileScope.App("com.acme.shop"), "/data/data/com.acme.shop/files/it's")
        (request.operation as AdbOperation.Shell).command.render() shouldBe
            "run-as 'com.acme.shop' rm -rf -- '/data/data/com.acme.shop/files/it'\\''s'"
    }

    @Test
    fun `protected folders cannot be deleted or renamed`() {
        shouldThrow<IllegalArgumentException> { FileCommands.delete(serial, FileScope.Device, "/sdcard/") }
        shouldThrow<IllegalArgumentException> { FileCommands.delete(serial, FileScope.Device, "/") }
        shouldThrow<IllegalArgumentException> { FileCommands.rename(serial, FileScope.Device, "/system", "/x") }
    }

    @Test
    fun `push and pull use adb sync argv`() {
        (FileCommands.pull(serial, "/sdcard/a b.txt", "C:\\out dir").operation as AdbOperation.Host).arguments shouldContainExactly
            listOf("pull", "/sdcard/a b.txt", "C:\\out dir")
        (FileCommands.push(serial, "C:\\in\\x.txt", "/sdcard/Download").operation as AdbOperation.Host).arguments shouldContainExactly
            listOf("push", "C:\\in\\x.txt", "/sdcard/Download/")
    }

    @Test
    fun `reading a private file quotes for exec-out`() {
        val request = FileCommands.readPrivate(serial, FileScope.App("com.acme.shop"), "/data/data/com.acme.shop/files/a b")
        (request.operation as AdbOperation.Exec).arguments shouldContainExactly
            listOf("run-as 'com.acme.shop' cat '/data/data/com.acme.shop/files/a b'")
    }
}
