package ponticello.ui.score

import fxutils.*
import fxutils.drag.setupDragging
import fxutils.prompt.YesNoPrompt
import hextant.context.compoundEdit
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
import ponticello.impl.*
import ponticello.model.obj.ParameterizedObject
import ponticello.model.score.Envelope
import ponticello.model.score.Envelope.EnvelopePoint
import ponticello.model.score.ObjectPosition
import ponticello.model.score.ParameterControlList.NamedParameterControl
import ponticello.model.score.controls.EnvelopeControl
import ponticello.sc.DecimalLiteral
import ponticello.sc.NumericalControlSpec
import ponticello.sc.mapOnto
import ponticello.ui.controls.ControlSpecPrompt
import ponticello.ui.controls.DecimalPrompt
import ponticello.ui.impl.Cursors
import reaktive.value.binding.map
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import kotlin.math.pow

class EnvelopeEditor(
    val namedControl: NamedParameterControl, val envelope: Envelope,
    val objectView: ScoreObjectView, val pane: Pane,
) : EnvelopeView {
    private val control get() = namedControl.now as EnvelopeControl

    val parameterName get() = namedControl.name.now

    private val color get() = control.displayColor
    private val parentPane = objectView.parentPane
    private val associatedObject get() = objectView.obj

    private val spec get() = namedControl.spec.now as NumericalControlSpec
    private val yTransform get() = spec.mapOnto(pane.height, 0.0)

    private val valueGrid get() = spec.step.get()

    private val mouseInfo = Label() styleClass "coordinate-info"
    private val handles = mutableListOf<Circle>()
    private val innerCircles = mutableListOf<Circle>()
    private val line = Polyline() styleClass "envelope-line"

    var singleEnvelopeMode = false

    init {
        mouseInfo.isVisible = false
        line.strokeProperty().bind(color.asObservableValue())
        line.strokeWidthProperty().bind(Bindings.createDoubleBinding({
            if (pane.prefWidth > WIDTH_THRESHOLD && line.isHover) 4.0
            else 2.0
        }, pane.prefWidthProperty(), line.hoverProperty()))
        mouseInfo.textFillProperty().bind(objectView.backgroundColor.map(Color::invert).asObservableValue())
        configureMouseActions()
        setupPositionInfo()
        setupLineDragging()
        envelope.addListener(this)
    }

    private fun setupPositionInfo() {
        line.setOnMouseEntered {
            if (pane.prefWidth >= WIDTH_THRESHOLD) mouseInfo.isVisible = true
        }
        line.setOnMouseExited { mouseInfo.isVisible = false }
        line.setOnMouseMoved { ev ->
            if (pane.prefWidth < WIDTH_THRESHOLD) return@setOnMouseMoved
            val t = objectView.getDuration(ev.x)
            val v = envelope.interpolateValueAt(t, spec.warp)
            displayPosition(t, v)
        }
    }

    private fun setupLineDragging() {
        var draggingSegment = false
        line.setupDragging(
            defaultCursor = Cursors.CROSS_HAIR, dragCursor = Cursors.RESIZE_VERTICAL,
            onPressed = { ev: MouseEvent ->
                if (ev.modifiers.isNotEmpty()) return@setupDragging false
                if (pane.prefWidth < WIDTH_THRESHOLD) return@setupDragging false
                val t = transformXToTime(ev.x)
                var segmentIdx = envelope.points.map(EnvelopePoint::time).binarySearch(t)
                if (segmentIdx < 0) segmentIdx = -(segmentIdx + 1)
                if (segmentIdx == 0 || segmentIdx == envelope.points.size) return@setupDragging false
                val diff = envelope.points[segmentIdx - 1].value - envelope.points[segmentIdx].value
                if (diff.abs() >= spec.step.get()) return@setupDragging false
                draggingSegment = true
                envelope.beginSegmentEdit(segmentIdx - 1)
                true
            },
            onReleased = {
                if (draggingSegment) {
                    envelope.finishEdit()
                    draggingSegment = false
                }
            }
        ) { ev, start: Point2D, _, _, dy: Double ->
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
        pane.setOnMouseClicked { ev ->
            if (singleEnvelopeMode) {
                when {
                    ev.button == SECONDARY && ev.clickCount == 1 -> createNewPoint(ev, interpolate = false)
                }
                ev.consume()
            }
        }
        line.setOnMouseClicked { ev ->
            when {
                ev.button == PRIMARY && ev.isControlDown -> {
                    val root = parentPane.root
                    if (root is RootScorePane) {
                        root.magnifyEnvelope(this)
                    }
                }

                ev.button == PRIMARY && ev.isShiftDown && associatedObject is ParameterizedObject -> {
                    val parentObject = objectView.obj as ParameterizedObject
                    ControlSpecPrompt.create(
                        parameterName,
                        parentObject,
                        spec,
                    )?.showDialog(anchorNode = pane)
                }

                ev.button == PRIMARY && ev.clickCount == 2 -> setSegmentValueFromPrompt(ev)
                ev.button == PRIMARY -> bringToFront()
                ev.button == SECONDARY -> {
                    if (pane.prefWidth >= WIDTH_THRESHOLD) {
                        createNewPoint(ev, interpolate = true)
                    }
                }

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
        val v = if (interpolate) envelope.interpolateValueAt(t, spec.warp) else transformYToValue(ev.y)
        val newPoint = EnvelopePoint(t, v)
        var idx = envelope.points.binarySearch(newPoint, compareBy(EnvelopePoint::time))
        if (idx >= 0) return //no duplicates allowed!
        idx = -(idx + 1)
        envelope.addPoint(idx, newPoint, undoable = true)
    }

    override fun addedPoint(idx: Int, point: EnvelopePoint) {
        val x = objectView.getWidth(point.time)
        val y = yTransform.map(point.value.toDouble())
        line.points.addAll(idx * 2, listOf(x, y))
        addHandle(idx, x, y)
    }

    override fun removedPoint(idx: Int, point: EnvelopePoint) {
        val handle = handles.removeAt(idx)
        val innerCircle = innerCircles.removeAt(idx)
        pane.children.remove(handle)
        pane.children.remove(innerCircle)
        line.points.remove(idx * 2, (idx + 1) * 2)
    }

    override fun changedPoint(idx: Int, newPoint: EnvelopePoint) {
        val (px, py) = newPoint
        displayPosition(px, py)

        val tx = objectView.getWidth(px)
        val ty = yTransform.map(py.toDouble())

        handles[idx].centerX = tx
        handles[idx].centerY = ty

        line.points[idx * 2] = tx
        line.points[idx * 2 + 1] = ty
    }

    private fun transformXToTime(x: Double): Decimal {
        val pos = ObjectPosition(objectView.getDuration(x), 0.0.asY) + objectView.absolutePosition
        val (t, _) = parentPane.snapToGrid(pos)
        parentPane.markT(t)
        return (t - objectView.absolutePosition.time).coerceIn(zero..associatedObject.duration)
    }

    private fun transformYToValue(y: Double): Decimal =
        yTransform.unmap(y).snap(valueGrid).coerceIn(yTransform.sourceRange)

    private fun displayPosition(t: Decimal, v: Decimal) {
        val x = objectView.getWidth(t)
        mouseInfo.text = "${parameterName}: ${v.toCanonicalString()}"
        var y = yTransform.map(v.toDouble())
        val infoHeight = mouseInfo.prefHeight(-1.0)
        val infoWidth = mouseInfo.prefWidth(-1.0)
        if (objectView.prefWidth < infoWidth) return
        if (y < infoHeight * 2) y += infoHeight / 1.5 else y -= infoHeight * 1.5
        val infoX = (x - infoWidth / 2).coerceIn(0.0, pane.prefWidth - infoWidth)
        mouseInfo.relocate(infoX, y)
    }

    override fun repaint() {
        pane.children.removeAll(handles)
        pane.children.removeAll(innerCircles)
        handles.clear()
        innerCircles.clear()
        line.points.clear()
        if (mouseInfo !in pane.children) pane.children.add(mouseInfo)
        if (line !in pane.children) pane.children.add(line)
        for ((idx, p) in envelope.points.withIndex()) {
            val x = objectView.getWidth(p.time)
            val y = yTransform.map(p.value.toDouble())
            line.points.addAll(x, y)
            addHandle(idx, x, y)
        }
    }

    private fun removeChildren() {
        pane.children.removeAll(handles)
        pane.children.removeAll(innerCircles)
        pane.children.remove(line)
        pane.children.remove(mouseInfo)
    }

    private fun addHandle(idx: Int, x: Double, y: Double) {
        val handle = Circle(x, y, HANDLE_RADIUS)
        handle.visibleProperty().bind(pane.prefWidthProperty().greaterThanOrEqualTo(WIDTH_THRESHOLD))
        val innerCircle = Circle(x, y, HANDLE_RADIUS / 3)
        innerCircle.centerXProperty().bind(handle.centerXProperty())
        innerCircle.centerYProperty().bind(handle.centerYProperty())
        handle.isFocusTraversable = true
        innerCircle.fillProperty().bind(color.asObservableValue())
        handle.fill = Color.TRANSPARENT
        handle.strokeProperty().bind(Bindings.createObjectBinding({
            val focused = handle.isFocused
            if (focused) color.now.invert() else color.now.darker()
        }, handle.focusedProperty(), color.asObservableValue()))
        handle.viewOrder = -1000.0
        setupHandle(handle)
        pane.children.add(innerCircle)
        pane.children.add(handle)
        handles.add(idx, handle)
        innerCircles.add(idx, innerCircle)
    }

    private fun setupHandle(handle: Circle) {
        var dragging = false
        handle.registerShortcuts {
            val idx = handles.indexOf(handle)
            on("V") { ev -> showPromptFor(idx) }
            on("DELETE") { removePoint(idx) }
        }
        handle.registerShortcuts(KeyEvent.KEY_PRESSED) {
            val idx = handles.indexOf(handle)
            on("LEFT") { adjustPointHorizontal(idx, HorizontalDirection.LEFT) }
            on("RIGHT") { adjustPointHorizontal(idx, HorizontalDirection.RIGHT) }
            on("UP") { adjustPointVertical(idx, +1) }
            on("DOWN") { adjustPointVertical(idx, -1) }
        }
        handle.setupDragging(
            startDragEvent = MouseEvent.MOUSE_PRESSED,
            defaultCursor = Cursors.CROSS_HAIR, dragCursor = Cursors.CROSS_HAIR,
            onPressed = { ev ->
                if (ev.modifiers == setOf(Shift) || ev.modifiers == setOf(Ctrl) || ev.modifiers == noModifiers) {
                    envelope.beginPointEdit(handles.indexOf(handle))
                    true
                } else {
                    false
                }
            },
            onReleased = { envelope.finishEdit() }
        ) { ev, _, old, dx, dy ->
            val idx = handles.indexOf(handle)
            var t = when {
                ev.isShiftDown -> envelope.points[idx].time
                idx == 0 -> 0.0.asTime
                idx == envelope.points.size - 1 -> associatedObject.duration
                else -> transformXToTime(old.minX + HANDLE_RADIUS + dx)
            }
            val y = (old.minY + HANDLE_RADIUS + dy).coerceIn(yTransform.targetRange.reverseIfEmpty())

            val v =
                if (ev.isControlDown) envelope.points[idx].value
                else transformYToValue(y)

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
                removePoint(idx)
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
        val deltaT = objectView.getDeltaT(dir)
        val tBefore = envelope.points[idx].time
        val newT = run {
            val positionInPane = objectView.instance.position.plusTime(tBefore)
            val snapped = parentPane.snapToGrid(positionInPane)
            val tSnapped = snapped.time - objectView.instance.start
            when {
                deltaT > 0.0.asTime && tSnapped > tBefore -> tSnapped
                deltaT > 0.0.asTime && tSnapped <= tBefore -> tSnapped + deltaT
                deltaT < 0.0.asTime && tSnapped >= tBefore -> tSnapped + deltaT
                deltaT < 0.0.asTime && tSnapped < tBefore -> tSnapped
                else -> throw AssertionError("All cases are covered")
            }
        }
        parentPane.markT(newT + objectView.instance.start)
        envelope.beginPointEdit(idx)
        envelope.editPoint(envelope.points[idx].copy(time = newT))
        envelope.finishEdit()
    }

    private fun showPromptFor(idx: Int) {
        val point = envelope.points[idx]
        val anchor = handles[idx].localToScreen(0.0, 0.0)
        val value = showValuePrompt(point, anchor) ?: return
        envelope.editPoint(idx, value.value.snap(valueGrid))
    }

    private fun showValuePrompt(point: EnvelopePoint, anchor: Point2D): Decimal? {
        val value = DecimalPrompt("Value for $parameterName", point.value)
            .showDialog(pane.scene.window, anchor) ?: return null
        if (value !in spec.range) {
            val adjustSpec = YesNoPrompt("The value is not in the range ${spec.range}. Adjust the spec?")
                .showDialog(pane.scene.window, anchor) ?: return null
            if (adjustSpec) {
                val min = DecimalLiteral(minOf(value, spec.range.start))
                val max = DecimalLiteral(maxOf(value, spec.range.endInclusive))
                namedControl.setCustomSpec(spec.copy(min = min, max = max))
            } else return null
        }
        return value
    }

    private fun removePoint(idx: Int) {
        when (idx) {
            0 -> {
                if (envelope.points.size >= 3) {
                    envelope.context.compoundEdit("Remove first envelope point") {
                        envelope.editPoint(0, envelope.points[1].value)
                        envelope.removePoint(1, undoable = true)
                    }
                }
            }

            envelope.points.size - 1 -> {
                if (envelope.points.size >= 3) {
                    envelope.context.compoundEdit("Remove last envelope point") {
                        envelope.editPoint(idx, envelope.points[idx - 1].value)
                        envelope.removePoint(idx - 1, undoable = true)
                    }
                }
            }

            else -> envelope.removePoint(idx)
        }
    }

    private fun bringToFront() {
        line.toFront()
        innerCircles.forEach { h -> h.toFront() }
        handles.forEach { h -> h.toFront() }
    }

    fun dispose() {
        removeChildren()
        envelope.removeView(this)
    }

    companion object {
        private const val HANDLE_RADIUS = 8.0
        private const val WIDTH_THRESHOLD = 50.0
    }
}