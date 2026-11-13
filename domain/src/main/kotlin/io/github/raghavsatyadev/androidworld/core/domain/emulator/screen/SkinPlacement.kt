package io.github.raghavsatyadev.androidworld.core.domain.emulator.screen

/** Calculated screen and skin frame geometry for rendering within a viewport. */
data class SkinPlacement(
    val frame: FitRect?,
    val display: FitRect,
    val scale: Float,
    val canvasDegrees: Float,
)

/** Pure math helper for positioning emulator skins and content within a viewport. */
object SkinPlacer {
    fun place(
        skin: SkinLayout?,
        rotation: ScreenRotation,
        contentW: Int,
        contentH: Int,
        viewW: Float,
        viewH: Float,
    ): SkinPlacement {
        if (skin == null) {
            val display = ScreenGeometry.fit(contentW.toFloat(), contentH.toFloat(), viewW, viewH)
            val scale = if (contentW > 0) display.width / contentW.toFloat() else 0f
            return SkinPlacement(
                frame = null,
                display = display,
                scale = scale,
                canvasDegrees = 0f,
            )
        }

        val frameWidth = skin.frameWidth.toFloat()
        val frameHeight = skin.frameHeight.toFloat()

        val (rotatedOuterW, rotatedOuterH) = when (rotation) {
            ScreenRotation.Portrait, ScreenRotation.ReversePortrait -> frameWidth to frameHeight
            ScreenRotation.Landscape, ScreenRotation.ReverseLandscape -> frameHeight to frameWidth
        }

        val frame = ScreenGeometry.fit(rotatedOuterW, rotatedOuterH, viewW, viewH)
        val scale = if (rotatedOuterW > 0f) frame.width / rotatedOuterW else 0f

        val dx = skin.displayX.toFloat()
        val dy = skin.displayY.toFloat()
        val dw = skin.displayWidth.toFloat()
        val dh = skin.displayHeight.toFloat()

        val x: Float
        val y: Float
        val w: Float
        val h: Float
        when (rotation) {
            ScreenRotation.Portrait -> {
                x = dx
                y = dy
                w = dw
                h = dh
            }
            ScreenRotation.Landscape -> {
                x = dy
                y = frameWidth - dx - dw
                w = dh
                h = dw
            }
            ScreenRotation.ReversePortrait -> {
                x = frameWidth - dx - dw
                y = frameHeight - dy - dh
                w = dw
                h = dh
            }
            ScreenRotation.ReverseLandscape -> {
                x = frameHeight - dy - dh
                y = dx
                w = dh
                h = dw
            }
        }

        val display = FitRect(
            left = frame.left + x * scale,
            top = frame.top + y * scale,
            width = w * scale,
            height = h * scale,
        )

        val canvasDegrees = when (rotation) {
            ScreenRotation.Portrait -> 0f
            ScreenRotation.Landscape -> -90f
            ScreenRotation.ReversePortrait -> 180f
            ScreenRotation.ReverseLandscape -> 90f
        }

        return SkinPlacement(
            frame = frame,
            display = display,
            scale = scale,
            canvasDegrees = canvasDegrees,
        )
    }
}
