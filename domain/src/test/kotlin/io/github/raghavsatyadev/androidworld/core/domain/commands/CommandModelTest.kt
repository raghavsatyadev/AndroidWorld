package io.github.raghavsatyadev.androidworld.core.domain.commands

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldBeEmpty as shouldBeEmptyMap
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class CommandModelTest {

    private fun entry(id: String) = CommandCatalog.entries.single { it.id == id }

    @Test
    fun `tap puts X and Y in two separate quoted values`() {
        entry("tap").action.build(listOf("500", "1200")).render() shouldBe "input tap '500' '1200'"
    }

    @Test
    fun `a prefix stays inside the quotes`() {
        entry("dial").action.build(listOf("+1 555")).render() shouldBe "am start -a android.intent.action.DIAL -d 'tel:+1 555'"
        entry("proc-status").action.build(listOf("42")).render() shouldBe "cat '/proc/42/status'"
    }

    @Test
    fun `shell syntax in a value stays quoted`() {
        entry("text").action.build(listOf("x'; reboot")).render() shouldBe "input text 'x'\\''; reboot'"
    }

    @Test
    fun `a blank optional field is left out`() {
        val getprop = entry("getprop")
        getprop.action.build(listOf("")).render() shouldBe "getprop"
        getprop.problems(getprop.action, listOf("")).shouldBeEmptyMap()
        getprop.action.preview(getprop.fields, listOf("")) shouldBe "getprop"
    }

    @Test
    fun `problems name the field that is wrong`() {
        val level = entry("battery-level")
        level.problems(level.action, listOf("")) shouldBe mapOf(0 to "Required")
        level.problems(level.action, listOf("101")) shouldBe mapOf(0 to "Between 0 and 100")
        level.problems(level.action, listOf("x")) shouldBe mapOf(0 to "Enter a whole number")
        level.problems(level.action, listOf("42")).shouldBeEmptyMap()

        val rotation = entry("user-rotation")
        rotation.problems(rotation.action, listOf("7")) shouldBe mapOf(0 to "Pick one of the options")

        val size = entry("wm-size")
        size.problems(size.action, listOf("1080x2400; reboot")).keys shouldContainExactly setOf(0)
        size.problems(size.action, listOf("reset")).shouldBeEmptyMap()
    }

    @Test
    fun `the preview is the command that runs`() {
        val swipe = entry("swipe")
        val values = listOf("1", "2", "3", "4", "300")
        swipe.action.preview(swipe.fields, values) shouldBe swipe.action.build(values).render()
        swipe.action.preview(swipe.fields, listOf("1", "", "3", "4", "300")) shouldBe "input swipe '1' <from y> '3' '4' '300'"
    }

    @Test
    fun `the current value can be narrowed to one line`() {
        entry("battery-level").currentValue("Current Battery Service state:\n  AC powered: true\n  level: 87\n") shouldBe "level: 87"
        entry("auto-rotate").currentValue("1\n") shouldBe "1"
    }

    @Test
    fun `a read that uses a field checks only that field`() {
        val volume = entry("volume")
        volume.problems(volume.current!!, volume.defaults()).shouldBeEmptyMap()
        volume.problems(volume.action, volume.defaults()).keys shouldContainExactly setOf(1)
    }

    @Test
    fun `ids are unique and every slot has a field`() {
        val ids = CommandCatalog.entries.map { it.id }
        (ids.size - ids.toSet().size) shouldBe 0
        CommandCatalog.entries.flatMap { entry ->
            (entry.action.fieldsUsed + entry.current?.fieldsUsed.orEmpty()).filter { it !in entry.fields.indices }.map { entry.id }
        }.shouldBeEmpty()
    }

    @Test
    fun `search matches names and command words`() {
        entry("pidof").matches("PIDOF") shouldBe true
        entry("pidof").matches("bluetooth") shouldBe false
    }

    @Test
    fun `dumpsys services come from the listing`() {
        DumpsysServices.parse("Currently running services:\n  SurfaceFlinger\n  activity\n  accessibility\n  activity\n") shouldBe
            listOf("accessibility", "activity", "SurfaceFlinger")
        DumpsysServices.dump("battery").render() shouldBe "dumpsys 'battery'"
    }
}
