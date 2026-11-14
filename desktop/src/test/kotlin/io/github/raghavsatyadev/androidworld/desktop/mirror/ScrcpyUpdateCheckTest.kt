package io.github.raghavsatyadev.androidworld.desktop.mirror

import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ScrcpyUpdateCheckTest {
    @Test
    fun `older version compares lower`() {
        ScrcpyUpdateCheck.compare("3.3.2", "5.0.1") shouldBeLessThan 0
        ScrcpyUpdateCheck.compare("5.0", "5.0.1") shouldBeLessThan 0
    }

    @Test
    fun `same or newer version is not older`() {
        ScrcpyUpdateCheck.compare("5.0.1", "5.0.1") shouldBe 0
        ScrcpyUpdateCheck.compare("5.0", "5.0.0") shouldBe 0
        ScrcpyUpdateCheck.compare("5.10", "5.9") shouldBeGreaterThan 0
    }
}
