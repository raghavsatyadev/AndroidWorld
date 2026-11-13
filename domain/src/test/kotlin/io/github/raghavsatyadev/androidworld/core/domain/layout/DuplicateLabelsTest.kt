package io.github.raghavsatyadev.androidworld.core.domain.layout

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
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

class DuplicateLabelsTest {

    @Test
    fun `two clickable buttons with case and whitespace differences are both flagged`() {
        val b1 = n(1, big(0), "android.widget.Button", text = "Delete", flags = CLICKABLE)
        val b2 = n(2, big(dp(60)), "android.widget.Button", text = "delete ", flags = CLICKABLE)

        val report = AccessibilityAudit.audit(UiHierarchy(n(0, PixelRect(0, 0, 1080, 2340), children = arrayOf(b1, b2)), DPI))

        val duplicateIssues = report.stops.flatMap { it.issues.filter { issue -> issue.kind == AccessibilityIssueKind.DuplicateLabel } }
        duplicateIssues.map { it.message } shouldContainExactly listOf(
            "Same label as 1 other control: \"Delete\"",
            "Same label as 1 other control: \"delete\"",
        )
    }

    @Test
    fun `three equal buttons report 2 other controls`() {
        val b1 = n(1, big(0), "android.widget.Button", text = "Save", flags = CLICKABLE)
        val b2 = n(2, big(dp(60)), "android.widget.Button", text = "Save", flags = CLICKABLE)
        val b3 = n(3, big(dp(120)), "android.widget.Button", text = "Save", flags = CLICKABLE)

        val report = AccessibilityAudit.audit(UiHierarchy(n(0, PixelRect(0, 0, 1080, 2340), children = arrayOf(b1, b2, b3)), DPI))

        val duplicateIssues = report.stops.flatMap { it.issues.filter { issue -> issue.kind == AccessibilityIssueKind.DuplicateLabel } }
        duplicateIssues.map { it.message } shouldContainExactly listOf(
            "Same label as 2 other controls: \"Save\"",
            "Same label as 2 other controls: \"Save\"",
            "Same label as 2 other controls: \"Save\"",
        )
    }

    @Test
    fun `a clickable and a non-clickable text with the same text are not flagged`() {
        val b1 = n(1, big(0), "android.widget.Button", text = "Info", flags = CLICKABLE)
        val t2 = n(2, big(dp(60)), "android.widget.TextView", text = "Info")

        val report = AccessibilityAudit.audit(UiHierarchy(n(0, PixelRect(0, 0, 1080, 2340), children = arrayOf(b1, t2)), DPI))

        val duplicateIssues = report.stops.flatMap { it.issues.filter { issue -> issue.kind == AccessibilityIssueKind.DuplicateLabel } }
        duplicateIssues.shouldBeEmpty()
    }

    @Test
    fun `controls with empty labels are not flagged as duplicates`() {
        val b1 = n(1, big(0), "android.widget.ImageButton", flags = CLICKABLE)
        val b2 = n(2, big(dp(60)), "android.widget.ImageButton", flags = CLICKABLE)

        val report = AccessibilityAudit.audit(UiHierarchy(n(0, PixelRect(0, 0, 1080, 2340), children = arrayOf(b1, b2)), DPI))

        val duplicateIssues = report.stops.flatMap { it.issues.filter { issue -> issue.kind == AccessibilityIssueKind.DuplicateLabel } }
        duplicateIssues.shouldBeEmpty()
    }
}
