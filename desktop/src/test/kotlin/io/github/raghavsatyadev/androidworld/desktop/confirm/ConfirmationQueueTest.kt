package io.github.raghavsatyadev.androidworld.desktop.confirm

import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ConfirmationQueueTest {
    private fun req(title: String) = ConfirmRequest(title, "m", "OK")

    @Test
    fun `requests are shown one at a time in order`() = runTest {
        val queue = ConfirmationQueue()
        val first = req("a")
        val second = req("b")
        val a = async { queue.ask(first) }
        runCurrent()
        val b = async { queue.ask(second) }
        runCurrent()
        queue.current.value shouldBe first
        queue.answer(first, true)
        runCurrent()
        queue.current.value shouldBe second
        queue.answer(second, false)
        a.await() shouldBe true
        b.await() shouldBe false
        queue.current.value shouldBe null
    }

    @Test
    fun `a cancelled caller closes its dialog`() = runTest {
        val queue = ConfirmationQueue()
        val job = async { queue.ask(req("a")) }
        runCurrent()
        job.cancel()
        runCurrent()
        queue.current.value shouldBe null
    }

    @Test
    fun `a timed out request counts as no`() = runTest {
        val queue = ConfirmationQueue()
        val answer = async { queue.ask(req("a"), timeout = 5.seconds) }
        advanceTimeBy(6_000)
        answer.await() shouldBe false
        queue.current.value shouldBe null
    }
}
