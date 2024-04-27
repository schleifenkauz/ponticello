package xenakis.impl

import javafx.application.Platform
import javafx.geometry.Point2D
import javafx.geometry.Pos
import javafx.scene.Group
import javafx.scene.Node
import javafx.scene.control.ScrollPane
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import kotlin.concurrent.thread
import kotlin.math.exp

class ZoomableScrollPane(private val target: Region) : ScrollPane() {
    private lateinit var zoomNode: Node
    private var rescaling = false

    init {
        setupContent()
    }

    private fun setupContent() {
        vbarPolicy = ScrollBarPolicy.NEVER
        zoomNode = Group(target)
        val box = VBox(zoomNode)
        box.alignment = Pos.CENTER
        box.setOnScroll { e ->
            if (e.isControlDown) {
                val zoomX = !e.isAltDown
                val zoomY = !e.isShiftDown
                val wheelDelta = if (e.isShiftDown) e.textDeltaX else e.textDeltaY
                onScroll(wheelDelta, Point2D(e.x, e.y), zoomX, zoomY)
                e.consume()
            }
        }
        content = box
        thread {
            Thread.sleep(100)
            Platform.runLater {
                target.scaleX = viewportBounds.width / (target.prefWidth + 12.0)
            }
        }
        viewportBoundsProperty().addListener { _, _, new ->
            target.scaleY = (new.height - 5.0) / target.prefHeight
        }
    }

    private fun onScroll(wheelDelta: Double, mousePoint: Point2D, zoomX: Boolean, zoomY: Boolean) {
        rescaling = true
        val zoomFactor = exp(wheelDelta * ZOOM_INTENSITY)
        val innerBounds = zoomNode.layoutBounds
        val viewportBounds = viewportBounds

        // calculate pixel offsets from [0, 1] range
        val valX = hvalue * (innerBounds.width - viewportBounds.width)
//        val valY = vvalue * (innerBounds.height - viewportBounds.height)

        if (zoomX) {
            val minScaleX = viewportBounds.width / (target.prefWidth + 12.0)
            val newScaleX = (target.scaleX * zoomFactor).coerceIn(minScaleX, 10.0)
            target.scaleX = newScaleX
        }
//        if (zoomY) {
//            val minScaleY = viewportBounds.height / target.prefHeight
//            val newScaleY = (target.scaleY * zoomFactor).coerceAtLeast(minScaleY)
//            target.scaleY = newScaleY
//        }
        layout() // refresh ScrollPane scroll positions & target bounds

        // convert target coordinates to zoomTarget coordinates
        val posInZoomTarget = target.parentToLocal(zoomNode.parentToLocal(mousePoint))

        // calculate adjustment of scroll position (pixels)
        val adjustment = target.localToParentTransform.deltaTransform(posInZoomTarget.multiply(zoomFactor - 1))

        // convert back to [0, 1] range
        // (too large/small values are automatically corrected by ScrollPane)
        val updatedInnerBounds = zoomNode.boundsInLocal
        if (zoomX) hvalue = (valX + adjustment.x) / (updatedInnerBounds.width - viewportBounds.width)
//        if (zoomY) vvalue = (valY + adjustment.y) / (updatedInnerBounds.height - viewportBounds.height)
        rescaling = false
    }

    companion object {
        private const val ZOOM_INTENSITY = 0.02
    }
}