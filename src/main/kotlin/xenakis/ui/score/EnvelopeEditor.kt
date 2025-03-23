package xenakis.ui.score

import fxutils.dist
import fxutils.registerShortcuts
import fxutils.styleClass
import javafx.beans.binding.Bindings
import javafx.geometry.HorizontalDirection
import javafx.geometry.Point2D
import javafx.scene.Cursor
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
import xenakis.model.obj.ParameterizedObject
import xenakis.model.score.Envelope
import xenakis.model.score.Envelope.EnvelopePoint
import xenakis.model.score.EnvelopeControl
import xenakis.model.score.ObjectPosition
import xenakis.model.score.ParameterControlList.NamedParameterControl
import xenakis.sc.NumericalControlSpec
import xenakis.sc.mapOnto
import xenakis.ui.actions.Tool
import xenakis.ui.controls.ControlSpecPrompt
import xenakis.ui.controls.NumericalControlSpecPrompt
import xenakis.ui.controls.DecimalPrompt
import xenakis.ui.impl.rootPane
import xenakis.ui.impl.setupDragging
import kotlin.math.pow

class EnvelopeEditor(
    val namedControl: NamedParameterControl, val envelope: Envelope,
    val objectView: ScoreObjectView, val pane: Pane
) : EnvelopeView {
    private val context get() = parentPane.context

    private val control get() = namedControl.now as EnvelopeControl
    
    val parameterName get() = namedControl.name.now

    private val color get() = control.displayColor
    private val parentPane get() = objectView.pane
    private val associatedObject get() = objectView.instance.obj

    private val spec get() = namedControl.spec.now as NumericalControlSpec
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
            context, dragTool = Tool.Pointer,
            defaultCursor = Cursor.CROSSHAIR, dragCursor = Cursor.V_RESIZE,
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
                ev.button == PRIMARY && ev.isControlDown -> objectView.pane.context.rootPane.magnifyEnvelope(this)

                ev.button == PRIMARY && ev.isShiftDown && associatedObject is ParameterizedObject -> {
                    val parentObject = objectView.instance.obj as ParameterizedObject
                    ControlSpecPrompt.create(
                        parameterName,
                        parentObject,
                        spec,
                    )?.showDialog(anchorNode = pane)
                }

                ev.button == PRIMARY && ev.clickCount == 2 -> setSegmentValueFromPrompt(ev)
                ev.button == PRIMARY -> bringToFront()
                ev.button == SECONDARY -> createNewPoint(ev, interpolate = true)
                else -> {}
            }
            ev.consume()
        }
    }

    private fun setSegmentValueFromPrompt(ev: MouseEvent) {
        val t = transformXToTime(ev.x)
        var idx = envelope.points.map(EnvelopePoint::time).binarySearch(t)
        if (idx >= 0) return
        idx = -(idx + 1)
        if (idx == 0 || idx == envelope.points.size) return
        val v1 = envelope.points[idx - 1].value
        val v2 = envelope.points[idx].value
        if ((v1 - v2).absoluteValue < 0.1.pow(spec.precision)) {
            val v = DecimalPrompt("Value for envelope segment", spec.precision, v1, spec.range)
                .showDialog(pane.scene.window, Point2D(ev.screenX, ev.screenY)) ?: return
            envelope.beginSegmentEdit(idx - 1)
            envelope.editSegment(v)
            envelope.finishEdit()
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
        val (t, _) = context.rootPane.snapToGrid(pos)
        parentPane.markT(t)
        return (t - objectView.absolutePosition.time).coerceIn(zero..associatedObject.duration)
    }

    private fun transformYToValue(y: Double): Decimal =
        yTransform.unmap(y).snap(valueGrid).coerceIn(yTransform.sourceRange)

    private fun displayPosition(t: Decimal, v: Decimal) {
        val x = parentPane.getWidth(t)
        mouseInfo.text = "${parameterName}: ${v.toCanonicalString()}"
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
        handle.cursor = Cursor.CROSSHAIR
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
            context,
            defaultCursor = Cursor.CROSSHAIR, dragCursor = Cursor.MOVE,
            dragTool = Tool.Pointer,
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
        if (idx == 0 || idx == envelope.points.size - 1) return
        val positionInPane = objectView.instance.position.plusTime(envelope.points[idx].time)
        val tBefore = envelope.points[idx].time
        val deltaT = objectView.getDeltaT(dir)
        val snapped = objectView.pane.snapToGrid(positionInPane)
        val tSnapped = snapped.time - objectView.instance.start
        val newT = when {
            deltaT > 0.0.asTime && tSnapped > tBefore -> tSnapped
            deltaT > 0.0.asTime && tSnapped <= tBefore -> tSnapped + deltaT
            deltaT < 0.0.asTime && tSnapped >= tBefore -> tSnapped + deltaT
            deltaT < 0.0.asTime && tSnapped < tBefore -> tSnapped
            else -> throw AssertionError("All cases are covered")
        }
        parentPane.markT(newT + objectView.instance.start)
        envelope.beginPointEdit(idx)
        envelope.editPoint(envelope.points[idx].copy(time = newT))
        envelope.finishEdit()
    }

    private fun showPromptFor(idx: Int) {
        val point = envelope.points[idx]
        val value = DecimalPrompt("Value for $parameterName", point.value, spec.range)
            .showDialog(pane) ?: return //TODO where to show dialog?
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