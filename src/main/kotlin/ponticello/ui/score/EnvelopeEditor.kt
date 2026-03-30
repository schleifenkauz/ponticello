package ponticello.ui.score

import fxutils.*
import fxutils.actions.registerShortcuts
import fxutils.drag.setupDragging
import fxutils.prompt.YesNoPrompt
import hextant.context.compoundEdit
import javafx.beans.binding.Bindings
import javafx.geometry.HorizontalDirection
import javafx.geometry.Point2D
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseButton.PRIMARY
import javafx.scene.input.MouseButton.SECONDARY
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.shape.*
import ponticello.impl.*
import ponticello.model.instr.ParameterizedObject
import ponticello.model.score.Envelope
import ponticello.model.score.Envelope.EnvelopePoint
import ponticello.model.score.ObjectPosition
import ponticello.model.score.controls.EnvelopeControl
import ponticello.model.score.controls.ParameterControlList.NamedParameterControl
import ponticello.sc.DecimalLiteral
import ponticello.sc.NumericalControlSpec
import ponticello.sc.mapOnto
import ponticello.ui.controls.ControlSpecPrompt
import ponticello.ui.controls.DecimalPrompt
import ponticello.ui.impl.Cursors
import ponticello.ui.midi.EnvelopeMidiContext
import ponticello.ui.midi.MidiContext
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
    private val curveHandles = mutableListOf<Circle>()
    private val innerCurveHandles = mutableListOf<Circle>()
    private val path = Path() styleClass "envelope-line"

    var singleEnvelopeMode = false

    init {
        mouseInfo.isVisible = false
        path.strokeProperty().bind(color.asObservableValue())
        path.strokeWidthProperty().bind(Bindings.createDoubleBinding({
            if (pane.prefWidth > WIDTH_THRESHOLD && path.isHover) 4.0
            else 2.0
        }, pane.prefWidthProperty(), path.hoverProperty()))
        mouseInfo.textFillProperty().bind(objectView.backgroundColor.map(Color::invert).asObservableValue())
        configureMouseActions()
        setupPositionInfo()
        setupLineDragging()
        envelope.addListener(this)
    }

    private fun setupPositionInfo() {
        path.setOnMouseEntered {
            if (pane.prefWidth >= WIDTH_THRESHOLD) mouseInfo.isVisible = true
        }
        path.setOnMouseExited { mouseInfo.isVisible = false }
        path.setOnMouseMoved { ev ->
            if (pane.prefWidth < WIDTH_THRESHOLD) return@setOnMouseMoved
            val t = objectView.getDuration(ev.x)
            val v = envelope.interpolateValueAt(t, spec.warp)
            displayPosition(t, v)
        }
    }

    private fun setupLineDragging() {
        var draggingSegment = false
        path.setupDragging(
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
        path.setOnMouseClicked { ev ->
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
                .showDialog(pane.scene.window, Point2D(ev.screenX, ev.screenY))
                ?.checkRange(anchor = Point2D(ev.screenX, ev.screenY)) ?: return
            envelope.beginSegmentEdit(idx - 1)
            envelope.editSegment(v)
            envelope.finishEdit()
        }
    }

    private fun Decimal.checkRange(anchor: Point2D): Decimal? {
        if (this in spec.range) return this
        val extendRange = YesNoPrompt("Extend parameter range?", default = true)
            .showDialog(pane.scene.window, anchor) ?: return null
        if (extendRange) {
            val newSpec = spec.copy(
                min = DecimalLiteral(spec.min.get().coerceAtMost(this)),
                max = DecimalLiteral(spec.max.get().coerceAtLeast(this))
            )
            namedControl.setCustomSpec(newSpec)
        }
        return takeIf { extendRange }
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
        val pathElement = getPathElement(idx, point)
        path.elements.add(idx, pathElement)
        addHandle(idx, point)
        if (idx > 0) {
            addCurveHandle(idx, point)
        }
    }

    private fun addCurveHandle(idx: Int, point: EnvelopePoint) {
        val prevPoint = envelope.points[idx - 1]
        val (x, y) = getMiddlePoint(prevPoint, point)
        val handle = Circle(x, y, CURVE_HANDLE_RADIUS)
        val innerCircle = Circle(x, y, 0.0)
        setHandleStyle(handle, innerCircle)
        setupCurveHandle(handle)
        pane.children.add(innerCircle)
        pane.children.add(handle)
        curveHandles.add(idx - 1, handle)
        innerCurveHandles.add(idx - 1, innerCircle)
    }

    private fun setupCurveHandle(handle: Circle) {
        handle.setupDragging(
            startDragEvent = MouseEvent.MOUSE_PRESSED,
            defaultCursor = Cursors.CROSS_HAIR, dragCursor = Cursors.CROSS_HAIR
        ) { ev, start, old, dx, dy ->
            val idx = curveHandles.indexOf(handle)
            val pathElement = path.elements[idx + 1] as? QuadCurveTo ?: return@setupDragging
            val x = old.centerX + dx
            val y = old.centerY + dy
            pathElement.controlX = x
            pathElement.controlY = y
            handle.centerX = x
            handle.centerY = y
        }
    }

    private fun setHandleStyle(handle: Circle, innerCircle: Circle): Circle {
        handle.visibleProperty().bind(pane.prefWidthProperty().greaterThanOrEqualTo(WIDTH_THRESHOLD))
        innerCircle.centerXProperty().bind(handle.centerXProperty())
        innerCircle.centerYProperty().bind(handle.centerYProperty())
        innerCircle.fillProperty().bind(color.asObservableValue())
        handle.fill = Color.TRANSPARENT
        handle.strokeProperty().bind(Bindings.createObjectBinding({
            if (handle.isFocused) color.now.invert() else color.now.darker()
        }, handle.focusedProperty(), color.asObservableValue()))
        handle.viewOrder = -1000.0
        return innerCircle
    }

    override fun removedPoint(idx: Int, point: EnvelopePoint) {
        val handle = handles.removeAt(idx)
        val innerCircle = innerCircles.removeAt(idx)
        val curveHandle = curveHandles.removeAt(idx)
        val innerCurveHandle = innerCurveHandles.removeAt(idx)
        pane.children.remove(handle)
        pane.children.remove(innerCircle)
        pane.children.remove(curveHandle)
        pane.children.remove(innerCurveHandle)
        path.elements.removeAt(idx)
    }

    override fun changedPoint(idx: Int, newPoint: EnvelopePoint) {
        val (px, py) = newPoint
        displayPosition(px, py)

        val tx = objectView.getWidth(px)
        val ty = yTransform.map(py.toDouble())

        handles[idx].centerX = tx
        handles[idx].centerY = ty

        if (idx != 0) {
            val prevPoint = envelope.points[idx - 1]
            val middleT = (prevPoint.time + newPoint.time) / 2
            val warp = prevPoint.curve ?: spec.warp
            val value = envelope.interpolateValueAt(middleT, warp)
            curveHandles[idx - 1].centerX = objectView.getWidth(middleT)
            curveHandles[idx - 1].centerY = yTransform.map(value.toDouble())
        }
        if (idx != envelope.points.indices.last) {
            val nextPoint = envelope.points[idx + 1]
            val middleT = (newPoint.time + nextPoint.time) / 2
            val warp = newPoint.curve ?: spec.warp
            val value = envelope.interpolateValueAt(middleT, warp)
            curveHandles[idx].centerX = objectView.getWidth(middleT)
            curveHandles[idx].centerY = yTransform.map(value.toDouble())
        }

        path.elements[idx] = getPathElement(idx, newPoint)
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
        val myNodes = mutableSetOf<Node>()
        for (nodes in listOf(handles, innerCircles, curveHandles, innerCurveHandles)) {
            myNodes.addAll(nodes)
            nodes.clear()
        }
        pane.children.removeIf { it in myNodes }
        path.elements.clear()
        if (mouseInfo !in pane.children) pane.children.add(mouseInfo)
        if (path !in pane.children) pane.children.add(path)
        for ((idx, p) in envelope.points.withIndex()) {
            val pathElement = getPathElement(idx, p)
            path.elements.add(pathElement)
            addHandle(idx, p)
            if (idx != 0) {
                addCurveHandle(idx, p)
            }
        }
    }

    private fun getPathElement(idx: Int, p: EnvelopePoint): PathElement {
        val tx = objectView.getWidth(p.time)
        val ty = yTransform.map(p.value.toDouble())
        return if (idx == 0) MoveTo(tx, ty) else {
            val prev = envelope.points[idx - 1]
            val (mx, my) = getMiddlePoint(prev, p)
            QuadCurveTo(mx, my, tx, ty)
        }
    }

    private fun getViewCoords(p: EnvelopePoint): Point {
        val tx = objectView.getWidth(p.time)
        val ty = yTransform.map(p.value.toDouble())
        return Point(tx, ty)
    }

    private fun getMiddlePoint(p1: EnvelopePoint, p2: EnvelopePoint): Point {
        val middleT = (p1.time + p2.time) / 2
        val warp = p1.curve ?: spec.warp
        val value = envelope.interpolateValueAt(middleT, warp)
        val mx = objectView.getWidth(middleT)
        val my = yTransform.map(value.toDouble())
        return Point(mx, my)
    }

    private fun removeChildren() {
        pane.children.removeAll(handles)
        pane.children.removeAll(innerCircles)
        pane.children.remove(path)
        pane.children.remove(mouseInfo)
    }

    private fun addHandle(idx: Int, p: EnvelopePoint) {
        val (x, y) = getViewCoords(p)
        val handle = Circle(x, y, HANDLE_RADIUS)
        handle.visibleProperty().bind(pane.prefWidthProperty().greaterThanOrEqualTo(WIDTH_THRESHOLD))
        val midiContext = EnvelopeMidiContext(envelope, spec, idx)
        handle.registerShortcuts(
            listOf(MidiContext.toggleActiveAction.withContext(midiContext))
        )
        val innerCircle = Circle(x, y, 0.0)
        innerCircle.radiusProperty().bind(midiContext.isActive.asObservableValue().map { midiActive ->
            if (midiActive) HANDLE_RADIUS / 2 else HANDLE_RADIUS / 3
        })
        handle.isFocusTraversable = true
        setHandleStyle(handle, innerCircle)
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
            .showDialog(pane.scene.window, anchor)
            ?.checkRange(anchor) ?: return null
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
        path.toFront()
        innerCircles.forEach { h -> h.toFront() }
        handles.forEach { h -> h.toFront() }
        curveHandles.forEach { h -> h.toFront() }
        innerCurveHandles.forEach { h -> h.toFront() }
    }

    fun dispose() {
        removeChildren()
        envelope.removeView(this)
    }

    companion object {
        private const val HANDLE_RADIUS = 8.0
        private const val CURVE_HANDLE_RADIUS = 5.0
        private const val WIDTH_THRESHOLD = 50.0
    }
}