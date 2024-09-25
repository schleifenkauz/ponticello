package xenakis.ui

import hextant.undo.UndoManager
import javafx.geometry.Point2D
import javafx.scene.control.Label
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Pane
import javafx.scene.shape.Circle
import javafx.scene.shape.Polyline
import reaktive.value.binding.map
import reaktive.value.fx.asObservableValue
import xenakis.impl.Point
import xenakis.model.Envelope
import xenakis.model.EnvelopeControl
import xenakis.model.EnvelopeEdit
import xenakis.sc.LinearTransformation
import xenakis.sc.NumericalControlSpec
import xenakis.sc.mapOnto
import kotlin.math.abs

class EnvelopeEditor(
    val parameterName: String, private val envelope: Envelope,
    private val objectView: ScoreObjectView, private val pane: Pane,
) : EnvelopeView {
    private val control
        get() = associatedObject.associatedControls.getValue(parameterName) as EnvelopeControl
    private val spec get() = associatedObject.getSpec(parameterName) as NumericalControlSpec
    private val xTransform get() = LinearTransformation(0.0..associatedObject.duration, 0.0..objectView.prefWidth)
    private val yTransform get() = spec.mapOnto(objectView.prefHeight..0.0)

    private val valueGrid get() = spec.step.get()

    private val mouseInfo = Label() styleClass "coordinate-info"
    private val handles = mutableListOf<Circle>()
    private val line = Polyline() styleClass "envelope-line"

    private val color get() = control.displayColor

    private val parentPane get() = objectView.pane
    private val associatedObject get() = objectView.instance.obj

    init {
        mouseInfo.isVisible = false
        line.strokeProperty().bind(color.asObservableValue())
        createNewPointsOnClick()
        setupPositionInfo()
        setupLineDragging()
        envelope.addView(this)
    }

    private fun setupPositionInfo() {
        line.setOnMouseEntered { mouseInfo.isVisible = true }
        line.setOnMouseExited { mouseInfo.isVisible = false }
        line.setOnMouseMoved { ev ->
            val t = transformXToTime(ev.x)
            val v = envelope.interpolateValueAt(t)
            relocateInfoToMouse(ev)
            displayPosition(t, v)
        }
    }

    private fun setupLineDragging() {
        line.setupDragging(
            onPressed = { parentPane.context[UndoManager].beginCompoundEdit("Move envelope segment") },
            onReleased = { parentPane.context[UndoManager].finishCompoundEdit("Move envelope segment") }
        ) { ev, start, _, _, dy ->
            val t = transformXToTime(ev.x)
            var segmentIdx = envelope.points.map(Point::x).binarySearch(t)
            if (segmentIdx < 0) segmentIdx = -(segmentIdx + 1)
            if (segmentIdx == 0 || segmentIdx == envelope.points.size) return@setupDragging
            if (envelope.points[segmentIdx - 1].y != envelope.points[segmentIdx].y) return@setupDragging
            val y = start.y + dy
            val value = transformYToValue(y)
            envelope.editPoint(segmentIdx - 1, value)
            envelope.editPoint(segmentIdx, value)
            relocateInfoToMouse(ev)
        }
    }

    private fun createNewPointsOnClick() {
        line.setOnMouseClicked { ev ->
            if (ev.isAltDown) {
                val t = transformXToTime(ev.x)
                val v = envelope.interpolateValueAt(t)
                val newPoint = Point(t, v)
                var idx = envelope.points.binarySearch(newPoint, compareBy(Point::x))
                idx = -(idx + 1)
                parentPane.context[UndoManager].record(EnvelopeEdit.AddPoint(newPoint, idx, envelope))
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

    private fun transformXToTime(x: Double): Double {
        val scoreX = x + objectView.layoutX
        val scoreY = objectView.paneY
        val xInScore = parentPane.snapToGrid(scoreX, scoreY).x
        val xInObject = xInScore - objectView.layoutX
        return xTransform.unmap(xInObject).coerceIn(xTransform.sourceRange)
    }

    private fun transformYToValue(y: Double) = yTransform.unmap(y).snap(valueGrid).coerceIn(yTransform.sourceRange)

    private fun displayPosition(t: Double, v: Double) {
        var coords = Point2D(parentPane.getWidth(t), 0.0)
        coords = objectView.localToScene(coords)
        coords = parentPane.rootPane.sceneToLocal(coords)
        val absoluteTime = parentPane.rootPane.getTime(coords.x)
        val timeAccuracy = parentPane.xAccuracy
        val timeStr = timeCode(absoluteTime, timeAccuracy)
        val valueAccuracy = accuracy(spec.step.get())
        mouseInfo.text = "t: $timeStr, $parameterName: ${v.format(valueAccuracy)}"
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
        val handle = Circle(x, y, HANDLE_RADIUS)
        handle.fillProperty().bind(color.asObservableValue())
        handle.strokeProperty().bind(color.map { c -> c.darker() }.asObservableValue())
        handle.strokeWidthProperty().bind(handle.hoverProperty().map { hover -> if (hover) 2.0 else 0.0 })
        setupHandle(handle)
        pane.children.add(handle)
        handles.add(idx, handle)
    }

    private fun setupHandle(handle: Circle) {
        var dragging = false
        handle.setupDragging(
            onPressed = { parentPane.context[UndoManager].beginCompoundEdit("Move envelope point") },
            onReleased = { parentPane.context[UndoManager].finishCompoundEdit("Move envelope point") }
        ) { ev, _, old, dx, dy ->
            val idx = handles.indexOf(handle)
            val newX = when (idx) {
                0 -> 0.0
                envelope.points.size - 1 -> objectView.prefWidth
                else -> (old.minX + dx).coerceIn(xTransform.targetRange.reverseIfEmpty())
            }
            val newY = (old.minY + dy).coerceIn(yTransform.targetRange.reverseIfEmpty())

            val px = transformXToTime(newX)
            val py = transformYToValue(newY)

            if (idx > 0 && px < envelope.points[idx - 1].x) return@setupDragging
            if (idx + 1 < envelope.points.size && px > envelope.points[idx + 1].x) return@setupDragging
            relocateInfoToMouse(ev)
            val oldPoint = envelope.points[idx]
            val newPoint = Point(px, py)
            parentPane.context[UndoManager].record(EnvelopeEdit.EditPoint(idx, oldPoint, newPoint, envelope))
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
            val point = envelope.points[idx]
            if (ev.button == MouseButton.SECONDARY) {
                if (idx != 0 && idx != envelope.points.size - 1) {
                    parentPane.context[UndoManager].record(EnvelopeEdit.RemovePoint(point, idx, envelope))
                    envelope.removePoint(idx)
                }
            } else if (ev.clickCount >= 2) {
                showNumberPrompt("$parameterName at t=${point.x}", spec.range, point.y, parentPane.context) { value ->
                    envelope.editPoint(idx, point.copy(y = value))
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
        removeChildren()
        envelope.removeView(this)
    }

    companion object {
        private const val HANDLE_RADIUS = 5.0
    }
}