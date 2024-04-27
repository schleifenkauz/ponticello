package xenakis.ui

import javafx.beans.binding.Bindings
import javafx.scene.control.Label
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.shape.Circle
import javafx.scene.shape.Polyline
import javafx.scene.text.Font
import xenakis.impl.Point
import xenakis.impl.dist
import xenakis.sc.LinearTransformation
import xenakis.sc.NumericalControlSpec
import xenakis.sc.mapOnto

class EnvelopeEditor(
    val parameterName: String,
    val spec: NumericalControlSpec,
    private val points: MutableList<Point>,
    private val pane: Pane, private val scoreView: ScoreView,
    color: Color, contrastColor: Color = Color.BLACK,
    var valueGrid: Double,
    private val fixEdgePoints: Boolean
) {
    private val yTransform get() = spec.mapOnto(pane.height..0.0)
    private val xTransform get() = LinearTransformation(0.0..1.0, 0.0..pane.width)

    private val mouseInfo = Label().apply {
        font = Font(14.0)
        isVisible = false
        textFill = contrastColor
    }
    private val handles = mutableListOf<Circle>()
    val line = Polyline()

    var color: Color = color
        set(value) {
            field = value
            for (h in handles) h.fill = value
            line.stroke = value
        }

    init {
        line.stroke = color
        line.setOnMouseClicked { ev ->
            if (ev.isShiftDown) {
                val t = transformXToTime(ev.x)
                val v = transformYToValue(ev.y)
                val newPoint = Point(t, v)
                var i = points.binarySearch(newPoint, compareBy(Point::x))
                i = -(i + 1)
                points.add(i, newPoint)
                val x = xTransform.map(t)
                val y = yTransform.map(v)
                line.points.addAll(i * 2, listOf(x, y))
                val handle = createHandle(color, x, y)
                addHandle(newPoint, handle)
                ev.consume()
            } else {
                bringToFront()
            }
        }
        line.strokeWidthProperty().bind(
            Bindings.`when`(line.hoverProperty())
                .then(6.0).otherwise(3.0)
                .divide(scoreView.scaleYProperty())
        )
        line.setOnMouseEntered {
            mouseInfo.isVisible = true
        }
        line.setOnMouseExited {
            mouseInfo.isVisible = false
        }
        line.setOnMouseMoved { ev ->
            val t = transformXToTime(ev.x)
            val v = transformYToValue(ev.y)
            relocateInfoToMouse(ev)
            displayPosition(t, v)
        }
    }

    private fun transformXToTime(x: Double): Double =
        xTransform.unmap(x.snap(scoreView.timeSnap)).coerceIn(xTransform.sourceRange)

    private fun transformYToValue(y: Double) = yTransform.unmap(y).snap(valueGrid).coerceIn(yTransform.sourceRange)

    private fun createHandle(color: Color, x: Double, y: Double): Circle {
        val container = scoreView
        return Circle(x, y, HANDLE_RADIUS, color).antiScale(container)
    }

    private fun displayPosition(x: Double, y: Double) {
        val t = x * pane.width / ScoreView.PIXELS_PER_SECOND
        mouseInfo.text = "t: ${t.format(3)}, $parameterName: ${y.format(3)}"
    }

    fun setContrastColor(color: Color) {
        mouseInfo.textFill = color
    }

    fun repaint() {
        removeChildren()
        pane.children.add(mouseInfo)
        handles.clear()
        line.points.clear()
        pane.children.add(line)
        if (!mouseInfo.scaleYProperty().isBound) mouseInfo.antiScale(scoreView)

        for (p in points) {
            val x = xTransform.map(p.x)
            val y = yTransform.map(p.y)
            line.points.addAll(x, y)
            addHandle(p, createHandle(color, x, y))
        }
    }

    fun removeChildren() {
        pane.children.removeAll(handles)
        pane.children.remove(line)
        pane.children.remove(mouseInfo)
    }

    private fun addHandle(p: Point, handle: Circle) {
        var dragging = false
        handle.stroke = color.darker()
        handle.strokeWidthProperty().bind(handle.hoverProperty().map { hover -> if (hover) 2.0 else 0.0 })
        handle.setupDragging { ev, old, dx, dy ->
            val idx = points.indexOf(p)
            val newX = when {
                fixEdgePoints && idx == 0 -> xTransform.map(0.0)
                fixEdgePoints && idx == points.size - 1 -> xTransform.map(1.0)
                else -> (old.minX + dx / pane.parent.scaleX).coerceIn(xTransform.targetRange.reverseIfEmpty())
            }
            val newY = (old.minY + dy / pane.parent.scaleY).coerceIn(yTransform.targetRange.reverseIfEmpty())

            val px = transformXToTime(newX)
            val py = transformYToValue(newY)

            if (idx > 0 && px < points[idx - 1].x) return@setupDragging
            if (idx + 1 < points.size && px > points[idx + 1].x) return@setupDragging
            p.x = px
            p.y = py
            relocateInfoToMouse(ev)
            displayPosition(p.x, p.y)

            handle.centerX = xTransform.map(px)
            handle.centerY = yTransform.map(py)
            line.points[idx * 2] = handle.centerX
            line.points[idx * 2 + 1] = handle.centerY
        }
        handle.addEventHandler(MouseEvent.MOUSE_PRESSED) {
            dragging = true
        }
        handle.addEventHandler(MouseEvent.MOUSE_RELEASED) { ev ->
            dragging = false
            val mousePos = Point(ev.screenX, ev.screenY)
            val center = handle.localToScreen(handle.centerX, handle.centerY)
            mouseInfo.isVisible = mousePos.dist(Point(center.x, center.y)) <= handle.radius
        }
        handle.setOnMouseClicked { ev ->
            val idx = points.indexOf(p)
            if (ev.button == MouseButton.SECONDARY) {
                if (idx != 0 && idx != points.size - 1) {
                    points.remove(p)
                    pane.children.remove(handle)
                    line.points.remove(idx * 2, (idx + 1) * 2)
                    handles.remove(handle)
                }
            } else if (!ev.isShiftDown) {
                bringToFront()
            }
            ev.consume()
        }
        handle.setOnMouseEntered { ev ->
            mouseInfo.isVisible = true
            relocateInfoToMouse(ev)
            displayPosition(p.x, p.y)
            ev.consume()
        }
        handle.setOnMouseExited { ev ->
            mouseInfo.isVisible = dragging
            ev.consume()
        }
        pane.children.add(handle)
        handles.add(handle)
    }

    private fun relocateInfoToMouse(ev: MouseEvent) {
        val infoPos = pane.screenToLocal(ev.screenX, ev.screenY)
        mouseInfo.relocate(infoPos.x, infoPos.y - 50 / scoreView.scaleY)
        mouseInfo.toFront()
    }

    private fun bringToFront() {
        line.toFront()
        handles.forEach { h -> h.toFront() }
    }

    companion object {
        private const val HANDLE_RADIUS = 5.0
    }
}