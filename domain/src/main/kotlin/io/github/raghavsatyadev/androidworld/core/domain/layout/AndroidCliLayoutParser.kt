package io.github.raghavsatyadev.androidworld.core.domain.layout

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Parses the JSON `android layout --full` writes into the same [UiNode] tree the uiautomator parser
 * builds, so `get_ui`, tap-by-ref and the accessibility audit work on either source.
 *
 * The JSON is a list of windows, each with `bounds` and `content` (nodes with `class`, `resource-id`,
 * `text`, `content-desc`, `interactions`, `state`, `bounds`, `children`). The windows go under one
 * synthetic root. Ids follow document order with the root at 0. Hidden nodes are dropped, as
 * uiautomator does. The JSON has no package name, enabled flag or NAF verdict, so those keep their
 * defaults.
 */
object AndroidCliLayoutParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val boundsPattern = Regex("""\[(-?\d+),(-?\d+)]\[(-?\d+),(-?\d+)]""")

    fun parse(text: String): UiNode? {
        val windows = runCatching { json.parseToJsonElement(text).jsonArray }.getOrNull() ?: return null
        var next = 0
        fun build(node: JsonObject): UiNode? {
            if (node.bool("hidden")) return null
            val id = next++
            val interactions = node.strings("interactions").map { it.replace('-', '_') }.toSet()
            val state = node.strings("state").toSet()
            val children = node.array("children").mapNotNull { (it as? JsonObject)?.let(::build) }
            return UiNode(
                id = id,
                className = node.string("class"),
                resourceId = node.string("resource-id"),
                text = node.string("text"),
                contentDescription = node.string("content-desc"),
                bounds = parseBounds(node.string("bounds")),
                flags = UiNodeFlags(
                    clickable = "CLICKABLE" in interactions,
                    longClickable = "LONG_CLICKABLE" in interactions,
                    focusable = "FOCUSABLE" in interactions,
                    checkable = "CHECKABLE" in interactions,
                    scrollable = "SCROLLABLE" in interactions,
                    password = "PASSWORD" in interactions,
                    checked = "CHECKED" in state,
                    selected = "SELECTED" in state,
                ),
                children = children,
            )
        }
        val rootId = next++
        val windowNodes = windows.mapNotNull { it as? JsonObject }.flatMap { window ->
            window.array("content").mapNotNull { (it as? JsonObject)?.let(::build) }
        }
        if (windowNodes.isEmpty()) return null
        val windowBounds = windows.mapNotNull { (it as? JsonObject)?.string("bounds")?.let(::parseBounds) }.filterNot { it.isEmpty }
        val rootBounds = if (windowBounds.isEmpty()) PixelRect.EMPTY else PixelRect(
            windowBounds.minOf { it.left }, windowBounds.minOf { it.top },
            windowBounds.maxOf { it.right }, windowBounds.maxOf { it.bottom },
        )
        return UiNode(id = rootId, className = "hierarchy", bounds = rootBounds, children = windowNodes)
    }

    internal fun parseBounds(text: String): PixelRect {
        val match = boundsPattern.find(text) ?: return PixelRect.EMPTY
        val (l, t, r, b) = match.destructured
        return PixelRect(l.toInt(), t.toInt(), r.toInt(), b.toInt())
    }

    private fun JsonObject.string(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun JsonObject.bool(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull == true

    private fun JsonObject.array(key: String): List<JsonElement> = (this[key] as? JsonArray).orEmpty()

    private fun JsonObject.strings(key: String): List<String> =
        array(key).mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
}
