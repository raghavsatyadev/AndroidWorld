package io.github.raghavsatyadev.androidworld.core.domain.emulator.screen

import kotlin.math.roundToInt

/** Bounding rectangle within a view where scaled content is displayed. */
data class FitRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

/** Geometry calculations for letterboxed aspect fitting and coordinate transformations. */
object ScreenGeometry {
    /**
     * Calculates the largest centered rectangle with aspect ratio [contentW]:[contentH]
     * that fits completely inside a view of size [viewW]x[viewH]. Returns [FitRect] with
     * all zeros if any dimension is non-positive.
     */
    fun fit(contentW: Float, contentH: Float, viewW: Float, viewH: Float): FitRect {
        if (contentW <= 0f || contentH <= 0f || viewW <= 0f || viewH <= 0f) {
            return FitRect(0f, 0f, 0f, 0f)
        }
        val scale = minOf(viewW / contentW, viewH / contentH)
        val width = contentW * scale
        val height = contentH * scale
        val left = (viewW - width) / 2f
        val top = (viewH - height) / 2f
        return FitRect(left, top, width, height)
    }

    /**
     * Maps view coordinates ([viewX], [viewY]) within [shown] to natural (portrait) device
     * pixel coordinates based on the current [rotation]. Returns null if the point is outside
     * [shown] or if dimensions are non-positive.
     */
    fun toDevice(
        viewX: Float,
        viewY: Float,
        shown: FitRect,
        naturalW: Int,
        naturalH: Int,
        rotation: ScreenRotation,
    ): Pair<Int, Int>? {
        if (shown.width <= 0f || shown.height <= 0f || naturalW <= 0 || naturalH <= 0) return null
        if (viewX < shown.left || viewX > shown.left + shown.width) return null
        if (viewY < shown.top || viewY > shown.top + shown.height) return null

        val u = ((viewX - shown.left) / shown.width).coerceIn(0f, 1f)
        val v = ((viewY - shown.top) / shown.height).coerceIn(0f, 1f)

        val w = naturalW.toFloat()
        val h = naturalH.toFloat()

        val (devX, devY) = when (rotation) {
            ScreenRotation.Portrait -> (u * w) to (v * h)
            ScreenRotation.Landscape -> (w * (1f - v)) to (h * u)
            ScreenRotation.ReversePortrait -> (w * (1f - u)) to (h * (1f - v))
            ScreenRotation.ReverseLandscape -> (w * v) to (h * (1f - u))
        }

        val clampedX = devX.roundToInt().coerceIn(0, naturalW - 1)
        val clampedY = devY.roundToInt().coerceIn(0, naturalH - 1)
        return clampedX to clampedY
    }
}
