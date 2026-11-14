package io.github.raghavsatyadev.androidworld.core.domain.emulator.screen

import kotlin.math.abs

/** Parsed Android SDK emulator skin layout description. */
data class SkinLayout(
    val frameWidth: Int,
    val frameHeight: Int,
    val displayX: Int,
    val displayY: Int,
    val displayWidth: Int,
    val displayHeight: Int,
    val cornerRadius: Int,
    val background: String?,
    val mask: String?,
)

/** Parses Android SDK skin `layout` files into [SkinLayout]. */
object SkinLayoutParser {
    private class Node(
        val values: MutableMap<String, String> = mutableMapOf(),
        val children: MutableMap<String, Node> = mutableMapOf(),
    )

    private val TOKEN_REGEX = Regex("""\{|\}|[^\s{}]+""")

    fun parse(text: String): SkinLayout? {
        val root = Node()
        val stack = ArrayDeque<Node>()
        stack.addLast(root)

        var pendingKey: String? = null

        TOKEN_REGEX.findAll(text).forEach { match ->
            when (val token = match.value) {
                "{" -> {
                    val key = pendingKey ?: "anonymous_${stack.size}"
                    val child = stack.last().children.getOrPut(key) { Node() }
                    stack.addLast(child)
                    pendingKey = null
                }
                "}" -> {
                    pendingKey = null
                    if (stack.size > 1) {
                        stack.removeLast()
                    }
                }
                else -> {
                    val key = pendingKey
                    if (key == null) {
                        pendingKey = token
                    } else {
                        val current = stack.last()
                        if (key !in current.values) {
                            current.values[key] = token
                        }
                        pendingKey = null
                    }
                }
            }
        }

        val parts = root.children["parts"] ?: return null
        val device = parts.children["device"] ?: return null
        val display = device.children["display"] ?: return null

        val displayWidth = display.values["width"]?.toIntOrNull() ?: return null
        val displayHeight = display.values["height"]?.toIntOrNull() ?: return null
        val displayX = display.values["x"]?.toIntOrNull() ?: 0
        val displayY = display.values["y"]?.toIntOrNull() ?: 0
        val cornerRadius = display.values["corner_radius"]?.toIntOrNull() ?: 0

        val portraitPart = parts.children["portrait"]
        val background = portraitPart?.children?.get("background")?.values?.get("image")
        val mask = portraitPart?.children?.get("foreground")?.values?.get("mask")

        val layouts = root.children["layouts"]
        // Most skins name their layout "portrait"; some (e.g. unfolded foldables) only have "landscape".
        val portraitLayout = layouts?.children?.get("portrait")
            ?: layouts?.children?.values?.firstOrNull { layout ->
                layout.children.values.any { it.values["name"] == "device" }
            }

        val frameWidth: Int
        val frameHeight: Int
        val finalDisplayX: Int
        val finalDisplayY: Int

        if (portraitLayout != null) {
            frameWidth = portraitLayout.values["width"]?.toIntOrNull() ?: displayWidth
            frameHeight = portraitLayout.values["height"]?.toIntOrNull() ?: displayHeight
            val devicePart = portraitLayout.children.values.firstOrNull { it.values["name"] == "device" }
            val devX = devicePart?.values?.get("x")?.toIntOrNull() ?: 0
            val devY = devicePart?.values?.get("y")?.toIntOrNull() ?: 0
            finalDisplayX = devX + displayX
            finalDisplayY = devY + displayY
        } else {
            frameWidth = displayWidth
            frameHeight = displayHeight
            finalDisplayX = displayX
            finalDisplayY = displayY
        }

        return SkinLayout(
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            displayX = finalDisplayX,
            displayY = finalDisplayY,
            displayWidth = displayWidth,
            displayHeight = displayHeight,
            cornerRadius = cornerRadius,
            background = background,
            mask = mask,
        )
    }
}

/** Selects the best matching skin layout for a given [EmulatorHardware]. */
object SkinMatcher {
    /**
     * Returns the key of the skin in [available] to use for [hardware], or null if none fits.
     *
     * 1. An exact `hardware.skinName` key wins. If only variant keys (`name/sub`) exist for that
     *    name, one variant is chosen for the frame.
     * 2. Otherwise the first layout whose display size matches the hardware (either orientation)
     *    is used. If it is a variant key, its siblings are re-picked for the frame.
     *
     * [frameWidth] and [frameHeight] are the current frame size; 0 means unknown.
     */
    fun pick(
        available: Map<String, SkinLayout>,
        hardware: EmulatorHardware,
        frameWidth: Int = 0,
        frameHeight: Int = 0,
    ): String? {
        val base = hardware.skinName
        if (base != null) {
            if (base in available) return base
            val variants = available.keys.filter { it.startsWith("$base/") }
            if (variants.isNotEmpty()) return pickVariant(variants, available, frameWidth, frameHeight)
        }
        val hwW = hardware.width
        val hwH = hardware.height
        if (hwW <= 0 || hwH <= 0) return null

        val matches = available.filter { (_, layout) ->
            (layout.displayWidth == hwW && layout.displayHeight == hwH) ||
                (layout.displayWidth == hwH && layout.displayHeight == hwW)
        }.keys

        val picked = matches.minWithOrNull(
            compareBy<String> { name -> if (name.startsWith("pixel", ignoreCase = true)) 0 else 1 }
                .thenBy { it.length }
                .thenBy { it }
        ) ?: return null

        if (!picked.contains('/') || frameWidth <= 0 || frameHeight <= 0) return picked
        val prefix = picked.substringBefore('/') + "/"
        val siblings = available.keys.filter { it.startsWith(prefix) }
        return pickVariant(siblings, available, frameWidth, frameHeight)
    }

    /**
     * Chooses among variant [candidates] by display aspect ratio closest to the frame's
     * (orientation-independent). Without frame dimensions, prefers `/default`, else the first by name.
     */
    private fun pickVariant(
        candidates: List<String>,
        available: Map<String, SkinLayout>,
        frameWidth: Int,
        frameHeight: Int,
    ): String? {
        if (candidates.isEmpty()) return null
        if (frameWidth > 0 && frameHeight > 0) {
            val frameAspect = aspect(frameWidth, frameHeight)
            return candidates.minWithOrNull(
                compareBy<String> { name ->
                    val layout = available.getValue(name)
                    abs(aspect(layout.displayWidth, layout.displayHeight) - frameAspect)
                }.thenBy { it }
            )
        }
        return candidates.sorted().firstOrNull { it.endsWith("/default") } ?: candidates.minOrNull()
    }

    /** Orientation-independent aspect ratio, max / min. */
    private fun aspect(w: Int, h: Int): Float = maxOf(w, h).toFloat() / minOf(w, h).coerceAtLeast(1)
}
