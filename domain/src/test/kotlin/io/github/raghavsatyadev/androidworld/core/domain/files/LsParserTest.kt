package io.github.raghavsatyadev.androidworld.core.domain.files

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

/** Fixtures are real `ls -la` lines captured from emulator-5554 (toybox 0.8.14) unless noted. */
class LsParserTest {
    private val folder = """
        total 36
        drwxrwxrwx 3 shell shell 4096 2026-11-13 11:19 .
        drwxrwx--x 4 shell shell 4096 2026-11-13 11:19 ..
        -rw-rw-rw- 1 shell shell    0 2026-11-13 11:19 a  b.txt
        lrwxrwxrwx 1 shell shell   16 2026-11-13 11:19 dlink -> /sdcard/Download
        drwxrwxrwx 2 shell shell 4096 2026-11-13 11:19 my dir
        -rw-rw-rw- 1 shell shell    0 2026-11-13 11:19 x -> y
    """.trimIndent()

    @Test
    fun `total and dot entries are skipped and openable entries come first`() {
        val listing = LsParser.parse(folder)
        listing.entries.map { it.name } shouldContainExactly listOf("dlink", "my dir", "a  b.txt", "x -> y")
        listing.errors.shouldBeEmpty()
    }

    @Test
    fun `runs of spaces in a name survive`() {
        LsParser.parse(folder).entries.first { it.type == RemoteEntryType.File }.name shouldBe "a  b.txt"
    }

    @Test
    fun `a symlink splits into name and target`() {
        val link = LsParser.parse(folder).entries.first { it.isLink }
        link.name shouldBe "dlink"
        link.linkTarget shouldBe "/sdcard/Download"
        link.canOpen shouldBe true
    }

    @Test
    fun `an arrow in a regular file name is part of the name`() {
        val file = LsParser.parse(folder).entries.last()
        file.name shouldBe "x -> y"
        file.linkTarget shouldBe null
    }

    @Test
    fun `size, owner, group, mode and date are read`() {
        val entry = LsParser.parseLine("drwxr-xr-x 108 root   root       2180 2026-11-11 18:34 apex")!!
        entry shouldBe RemoteEntry("apex", RemoteEntryType.Directory, "drwxr-xr-x", "root", "root", 2180, "2026-11-11 18:34")
    }

    @Test
    fun `a device node has no size`() {
        val entry = LsParser.parseLine("crw-rw-rw-  1 root        root         10, 260 2026-11-11 18:34 ashmem")!!
        entry.type shouldBe RemoteEntryType.CharDevice
        entry.size shouldBe null
        entry.name shouldBe "ashmem"
    }

    @Test
    fun `sdcard listings without dot entries parse`() {
        val listing = LsParser.parse(
            """
            total 128
            drwxrws--- 2 u0_a209  media_rw 4096 2026-11-11 18:36 .trash-storage
            drwxrws--x 5 media_rw media_rw 4096 2026-11-11 18:36 Android
            """.trimIndent(),
        )
        listing.entries.map { it.name } shouldContainExactly listOf(".trash-storage", "Android")
        listing.entries[0].owner shouldBe "u0_a209"
    }

    @Test
    fun `permission denied with no entries is reported`() {
        val listing = LsParser.parse("ls: /data: Permission denied\n")
        listing.entries.shouldBeEmpty()
        listing.permissionDenied shouldBe true
    }

    @Test
    fun `not a directory is reported`() {
        LsParser.parse("ls: /system/bin/sh/: Not a directory").notADirectory shouldBe true
    }

    @Test
    fun `an unreadable entry beside readable ones is kept as an error only`() {
        val listing = LsParser.parse(
            "ls: /proc/1/cwd: Permission denied\r\ndr-xr-xr-x 9 root root 0 2026-11-13 11:00 1\r\n",
        )
        listing.entries.map { it.name } shouldContainExactly listOf("1")
        listing.errors shouldContainExactly listOf("ls: /proc/1/cwd: Permission denied")
        listing.permissionDenied shouldBe false
    }

    @Test
    fun `old toolbox lines without link count or folder size parse`() {
        // Format of the pre-Marshmallow toolbox ls (from ddmlib's FileListingService fixtures).
        val dir = LsParser.parseLine("drwxr-xr-x root     root              2015-06-01 10:00 system")!!
        dir.size shouldBe null
        dir.name shouldBe "system"
        LsParser.parseLine("-rw-r--r-- root     root         1234 2015-06-01 10:00 build.prop")!!.size shouldBe 1234
    }

    @Test
    fun `seconds, a trailing ACL mark and month-name dates parse`() {
        LsParser.parseLine("-rw-r--r--+ 1 root root 5 2026-11-13 11:19:02.123456789 +0000 f")!!.name shouldBe "f"
        LsParser.parseLine("-rw-r--r-- 1 root root 5 Nov 13 11:19 g h")!!.name shouldBe "g h"
        LsParser.parseLine("-rw-r--r-- 1 root root 5 Jan  2  2024 old")!!.modified shouldBe "Jan  2  2024"
    }

    @Test
    fun `entries SELinux hides from stat keep their type and name`() {
        // Real lines from `ls -la /` and `/system/bin` on an API 37 emulator.
        val listing = LsParser.parse(
            """
            d?????????   ? ?      ?             ?                ? data_mirror
            l?????????   ? ?      ?             ?                ? init -> ?
            -?????????  ? ?    ?          ?                ? apexd
            """.trimIndent(),
        )
        listing.errors shouldBe emptyList()
        listing.entries.map { it.name } shouldBe listOf("data_mirror", "init", "apexd")
        listing.entries.first().type shouldBe RemoteEntryType.Directory
        listing.entries[1].linkTarget shouldBe null
        listing.entries.last().size shouldBe null
    }

    @Test
    fun `junk lines are not entries`() {
        LsParser.parseLine("total 12") shouldBe null
        LsParser.parseLine("hello world") shouldBe null
        LsParser.parseLine("drwxr-xr-x 2 root root 4096 2026-11-13 11:19 ") shouldBe null
        LsParser.parseLine("-rw-r--r-- 1 10086 10086 5 2026-11-13 11:19 numeric") shouldNotBe null
    }
}
