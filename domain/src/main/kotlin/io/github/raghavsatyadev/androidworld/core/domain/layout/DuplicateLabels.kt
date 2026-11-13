package io.github.raghavsatyadev.androidworld.core.domain.layout

/** Identifies actionable screen-reader stops sharing identical speakable text (ATF DuplicateSpeakableTextCheck). */
internal object DuplicateLabels {
    fun mark(stops: List<AccessibilityStop>): List<AccessibilityStop> {
        val groups = stops
            .filter { stop ->
                val f = stop.node.flags
                (f.clickable || f.longClickable || f.checkable) && stop.label.trim().isNotEmpty()
            }
            .groupBy { it.label.trim().lowercase() }

        return stops.map { stop ->
            val f = stop.node.flags
            val isActionable = f.clickable || f.longClickable || f.checkable
            val trimmed = stop.label.trim()
            val normalized = trimmed.lowercase()

            if (!isActionable || normalized.isEmpty()) {
                stop
            } else {
                val group = groups[normalized]
                if (group != null && group.size >= 2) {
                    val k = group.size - 1
                    val noun = if (k == 1) "control" else "controls"
                    val issue = AccessibilityIssue(
                        AccessibilityIssueKind.DuplicateLabel,
                        "Same label as $k other $noun: \"$trimmed\"",
                    )
                    stop.copy(issues = stop.issues + issue)
                } else {
                    stop
                }
            }
        }
    }
}
