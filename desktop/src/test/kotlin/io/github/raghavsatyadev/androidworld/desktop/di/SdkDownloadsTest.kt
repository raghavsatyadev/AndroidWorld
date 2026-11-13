package io.github.raghavsatyadev.androidworld.desktop.di

import io.kotest.matchers.shouldBe
import java.io.File
import java.nio.file.Path
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SdkDownloadsTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `sweep removes files and folders added after snapshot but keeps earlier entries`() {
        val downloads = SdkDownloads { root.toString() }
        val archDir = root.resolve(".sdk/arch").toFile().apply { mkdirs() }
        val oldFile = File(archDir, "old").apply { writeText("old") }

        val before = downloads.snapshot()
        before shouldBe setOf("arch/old")

        val newFile = File(archDir, "new.part").apply { writeText("part") }
        val unzipsNewDir = root.resolve(".sdk/unzips/new").toFile().apply { mkdirs() }
        val unzippedFile = File(unzipsNewDir, "extracted").apply { writeText("data") }

        downloads.sweep(before)

        oldFile.exists() shouldBe true
        newFile.exists() shouldBe false
        unzipsNewDir.exists() shouldBe false
        unzippedFile.exists() shouldBe false
    }

    @Test
    fun `files created directly in root are not deleted`() {
        val downloads = SdkDownloads { root.toString() }
        val before = downloads.snapshot()
        val keepFile = root.resolve("keep.tmp").toFile().apply { writeText("keep") }

        downloads.sweep(before)

        keepFile.exists() shouldBe true
    }

    @Test
    fun `snapshot with null sdk root returns empty set and sweep does nothing`() {
        val downloads = SdkDownloads { null }
        downloads.snapshot() shouldBe emptySet()
        downloads.sweep(emptySet())
    }
}
