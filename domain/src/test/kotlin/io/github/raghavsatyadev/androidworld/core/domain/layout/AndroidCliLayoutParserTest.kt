package io.github.raghavsatyadev.androidworld.core.domain.layout

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AndroidCliLayoutParserTest {
    private val sample = """
        [{"window-title":"","type":"APPLICATION","bounds":"[0,0][1080,2400]","content":[
          {"class":"android.widget.FrameLayout","bounds":"[0,0][1080,2400]","children":[
            {"class":"android.widget.Button","resource-id":"com.example:id/ok","text":"OK",
             "interactions":["CLICKABLE","LONG-CLICKABLE","FOCUSABLE"],"state":["SELECTED"],"bounds":"[10,20][110,70]"},
            {"class":"android.widget.TextView","text":"gone","hidden":true,"bounds":"[0,0][1,1]"},
            {"class":"android.widget.Switch","content-desc":"Wi-Fi","interactions":["CHECKABLE"],"state":["CHECKED"],"bounds":"[0,100][200,160]"}
          ]}
        ]}]
    """.trimIndent()

    @Test
    fun `builds the tree with document-order ids under a synthetic root`() {
        val root = AndroidCliLayoutParser.parse(sample).shouldNotBeNull()
        root.id shouldBe 0
        root.bounds shouldBe PixelRect(0, 0, 1080, 2400)
        root.descendantsAndSelf().map { it.id }.toList() shouldContainExactly listOf(0, 1, 2, 3)
        val button = root.children.single().children.first()
        button.resourceId shouldBe "com.example:id/ok"
        button.text shouldBe "OK"
        button.bounds shouldBe PixelRect(10, 20, 110, 70)
        button.flags.clickable shouldBe true
        button.flags.longClickable shouldBe true
        button.flags.focusable shouldBe true
        button.flags.selected shouldBe true
    }

    @Test
    fun `drops hidden nodes and maps checkable state`() {
        val root = AndroidCliLayoutParser.parse(sample).shouldNotBeNull()
        val frame = root.children.single()
        frame.children.map { it.shortClassName } shouldContainExactly listOf("Button", "Switch")
        val switch = frame.children[1]
        switch.flags.checkable shouldBe true
        switch.flags.checked shouldBe true
        switch.contentDescription shouldBe "Wi-Fi"
    }

    @Test
    fun `parses a real capture from a Samsung lock screen`() {
        val text = javaClass.getResource("/layout/android-cli-samsung-lockscreen.json")!!.readText()
        val root = AndroidCliLayoutParser.parse(text).shouldNotBeNull()
        root.bounds shouldBe PixelRect(0, 0, 1440, 3120)
        root.descendantsAndSelf().any { it.text == "21°" } shouldBe true
    }

    @Test
    fun `returns null for text that is not a window list`() {
        AndroidCliLayoutParser.parse("Installing layout instrumentation server...").shouldBeNull()
        AndroidCliLayoutParser.parse("[]").shouldBeNull()
    }
}
