package io.github.raghavsatyadev.androidworld.core.application.layout

import io.github.raghavsatyadev.androidworld.core.domain.foreground.ForegroundState
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ForegroundLabelTest {
    @Test
    fun `app shows package and activity`() {
        foregroundLabel(ForegroundState.App("com.example", ".Main")) shouldBe "com.example/.Main"
    }

    @Test
    fun `blank package or activity is left out`() {
        foregroundLabel(ForegroundState.App("", ".Main")) shouldBe ".Main"
        foregroundLabel(ForegroundState.App("com.example", "")) shouldBe "com.example"
        foregroundLabel(ForegroundState.App("", "")) shouldBe null
    }

    @Test
    fun `home with blank launcher package`() {
        foregroundLabel(ForegroundState.Home("")) shouldBe "Home screen"
        foregroundLabel(ForegroundState.Home("com.launcher")) shouldBe "com.launcher (home)"
    }
}
