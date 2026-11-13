package io.github.raghavsatyadev.androidworld.core.domain.layout

import kotlin.math.roundToInt

/** Formats a [UiNode]'s attributes into structured key-value pairs for inspector display. */
object NodeProperties {
    fun of(node: UiNode, hierarchy: UiHierarchy): List<Pair<String, String>> {
        val b = node.bounds
        val wDp = hierarchy.dp(b.width).roundToInt()
        val hDp = hierarchy.dp(b.height).roundToInt()
        val f = node.flags

        return listOf(
            "Class" to node.className.dashIfEmpty(),
            "Role" to AccessibilityAudit.role(node).dashIfEmpty(),
            "Resource id" to node.resourceId.dashIfEmpty(),
            "Text" to node.text.dashIfEmpty(),
            "Content description" to node.contentDescription.dashIfEmpty(),
            "Package" to node.packageName.dashIfEmpty(),
            "Bounds" to "[${b.left},${b.top}][${b.right},${b.bottom}]",
            "Size" to "${b.width} × ${b.height} px ($wDp × $hDp dp)",
            "Clickable" to f.clickable.toString(),
            "Long-clickable" to f.longClickable.toString(),
            "Focusable" to f.focusable.toString(),
            "Enabled" to f.enabled.toString(),
            "Checkable" to f.checkable.toString(),
            "Checked" to f.checked.toString(),
            "Selected" to f.selected.toString(),
            "Scrollable" to f.scrollable.toString(),
            "Password" to f.password.toString(),
            "NAF" to f.naf.toString(),
        )
    }

    private fun String.dashIfEmpty(): String = ifEmpty { "—" }
}
