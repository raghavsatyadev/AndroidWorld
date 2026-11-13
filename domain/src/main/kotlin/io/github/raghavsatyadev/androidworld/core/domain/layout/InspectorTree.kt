package io.github.raghavsatyadev.androidworld.core.domain.layout

/** A row in the inspector tree view representing a [node] at a given [depth] in the hierarchy. */
data class TreeRow(
    val node: UiNode,
    val depth: Int,
    val hasChildren: Boolean,
    val expanded: Boolean,
)

/** Utilities for flattening, navigating, and inspecting a [UiNode] hierarchy. */
object InspectorTree {
    /** Emits visible tree rows in pre-order traversal; children are only visited if their parent is in [expanded]. */
    fun rows(root: UiNode, expanded: Set<Int>): List<TreeRow> = buildList {
        fun visit(node: UiNode, depth: Int) {
            val hasChildren = node.children.isNotEmpty()
            val isExpanded = node.id in expanded
            add(TreeRow(node, depth, hasChildren, isExpanded))
            if (hasChildren && isExpanded) {
                for (child in node.children) {
                    visit(child, depth + 1)
                }
            }
        }
        visit(root, 0)
    }

    /** Returns the path of nodes from [root] to the node with [id] inclusive, or an empty list if not found. */
    fun pathTo(root: UiNode, id: Int): List<UiNode> {
        if (root.id == id) return listOf(root)
        for (child in root.children) {
            val childPath = pathTo(child, id)
            if (childPath.isNotEmpty()) {
                return listOf(root) + childPath
            }
        }
        return emptyList()
    }

    /** Returns the set of node ids for all nodes that have at least one child. */
    fun allParents(root: UiNode): Set<Int> =
        root.descendantsAndSelf()
            .filter { it.children.isNotEmpty() }
            .map { it.id }
            .toSet()
}
