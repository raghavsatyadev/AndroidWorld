package io.github.raghavsatyadev.androidworld.core.domain.layout

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NodePropertiesTest {

    @Test
    fun `formats node properties in exact order with em-dash for empty strings and computed sizes`() {
        val node = UiNode(
            id = 1,
            className = "android.widget.Button",
            resourceId = "com.example.app:id/submit",
            text = "",
            contentDescription = "Submit button",
            packageName = "com.example.app",
            bounds = PixelRect(0, 0, 1080, 2400),
            flags = UiNodeFlags(
                clickable = true,
                longClickable = false,
                focusable = true,
                enabled = true,
                checkable = false,
                checked = false,
                selected = false,
                scrollable = false,
                password = false,
                naf = false,
            ),
        )
        val hierarchy = UiHierarchy(node, densityDpi = 420)

        val props = NodeProperties.of(node, hierarchy)

        props shouldBe listOf(
            "Class" to "android.widget.Button",
            "Role" to "Button",
            "Resource id" to "com.example.app:id/submit",
            "Text" to "—",
            "Content description" to "Submit button",
            "Package" to "com.example.app",
            "Bounds" to "[0,0][1080,2400]",
            "Size" to "1080 × 2400 px (411 × 914 dp)",
            "Clickable" to "true",
            "Long-clickable" to "false",
            "Focusable" to "true",
            "Enabled" to "true",
            "Checkable" to "false",
            "Checked" to "false",
            "Selected" to "false",
            "Scrollable" to "false",
            "Password" to "false",
            "NAF" to "false",
        )
    }

    @Test
    fun `renders populated text and all empty fields as em-dash`() {
        val node = UiNode(
            id = 2,
            text = "Hello World",
            bounds = PixelRect(10, 20, 110, 220),
        )
        val hierarchy = UiHierarchy(node, densityDpi = 160)

        val props = NodeProperties.of(node, hierarchy).toMap()

        props["Text"] shouldBe "Hello World"
        props["Class"] shouldBe "—"
        props["Role"] shouldBe "Text"
        props["Resource id"] shouldBe "—"
        props["Content description"] shouldBe "—"
        props["Package"] shouldBe "—"
        props["Bounds"] shouldBe "[10,20][110,220]"
        props["Size"] shouldBe "100 × 200 px (100 × 200 dp)"
    }
}
