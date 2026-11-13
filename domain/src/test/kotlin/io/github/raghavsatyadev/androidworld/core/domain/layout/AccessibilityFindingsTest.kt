package io.github.raghavsatyadev.androidworld.core.domain.layout

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

private const val DPI = 440
private fun dp(value: Int) = (value * DPI) / 160

private fun n(
    id: Int,
    rect: PixelRect,
    className: String = "android.view.View",
    text: String = "",
    desc: String = "",
    flags: UiNodeFlags = UiNodeFlags(),
    vararg children: UiNode,
) = UiNode(id = id, className = className, text = text, contentDescription = desc, bounds = rect, flags = flags, children = children.toList())

private fun big(top: Int) = PixelRect(0, top, dp(200), top + dp(56))
private val CLICKABLE = UiNodeFlags(clickable = true)

class AccessibilityFindingsTest {

    @Test
    fun `findings reports stops in order then unannounced images with correct kinds and node ids`() {
        val noLabelButton = n(1, big(0), "android.widget.ImageButton", flags = CLICKABLE)
        val smallTargetButton = n(2, PixelRect(0, dp(100), dp(40), dp(130)), "android.widget.Button", text = "Ok", flags = CLICKABLE)
        val unannouncedImage = n(3, big(dp(200)), "android.widget.ImageView")

        val report = AccessibilityAudit.audit(
            UiHierarchy(
                n(0, PixelRect(0, 0, 1080, 2340), children = arrayOf(noLabelButton, smallTargetButton, unannouncedImage)),
                DPI,
            ),
        )

        val findings = report.findings()

        findings.map { it.node.id } shouldContainExactly listOf(1, 2, 3)
        findings.map { it.kind } shouldContainExactly listOf(
            AccessibilityIssueKind.NoLabel,
            AccessibilityIssueKind.SmallTouchTarget,
            AccessibilityIssueKind.ImageWithoutDescription,
        )
        findings.map { it.message } shouldContainExactly listOf(
            "No label: TalkBack says only \"Button\"",
            "Touch target 40 × 30 dp, minimum 48 × 48",
            "Image has no content description (fine only if decorative)",
        )

        findings.forNode(1).map { it.kind } shouldContainExactly listOf(AccessibilityIssueKind.NoLabel)
        findings.forNode(2).map { it.kind } shouldContainExactly listOf(AccessibilityIssueKind.SmallTouchTarget)
        findings.forNode(3).map { it.kind } shouldContainExactly listOf(AccessibilityIssueKind.ImageWithoutDescription)
        findings.forNode(99).shouldBeEmpty()
    }
}
