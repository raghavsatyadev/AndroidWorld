package io.github.raghavsatyadev.androidworld.desktop.ui.inspector

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.core.application.layout.InspectorCapture
import io.github.raghavsatyadev.androidworld.core.application.layout.LayoutInspectorState
import kotlin.math.min
import org.jetbrains.skia.Image as SkiaImage

@Composable
fun InspectorScreenshot(
    capture: InspectorCapture,
    state: LayoutInspectorState,
    onTap: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(capture) {
        runCatching { SkiaImage.makeFromEncoded(capture.snapshot.png).toComposeImageBitmap() }.getOrNull()
    }

    if (bitmap == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Could not decode the screenshot.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val maxWPx = constraints.maxWidth.toFloat()
        val maxHPx = constraints.maxHeight.toFloat()
        val bw = bitmap.width.toFloat()
        val bh = bitmap.height.toFloat()
        if (bw <= 0f || bh <= 0f || maxWPx <= 0f || maxHPx <= 0f) return@BoxWithConstraints

        val s = min(maxWPx / bw, maxHPx / bh)
        val displayedWidthDp = with(density) { (bw * s).toDp() }
        val displayedHeightDp = with(density) { (bh * s).toDp() }
        val f = (capture.imageScale * s).toFloat()

        val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        val errorStrokeColor = MaterialTheme.colorScheme.error
        val errorFillColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
        val primaryStrokeColor = MaterialTheme.colorScheme.primary
        val primaryFillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)

        val stroke1px = Stroke(width = 1f)
        val stroke2dp = with(density) { Stroke(width = 2.dp.toPx()) }
        val stroke3dp = with(density) { Stroke(width = 3.dp.toPx()) }

        Box(modifier = Modifier.size(displayedWidthDp, displayedHeightDp).align(Alignment.TopStart)) {
            Image(
                bitmap = bitmap,
                contentDescription = "Screen capture",
                modifier = Modifier.fillMaxSize(),
            )
            Canvas(
                modifier = Modifier.fillMaxSize().pointerInput(capture, s) {
                    detectTapGestures { offset ->
                        onTap((offset.x / s).toDouble(), (offset.y / s).toDouble())
                    }
                },
            ) {
                for (node in capture.snapshot.hierarchy.root.descendantsAndSelf()) {
                    val b = node.bounds
                    if (!b.isEmpty) {
                        drawRect(
                            color = outlineColor,
                            topLeft = Offset(b.left * f, b.top * f),
                            size = Size(b.width * f, b.height * f),
                            style = stroke1px,
                        )
                    }
                }

                if (state.highlightIssues) {
                    for (finding in capture.findings) {
                        val b = finding.node.bounds
                        if (!b.isEmpty) {
                            val topLeft = Offset(b.left * f, b.top * f)
                            val size = Size(b.width * f, b.height * f)
                            drawRect(
                                color = errorFillColor,
                                topLeft = topLeft,
                                size = size,
                            )
                            drawRect(
                                color = errorStrokeColor,
                                topLeft = topLeft,
                                size = size,
                                style = stroke2dp,
                            )
                        }
                    }
                }

                val selected = state.selectedNode
                if (selected != null && !selected.bounds.isEmpty) {
                    val b = selected.bounds
                    val topLeft = Offset(b.left * f, b.top * f)
                    val size = Size(b.width * f, b.height * f)
                    drawRect(
                        color = primaryFillColor,
                        topLeft = topLeft,
                        size = size,
                    )
                    drawRect(
                        color = primaryStrokeColor,
                        topLeft = topLeft,
                        size = size,
                        style = stroke3dp,
                    )
                }
            }
        }
    }
}
