package xenakis.ui

import hextant.fx.registerShortcuts
import javafx.beans.binding.Bindings
import javafx.geometry.HorizontalDirection
import javafx.scene.control.Label
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.paint.Color
import javafx.scene.shape.Circle
import javafx.scene.shape.Polyline
import reaktive.value.binding.map
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import xenakis.impl.Point
import xenakis.model.Envelope
import xenakis.model.EnvelopeControl
import xenakis.sc.LinearTransformation
import xenakis.sc.NumericalControlSpec
import xenakis.sc.mapOnto
import kotlin.math.absoluteValue

class EnvelopeEditor(
    val parameterName: String, private val envelope: Envelope,
    private val objectView: ScoreObjectView,
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
        mouseInfo.textFillProperty().bind(objectView.backgroundColor.map(Color::invert).asObservableValue())
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
            displayPosition(t, v)
        }
    }

    private fun setupLineDragging() {
        var draggingSegment = false
        line.setupDragging(
            onPressed = { ev ->
                val t = transformXToTime(ev.x)
                var segmentIdx = envelope.points.map(Point::x).binarySearch(t)
                if (segmentIdx < 0) segmentIdx = -(segmentIdx + 1)
                if (segmentIdx == 0 || segmentIdx == envelope.points.size) return@setupDragging
                val diff = envelope.points[segmentIdx - 1].y - envelope.points[segmentIdx].y
                if (diff.absoluteValue >= spec.step.get()) return@setupDragging
                draggingSegment = true
                envelope.beginSegmentEdit(segmentIdx - 1)
            },
            onReleased = {
                if (draggingSegment) {
                    envelope.finishEdit()
                    draggingSegment = false
                }
            }
        ) { ev, start, _, _, dy ->
            if (draggingSegment) {
                val t = transformXToTime(ev.x)
                val y = start.y + dy
                val value = transformYToValue(y)
                envelope.editSegment(value)
                displayPosition(t, value)
            }
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
        objectView.children.remove(handle)
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
        val snappedX = parentPane.snapToGrid(scoreX, scoreY).x
        parentPane.markX(snappedX)
        val xInObject = snappedX - objectView.layoutX
        return xTransform.unmap(xInObject).coerceIn(xTransform.sourceRange)
    }

    private fun transformYToValue(y: Double) = yTransform.unmap(y).snap(valueGrid).coerceIn(yTransform.sourceRange)

    private fun displayPosition(t: Double, v: Double) {
        val x = parentPane.getWidth(t)
        /*var coords = Point2D(x, 0.0)
        coords = objectView.localToScene(coords)
        coords = parentPane.rootPane.sceneToLocal(coords)
        val absoluteTime = parentPane.rootPane.getTime(coords.x)
        val timeAccuracy = parentPane.xAccuracy
        val timeStr = timeCode(absoluteTime, timeAccuracy)
        */
        val valueAccuracy = accuracy(spec.step.get())
        mouseInfo.text = "$parameterName: ${v.format(valueAccuracy)}"
        var y = yTransform.map(v)
        val infoHeight = mouseInfo.prefHeight(-1.0)
        val infoWidth = mouseInfo.prefWidth(-1.0)
        if (y < infoHeight * 2) y += infoHeight / 1.5 else y -= infoHeight * 1.5
        val infoX = (x - infoWidth / 2).coerceIn(0.0, objectView.width - infoWidth)
        mouseInfo.relocate(infoX, y)
    }

    fun repaint() {
        removeChildren()
        objectView.children.add(mouseInfo)
        handles.clear()
        line.points.clear()
        objectView.children.add(line)

        for ((idx, p) in envelope.points.withIndex()) {
            val x = xTransform.map(p.x)
            val y = yTransform.map(p.y)
            line.points.addAll(x, y)
            addHandle(idx, x, y)
        }
    }

    private fun removeChildren() {
        objectView.children.removeAll(handles)
        objectView.children.remove(line)
        objectView.children.remove(mouseInfo)
    }

    private fun addHandle(idx: Int, x: Double, y: Double) {
        val handle = Circle(x, y, HANDLE_RADIUS)
        handle.isFocusTraversable = true
        handle.fillProperty().bind(color.asObservableValue())
        handle.strokeProperty().bind(Bindings.createObjectBinding({
            val focused = handle.isFocused
            if (focused) color.now.invert() else color.now.darker()
        }, handle.focusedProperty(), color.asObservableValue()))
        handle.strokeWidthProperty().bind(
            handle.hoverProperty().or(handle.focusedProperty())
                .map { hover -> if (hover) 2.0 else 0.0 }
        )
        setupHandle(handle)
        objectView.children.add(handle)
        handles.add(idx, handle)
    }

    private fun setupHandle(handle: Circle) {
        var dragging = false
        handle.registerShortcuts {
            val idx = handles.indexOf(handle)
            on("V") { showPromptFor(idx) }
            on("DELETE") { removeHandle(idx) }
        }
        handle.registerShortcuts(KeyEvent.KEY_PRESSED) {
            val idx = handles.indexOf(handle)
            on("LEFT") { adjustPointHorizontal(idx, HorizontalDirection.LEFT) }
            on("RIGHT") { adjustPointHorizontal(idx, HorizontalDirection.RIGHT) }
            on("UP") { adjustPointVertical(idx, +1) }
            on("DOWN") { adjustPointVertical(idx, -1) }
        }
        handle.setupDragging(
            onPressed = { envelope.beginPointEdit(handles.indexOf(handle)) },
            onReleased = { envelope.finishEdit() }
        ) { _, _, old, dx, dy ->
            val idx = handles.indexOf(handle)
            val newX = when (idx) {
                0 -> 0.0
                envelope.points.size - 1 -> objectView.prefWidth
                else -> (old.minX + dx).coerceIn(xTransform.targetRange.reverseIfEmpty())
            }
            val newY = (old.minY + dy).coerceIn(yTransform.targetRange.reverseIfEmpty())

            var t = transformXToTime(newX)
            val v = transformYToValue(newY)

            if (idx != 0 && idx != envelope.points.size - 1) {
                t = t.coerceIn(envelope.points[idx - 1].x..envelope.points[idx + 1].x)
            }
            displayPosition(t, v)
            envelope.editPoint(Point(t, v))
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
                removeHandle(idx)
            } else if (ev.clickCount >= 2) {
                showPromptFor(idx)
            } else if (!ev.isShiftDown) {
                handle.requestFocus()
                bringToFront()
            }
            ev.consume()
        }
        handle.setOnMouseEntered { ev ->
            mouseInfo.isVisible = true
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

    private fun adjustPointVertical(idx: Int, dir: Int) {
        val delta = spec.step.get() * dir
        envelope.adjustPointVertical(idx, delta)
    }

    private fun adjustPointHorizontal(idx: Int, dir: HorizontalDirection) {
        val delta = objectView.getDeltaX(dir)
        envelope.adjustPointHorizontal(idx, delta)
    }

    private fun showPromptFor(idx: Int) {
        val point = envelope.points[idx]
        val value = DoubleInput("Value for $parameterName", point.y, spec.range)
            .showDialog(parentPane.context) ?: return
        envelope.editPoint(idx, value)
    }

    private fun removeHandle(idx: Int) {
        if (idx != 0 && idx != envelope.points.size - 1) {
            envelope.removePoint(idx)
        }
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