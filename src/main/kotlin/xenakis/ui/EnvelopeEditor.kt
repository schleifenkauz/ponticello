package xenakis.ui

import hextant.undo.UndoManager
import javafx.scene.control.Label
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Pane
import javafx.scene.shape.Circle
import javafx.scene.shape.Polyline
import xenakis.impl.Point
import xenakis.model.Envelope
import xenakis.model.EnvelopeEdit
import xenakis.model.ScoreObject
import xenakis.sc.LinearTransformation
import xenakis.sc.NumericalControlSpec
import xenakis.sc.mapOnto

class EnvelopeEditor(
    private val parameterName: String, private val envelope: Envelope,
    private val pane: Pane, private val scoreView: ScorePane,
    private val associatedObject: ScoreObject,
) : EnvelopeView {
    private val control
        get() = associatedObject.associatedEnvelopes.find { it.parameter == parameterName }
            ?: error("control $parameterName not found")
    private val spec get() = associatedObject.getSpec(parameterName) as NumericalControlSpec
    private val yTransform get() = spec.mapOnto(pane.height..0.0)
    private val xTransform get() = LinearTransformation(0.0..associatedObject.duration, 0.0..pane.width)

    private val valueGrid get() = spec.step.value

    private val mouseInfo = Label() styleClass "coordinate-info"
    private val handles = mutableListOf<Circle>()
    private val line = Polyline() styleClass "envelope-line"

    private val color get() = control.displayColor

    init {
        mouseInfo.isVisible = false
        line.stroke = color
        createNewPointsOnClick()
        setupPositionInfo()
        envelope.addView(this)
    }

    private fun setupPositionInfo() {
        line.setOnMouseEntered { mouseInfo.isVisible = true }
        line.setOnMouseExited { mouseInfo.isVisible = false }
        line.setOnMouseMoved { ev ->
            val t = transformXToTime(ev.x)
            val v = transformYToValue(ev.y)
            relocateInfoToMouse(ev)
            displayPosition(t, v)
        }
    }

    private fun createNewPointsOnClick() {
        line.setOnMouseClicked { ev ->
            if (ev.isAltDown) {
                val t = transformXToTime(ev.x)
                val v = transformYToValue(ev.y)
                val newPoint = Point(t, v)
                var idx = envelope.points.binarySearch(newPoint, compareBy(Point::x))
                idx = -(idx + 1)
                scoreView.context[UndoManager].record(EnvelopeEdit.AddPoint(newPoint, idx, envelope))
                envelope.addPoint(idx, newPoint)
                ev.consume()
            } else {
                bringToFront()
            }
        }
    }

    override fun addedPoint(idx: Int, point: Point) {
        val x = xTransform.map(point.x)
        val y = yTransform.map(point.y)
        line.points.addAll(idx * 2, listOf(x, y))
        addHandle(idx, x, y)
    }

    override fun removedPoint(idx: Int, point: Point) {
        val handle = handles.removeAt(idx)
        pane.children.remove(handle)
        line.points.remove(idx * 2, (idx + 1) * 2)
    }

    override fun changedPoint(idx: Int, newPoint: Point) {
        val (px, py) = newPoint
        displayPosition(px, py)

        val tx = xTransform.map(px)
        val ty = yTransform.map(py)

        handles[idx].centerX = tx
        handles[idx].centerY = ty

        line.points[idx * 2] = tx
        line.points[idx * 2 + 1] = ty
    }

    private fun transformXToTime(x: Double): Double =
        xTransform.unmap(x.snap(scoreView.timeSnap)).coerceIn(xTransform.sourceRange)

    private fun transformYToValue(y: Double) = yTransform.unmap(y).snap(valueGrid).coerceIn(yTransform.sourceRange)

    private fun displayPosition(t: Double, v: Double) {
        val scoreTime = associatedObject.start + t * associatedObject.duration
        val timeAccuracy = accuracy(scoreView.timeSnap)
        val valueAccuracy = accuracy(spec.step.value)
        mouseInfo.text = "t: ${scoreTime.format(timeAccuracy)}, $parameterName: ${v.format(valueAccuracy)}"
    }

    fun repaint() {
        removeChildren()
        pane.children.add(mouseInfo)
        handles.clear()
        line.points.clear()
        pane.children.add(line)

        for ((idx, p) in envelope.points.withIndex()) {
            val x = xTransform.map(p.x)
            val y = yTransform.map(p.y)
            line.points.addAll(x, y)
            addHandle(idx, x, y)
        }
    }

    fun removeChildren() {
        pane.children.removeAll(handles)
        pane.children.remove(line)
        pane.children.remove(mouseInfo)
    }

    private fun addHandle(idx: Int, x: Double, y: Double) {
        val handle = Circle(x, y, HANDLE_RADIUS, color)
        setupHandle(handle)
        pane.children.add(handle)
        handles.add(idx, handle)
    }

    private fun setupHandle(handle: Circle) {
        var dragging = false
        handle.stroke = color.darker()
        handle.strokeWidthProperty().bind(handle.hoverProperty().map { hover -> if (hover) 2.0 else 0.0 })
        handle.setupDragging(
            onPressed = { scoreView.context[UndoManager].beginCompoundEdit("Move envelope point") },
            onReleased = { scoreView.context[UndoManager].finishCompoundEdit("Move envelope point") }
        ) { ev, old, dx, dy ->
            val idx = handles.indexOf(handle)
            val newX = when (idx) {
                0 -> xTransform.map(0.0)
                envelope.points.size - 1 -> xTransform.map(associatedObject.duration)
                else -> (old.minX + dx / pane.parent.scaleX).coerceIn(xTransform.targetRange.reverseIfEmpty())
            }
            val newY = (old.minY + dy / pane.parent.scaleY).coerceIn(yTransform.targetRange.reverseIfEmpty())

            val px = transformXToTime(newX)
            val py = transformYToValue(newY)

            if (idx > 0 && px < envelope.points[idx - 1].x) return@setupDragging
            if (idx + 1 < envelope.points.size && px > envelope.points[idx + 1].x) return@setupDragging
            relocateInfoToMouse(ev)
            val oldPoint = envelope.points[idx]
            val newPoint = Point(px, py)
            scoreView.context[UndoManager].record(EnvelopeEdit.EditPoint(idx, oldPoint, newPoint, envelope))
            envelope.editPoint(idx, Point(px, py))
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
            val idx = handles.indexOf(handle)
            if (ev.button == MouseButton.SECONDARY) {
                if (idx != 0 && idx != envelope.points.size - 1) {
                    val point = envelope.points[idx]
                    scoreView.context[UndoManager].record(EnvelopeEdit.RemovePoint(point, idx, envelope))
                    envelope.removePoint(idx)
                }
            } else if (!ev.isShiftDown) {
                bringToFront()
            }
            ev.consume()
        }
        handle.setOnMouseEntered { ev ->
            mouseInfo.isVisible = true
            relocateInfoToMouse(ev)
            val idx = handles.indexOf(handle)
            val p = envelope.points[idx]
            displayPosition(p.x, p.y)
            ev.consume()
        }
        handle.setOnMouseExited { ev ->
            mouseInfo.isVisible = dragging
            ev.consume()
        }
    }

    private fun relocateInfoToMouse(ev: MouseEvent) {
        val infoPos = pane.screenToLocal(ev.screenX, ev.screenY)
        mouseInfo.relocate(infoPos.x, infoPos.y - 50)
        mouseInfo.toFront()
    }

    private fun bringToFront() {
        line.toFront()
        handles.forEach { h -> h.toFront() }
    }

    fun dispose() {
        envelope.removeView(this)
    }

    companion object {
        private const val HANDLE_RADIUS = 5.0
    }
}