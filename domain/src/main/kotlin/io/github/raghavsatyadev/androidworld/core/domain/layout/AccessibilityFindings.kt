package io.github.raghavsatyadev.androidworld.core.domain.layout

/** An individual accessibility issue associated with a specific [UiNode]. */
data class AccessibilityFinding(val kind: AccessibilityIssueKind, val node: UiNode, val message: String)

/** Flattens all issues from screen-reader stops and unannounced images into individual findings. */
fun AccessibilityReport.findings(): List<AccessibilityFinding> = buildList {
    for (stop in stops) {
        for (issue in stop.issues) {
            add(AccessibilityFinding(issue.kind, stop.node, issue.message))
        }
    }
    for (image in unannouncedImages) {
        add(
            AccessibilityFinding(
                AccessibilityIssueKind.ImageWithoutDescription,
                image,
                "Image has no content description (fine only if decorative)",
            ),
        )
    }
}

/** Filters findings associated with the given node [id]. */
fun List<AccessibilityFinding>.forNode(id: Int): List<AccessibilityFinding> = filter { it.node.id == id }
