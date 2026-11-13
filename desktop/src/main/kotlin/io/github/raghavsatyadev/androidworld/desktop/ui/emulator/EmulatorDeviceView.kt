package io.github.raghavsatyadev.androidworld.desktop.ui.emulator

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.core.application.emulator.screen.EmulatorScreenIntent
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.EmulatorFrame
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.EmulatorKeys
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.FitRect
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.ScreenRotation
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.SkinLayout
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.SkinPlacer
import kotlin.math.roundToInt

/**
 * Draws the emulator display and skin canvas, and routes pointer and keyboard interactions.
 */
@Composable
fun EmulatorDeviceView(
    frame: EmulatorFrame?,
    rotation: ScreenRotation,
    skin: SkinLayout?,
    background: ImageBitmap?,
    onPointer: (x: Float, y: Float, shown: FitRect, pressed: Boolean) -> Unit,
    onKey: (EmulatorScreenIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (frame == null) return

    val bitmap = remember(frame) { frame.toImageBitmap() }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val viewW = viewSize.width.toFloat()
    val viewH = viewSize.height.toFloat()

    val placement = SkinPlacer.place(skin, rotation, frame.width, frame.height, viewW, viewH)
    val focusRequester = remember { FocusRequester() }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { viewSize = it }
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Backspace -> {
                        onKey(EmulatorScreenIntent.Key(EmulatorKeys.BACKSPACE))
                        true
                    }
                    Key.Enter, Key.NumPadEnter -> {
                        onKey(EmulatorScreenIntent.Key(EmulatorKeys.ENTER))
                        true
                    }
                    Key.Tab -> {
                        onKey(EmulatorScreenIntent.Key(EmulatorKeys.TAB))
                        true
                    }
                    Key.Escape -> {
                        onKey(EmulatorScreenIntent.Key(EmulatorKeys.ESCAPE))
                        true
                    }
                    Key.DirectionLeft -> {
                        onKey(EmulatorScreenIntent.Key(EmulatorKeys.ARROW_LEFT))
                        true
                    }
                    Key.DirectionRight -> {
                        onKey(EmulatorScreenIntent.Key(EmulatorKeys.ARROW_RIGHT))
                        true
                    }
                    Key.DirectionUp -> {
                        onKey(EmulatorScreenIntent.Key(EmulatorKeys.ARROW_UP))
                        true
                    }
                    Key.DirectionDown -> {
                        onKey(EmulatorScreenIntent.Key(EmulatorKeys.ARROW_DOWN))
                        true
                    }
                    else -> {
                        val codePoint = event.utf16CodePoint
                        if (codePoint in 32..126) {
                            onKey(EmulatorScreenIntent.Text(codePoint.toChar().toString()))
                            true
                        } else {
                            false
                        }
                    }
                }
            }
            .pointerInput(placement) {
                var down = false
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        val pos = change.position
                        when (event.type) {
                            PointerEventType.Press -> {
                                down = true
                                runCatching { focusRequester.requestFocus() }
                                onPointer(pos.x, pos.y, placement.display, true)
                            }
                            PointerEventType.Move -> {
                                if (down) {
                                    onPointer(pos.x, pos.y, placement.display, true)
                                }
                            }
                            PointerEventType.Release -> {
                                if (down) {
                                    down = false
                                    onPointer(pos.x, pos.y, placement.display, false)
                                }
                            }
                        }
                        change.consume()
                    }
                }
            },
    ) {
        if (viewW <= 0f || viewH <= 0f) return@Canvas

        val pf = placement.frame
        if (pf != null && background != null) {
            val centerX = pf.left + pf.width / 2f
            val centerY = pf.top + pf.height / 2f
            val unrotatedW = if (placement.canvasDegrees % 180f != 0f) pf.height else pf.width
            val unrotatedH = if (placement.canvasDegrees % 180f != 0f) pf.width else pf.height
            val dstOffset = IntOffset(
                (centerX - unrotatedW / 2f).roundToInt(),
                (centerY - unrotatedH / 2f).roundToInt(),
            )
            val dstSize = IntSize(unrotatedW.roundToInt(), unrotatedH.roundToInt())
            withTransform({
                rotate(placement.canvasDegrees, pivot = Offset(centerX, centerY))
            }) {
                drawImage(
                    image = background,
                    dstOffset = dstOffset,
                    dstSize = dstSize,
                    filterQuality = FilterQuality.Medium,
                )
            }
        }

        val cornerRadiusPx = if (skin != null) skin.cornerRadius * placement.scale else 0f
        val displayRect = placement.display

        if (skin != null && background == null) {
            val paddingPx = 12.dp.toPx()
            val bezelRadius = cornerRadiusPx + paddingPx
            drawRoundRect(
                color = Color(0xFF1E1E1E),
                topLeft = Offset(displayRect.left - paddingPx, displayRect.top - paddingPx),
                size = Size(displayRect.width + 2 * paddingPx, displayRect.height + 2 * paddingPx),
                cornerRadius = CornerRadius(bezelRadius, bezelRadius),
            )
        }

        val dstOffset = IntOffset(displayRect.left.roundToInt(), displayRect.top.roundToInt())
        val dstSize = IntSize(displayRect.width.roundToInt(), displayRect.height.roundToInt())
        if (dstSize.width > 0 && dstSize.height > 0) {
            if (cornerRadiusPx > 0f) {
                val clipPath = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = displayRect.left,
                            top = displayRect.top,
                            right = displayRect.left + displayRect.width,
                            bottom = displayRect.top + displayRect.height,
                            cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                        ),
                    )
                }
                clipPath(clipPath) {
                    drawImage(
                        image = bitmap,
                        dstOffset = dstOffset,
                        dstSize = dstSize,
                        filterQuality = FilterQuality.Medium,
                    )
                }
            } else {
                drawImage(
                    image = bitmap,
                    dstOffset = dstOffset,
                    dstSize = dstSize,
                    filterQuality = FilterQuality.Medium,
                )
            }
        }
    }
}
