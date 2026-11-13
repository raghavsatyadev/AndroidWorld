package io.github.raghavsatyadev.androidworld.core.domain.layout

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

private fun n(id: Int, vararg children: UiNode) = UiNode(
    id = id,
    bounds = PixelRect(0, 0, 100, 100),
    children = children.toList(),
)

class InspectorTreeTest {
    private val leaf3 = n(3)
    private val child1 = n(1, leaf3)
    private val child2 = n(2)
    private val root = n(0, child1, child2)

    @Test
    fun `allParents returns ids of all nodes with children`() {
        InspectorTree.allParents(root) shouldBe setOf(0, 1)
        InspectorTree.allParents(child2).shouldBeEmpty()
    }

    @Test
    fun `rows emits pre-order with depths respecting expanded set`() {
        val collapsed = InspectorTree.rows(root, emptySet())
        collapsed shouldContainExactly listOf(
            TreeRow(root, depth = 0, hasChildren = true, expanded = false),
        )

        val rootExpanded = InspectorTree.rows(root, setOf(0))
        rootExpanded shouldContainExactly listOf(
            TreeRow(root, depth = 0, hasChildren = true, expanded = true),
            TreeRow(child1, depth = 1, hasChildren = true, expanded = false),
            TreeRow(child2, depth = 1, hasChildren = false, expanded = false),
        )

        val allExpanded = InspectorTree.rows(root, setOf(0, 1))
        allExpanded shouldContainExactly listOf(
            TreeRow(root, depth = 0, hasChildren = true, expanded = true),
            TreeRow(child1, depth = 1, hasChildren = true, expanded = true),
            TreeRow(leaf3, depth = 2, hasChildren = false, expanded = false),
            TreeRow(child2, depth = 1, hasChildren = false, expanded = false),
        )
    }

    @Test
    fun `pathTo returns root to target inclusive or empty if absent`() {
        InspectorTree.pathTo(root, 3) shouldContainExactly listOf(root, child1, leaf3)
        InspectorTree.pathTo(root, 0) shouldContainExactly listOf(root)
        InspectorTree.pathTo(root, 99).shouldBeEmpty()
    }
}
