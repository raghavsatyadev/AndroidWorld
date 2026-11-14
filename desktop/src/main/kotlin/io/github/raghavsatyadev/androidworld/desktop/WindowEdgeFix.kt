package io.github.raghavsatyadev.androidworld.desktop

import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JFrame

/**
 * At a fractional display scale (125 %, 175 %) Windows and AWT round the window's title bar height
 * differently, so the drawing surface can sit one pixel above the window's content area. The last pixel
 * row is then never painted and shows stale pixels, most visibly as a white line along the bottom of a
 * maximized window. Making the content one unit larger than the area covers that row; the extra unit is
 * clipped by the window.
 */
internal fun JFrame.coverFractionalEdge() {
    val root = rootPane
    fun grow() {
        val scale = graphicsConfiguration?.defaultTransform ?: return
        if (scale.scaleX % 1.0 == 0.0 && scale.scaleY % 1.0 == 0.0) return
        val edges = insets
        val wide = width - edges.left - edges.right + 1
        val tall = height - edges.top - edges.bottom + 1
        if (wide <= 1 || tall <= 1 || (root.width == wide && root.height == tall)) return
        root.setSize(wide, tall)
        root.validate()
    }
    root.addComponentListener(object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent) = grow()
        override fun componentMoved(e: ComponentEvent) = grow()
    })
    grow()
}
