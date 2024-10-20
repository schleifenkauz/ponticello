package xenakis.ui.score

import hextant.fx.registerShortcuts
import javafx.beans.binding.Bindings
import javafx.geometry.HorizontalDirection
import javafx.geometry.Point2D
import javafx.scene.control.Label
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseButton.PRIMARY
import javafx.scene.input.MouseButton.SECONDARY
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.shape.Circle
import javafx.scene.shape.Polyline
import reaktive.value.binding.map
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import xenakis.impl.*
import xenakis.model.score.*
import xenakis.model.score.Envelope.EnvelopePoint
import xenakis.sc.NumericalControlSpec
import xenakis.sc.mapOnto
import xenakis.ui.impl.dist
import xenakis.ui.impl.rootPane
import xenakis.ui.impl.setupDragging
import xenakis.ui.impl.styleClass
import xenakis.ui.prompt.ControlSpecPrompt
import xenakis.ui.prompt.DecimalPrompt

class EnvelopeEditor(
    val parameterName: String, val envelope: Envelope,
    val objectView: ScoreObjectView, val pane: Pane
) : EnvelopeView {
    private val color get() = control.displayColor
    private val parentPane get() = objectView.pane
    private val associatedObject get() = objectView.instance.obj

    private val control
        get() = associatedObject.associatedControls.getValue(parameterName) as EnvelopeControl
    private val spec get() = associatedObject.getSpec(parameterName) as NumericalControlSpec
    private val yTransform get() = spec.mapOnto(pane.prefHeight..0.0)

    private val valueGrid get() = spec.step.get()

    private val mouseInfo = Label() styleClass "coordinate-info"
    private val handles = mutableListOf<Circle>()
    private val line = Polyline() styleClass "envelope-line"

    init {
        mouseInfo.isVisible = false
        line.strokeProperty().bind(color.asObservableValue())
        mouseInfo.textFillProperty().bind(objectView.backgroundColor.map(Color::invert).asObservableValue())
        configureMouseActions()
        setupPositionInfo()
        setupLineDragging()
        envelope.addView(this)
    }

    private fun setupPositionInfo() {
        line.setOnMouseEntered { mouseInfo.isVisible = true }
        line.setOnMouseExited { mouseInfo.isVisible = false }
        line.setOnMouseMoved { ev ->
            val t = parentPane.getDuration(ev.x)
            val v = envelope.interpolateValueAt(t)
            displayPosition(t, v)
        }
    }

    private fun setupLineDragging() {
        var draggingSegment = false
        line.setupDragging(
            onPressed = { ev ->
                val t = transformXToTime(ev.x)
                var segmentIdx = envelope.points.map(EnvelopePoint::time).binarySearch(t)
                if (segmentIdx < 0) segmentIdx = -(segmentIdx + 1)
                if (segmentIdx == 0 || segmentIdx == envelope.points.size) return@setupDragging
                val diff = envelope.points[segmentIdx - 1].value - envelope.points[segmentIdx].value
                if (diff.abs() >= spec.step.get()) return@setupDragging
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

    private fun configureMouseActions() {
        if (pane != objectView) {
            pane.setOnMouseClicked { ev ->
                when {
                    ev.button == SECONDARY && ev.clickCount == 1 -> createNewPoint(ev, interpolate = false)
                }
                ev.consume()
            }
        }
        line.setOnMouseClicked { ev ->
            when {
                ev.button == PRIMARY && ev.clickCount == 2 -> {
                    if (ev.isShiftDown) {
                        val obj = objectView.instance.obj
                        if (obj is SynthObject) {
                            ControlSpecPrompt(obj, parameterName, spec).showDialog(objectView.context, pane)
                        }
                    } else objectView.pane.context.rootPane.magnifyEnvelope(this)
                }

                ev.button == PRIMARY && ev.isShiftDown && associatedObject is ParameterizedScoreObject -> {
                    val prompt = ControlSpecPrompt(associatedObject as ParameterizedScoreObject, parameterName, spec)
                    prompt.showDialog(associatedObject.context, anchorNode = pane)
                }

                ev.button == PRIMARY -> bringToFront()
                ev.button == SECONDARY -> createNewPoint(ev, interpolate = true)
                else -> {}
            }
            ev.consume()
        }
    }

    private fun createNewPoint(ev: MouseEvent, interpolate: Boolean) {
        val t = transformXToTime(ev.x)
        val v = if (interpolate) envelope.interpolateValueAt(t) else transformYToValue(ev.y)
        val newPoint = EnvelopePoint(t, v)
        var idx = envelope.points.binarySearch(newPoint, compareBy(EnvelopePoint::time))
        if (idx >= 0) return //no duplicates allowed!
        idx = -(idx + 1)
        envelope.addPoint(idx, newPoint)
    }

    override fun addedPoint(idx: Int, point: EnvelopePoint) {
        val x = parentPane.getWidth(point.time)
        val y = yTransform.map(point.value.toDouble())
        line.points.addAll(idx * 2, listOf(x, y))
        addHandle(idx, x, y)
    }

    override fun removedPoint(idx: Int, point: EnvelopePoint) {
        val handle = handles.removeAt(idx)
        pane.children.remove(handle)
        line.points.remove(idx * 2, (idx + 1) * 2)
    }

    override fun changedPoint(idx: Int, newPoint: EnvelopePoint) {
        val (px, py) = newPoint
        displayPosition(px, py)

        val tx = parentPane.getWidth(px)
        val ty = yTransform.map(py.toDouble())

        handles[idx].centerX = tx
        handles[idx].centerY = ty

        line.points[idx * 2] = tx
        line.points[idx * 2 + 1] = ty
    }

    private fun transformXToTime(x: Double): Decimal {
        val pos = ObjectPosition(parentPane.getDuration(x), 0.0.asY) + objectView.absolutePosition
        val (t, _) = parentPane.context.rootPane.snapToGrid(pos)
        parentPane.markT(t)
        return (t - objectView.absolutePosition.time).coerceIn(zero..associatedObject.duration)
    }

    private fun transformYToValue(y: Double): Decimal =
        yTransform.unmap(y).snap(valueGrid).coerceIn(yTransform.sourceRange)

    private fun displayPosition(t: Decimal, v: Decimal) {
        val x = parentPane.getWidth(t)
        mouseInfo.text = "$parameterName: ${v.toCanonicalString()}"
        var y = yTransform.map(v.toDouble())
        val infoHeight = mouseInfo.prefHeight(-1.0)
        val infoWidth = mouseInfo.prefWidth(-1.0)
        if (objectView.prefWidth < infoWidth) return
        if (y < infoHeight * 2) y += infoHeight / 1.5 else y -= infoHeight * 1.5
        val infoX = (x - infoWidth / 2).coerceIn(0.0, pane.prefWidth - infoWidth)
        mouseInfo.relocate(infoX, y)
    }

    fun repaint() {
        removeChildren()
        pane.children.add(mouseInfo)
        handles.clear()
        line.points.clear()
        pane.children.add(line)

        for ((idx, p) in envelope.points.withIndex()) {
            val x = parentPane.getWidth(p.time)
            val y = yTransform.map(p.value.toDouble())
            line.points.addAll(x, y)
            addHandle(idx, x, y)
        }
    }

    private fun removeChildren() {
        pane.children.removeAll(handles)
        pane.children.remove(line)
        pane.children.remove(mouseInfo)
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
        handle.viewOrder = -1000.0
        setupHandle(handle)
        pane.children.add(handle)
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
            var t = when (idx) {
                0 -> 0.0.asTime
                envelope.points.size - 1 -> associatedObject.duration
                else -> transformXToTime(old.minX + dx)
            }
            val y = (old.minY + dy).coerceIn(yTransform.targetRange.reverseIfEmpty())

            val v = transformYToValue(y)

            if (idx != 0 && idx != envelope.points.size - 1) {
                t = t.coerceIn(envelope.points[idx - 1].time..envelope.points[idx + 1].time)
            }
            displayPosition(t, v)
            envelope.editPoint(EnvelopePoint(t, v))
        }
        handle.addEventHandler(MouseEvent.MOUSE_PRESSED) {
            dragging = true
        }
        handle.addEventHandler(MouseEvent.MOUSE_RELEASED) { ev ->
            dragging = false
            val mousePos = Point2D(ev.screenX, ev.screenY)
            val center = handle.localToScreen(handle.centerX, handle.centerY)
            mouseInfo.isVisible = mousePos dist Point2D(center.x, center.y) <= handle.radius
        }
        handle.setOnMouseClicked { ev ->
            val idx = handles.indexOf(handle)
            if (ev.button == SECONDARY) {
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
            displayPosition(p.time, p.value)
            ev.consume()
        }
        handle.setOnMouseExited { ev ->
            mouseInfo.isVisible = dragging
            ev.consume()
        }
    }

    private fun adjustPointVertical(idx: Int, dir: Int) {
        val delta = spec.step.get() * dir
        if (envelope.points[idx].value + delta in spec.range) {
            envelope.adjustPointVertical(idx, delta)
        }
    }

    private fun adjustPointHorizontal(idx: Int, dir: HorizontalDirection) {
        val delta = objectView.getDeltaX(dir)
        envelope.adjustPointHorizontal(idx, delta)
    }

    private fun showPromptFor(idx: Int) {
        val point = envelope.points[idx]
        val value = DecimalPrompt("Value for $parameterName", point.value, spec.range)
            .showDialog(parentPane.context, handles[idx]) ?: return
        envelope.editPoint(idx, value.value.snap(valueGrid))
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