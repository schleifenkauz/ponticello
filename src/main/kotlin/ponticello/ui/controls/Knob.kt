package ponticello.ui.controls

import fxutils.centerHorizontally
import fxutils.setRoot
import fxutils.styleClass
import fxutils.undo.UndoManager
import fxutils.undo.VariableEdit
import javafx.geometry.Point2D
import javafx.scene.control.Control
import javafx.scene.control.Label
import javafx.scene.control.Tooltip
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.shape.Arc
import javafx.scene.shape.Circle
import javafx.scene.shape.Line
import ponticello.impl.*
import ponticello.sc.NumericalControlSpec
import ponticello.sc.SpecTransformation
import ponticello.sc.mapOnto
import reaktive.Observer
import reaktive.value.ReactiveVariable
import reaktive.value.forEach
import reaktive.value.now
import kotlin.math.*

class Knob(
    val parameter: String,
    private val variable: ReactiveVariable<Decimal>,
    private val spec: NumericalControlSpec,
    private val radius: Double = DEFAULT_RADIUS,
    private val color: Color = Color.BLACK,
    private val inputMethod: InputMethod = InputMethod.Vertical,
    private val showRange: Boolean = true,
    private val undoManager: UndoManager? = null,
) : Control() {
    private val knobDots = mutableListOf<Circle>()
    private val knob = Circle(radius, radius, radius - 10, color) styleClass "knob-mass"
    private val indicator = Line(radius, radius, 0.0, 0.0) styleClass "knob-indicator"
    private val valueLabel = Label() styleClass "knob-value"

    @Suppress("EmptyRange")
    private val transform = SpecTransformation(spec, MIN_ANGLE..MAX_ANGLE)
    private val root = Pane(knob, indicator, valueLabel)

    private val discreteValues = ((spec.max.get() - spec.min.get()) / spec.step.get()).roundToInt()

    private val valueObserver: Observer

    init {
        styleClass("control-knob")
        setRoot(root)
        isFocusTraversable = true
        setPrefSize(radius * 2, radius * 2)
        setMinSize(radius * 2, radius * 2)
        valueObserver = variable.forEach { value -> updatedValue(value) }
        indicator.stroke = spec.associatedColor
        valueLabel.centerHorizontally(this)
        valueLabel.layoutY = radius * 1.4
        addDotsOrArc()
        listenForMouseEvents()
        addShortcuts()
    }

    private fun listenForMouseEvents() {
        var valueBefore = Decimal.NaN
        val transformation = spec.mapOnto(DRAG_RANGE, -DRAG_RANGE)
        var dragStart = Point2D.ZERO
        addEventHandler(MouseEvent.ANY) { ev ->
            when (ev.eventType) {
                MouseEvent.DRAG_DETECTED -> {
                    if (inputMethod == InputMethod.Vertical || inputMethod == InputMethod.Horizontal) {
                        valueBefore = variable.now
                        dragStart = Point2D(ev.screenX, ev.screenY)
                        startFullDrag()
                    }
                    ev.consume()
                }

                MouseEvent.MOUSE_PRESSED -> {
                    if (inputMethod == InputMethod.Angle) {
                        val x = ev.x - radius
                        val y = ev.y - radius
                        if (sqrt(x * x + y * y) < radius / 2) return@addEventHandler
                        setValueFromMouse(x, y)
                    }
                }

                MouseEvent.MOUSE_DRAGGED -> {
                    when (inputMethod) {
                        InputMethod.Angle -> {
                            setValueFromMouse((ev.x - radius), (ev.y - radius))
                        }

                        InputMethod.Horizontal -> {
                            if (dragStart != Point2D.ZERO) {
                                val deltaX = ev.screenX - dragStart.x
                                updateValueByPosDelta(transformation, deltaX, valueBefore)
                            }
                        }

                        InputMethod.Vertical -> {
                            if (dragStart != Point2D.ZERO) {
                                val deltaY = ev.screenY - dragStart.y
                                updateValueByPosDelta(transformation, -deltaY, valueBefore)
                            }
                        }
                    }
                }

                MouseEvent.MOUSE_RELEASED -> {
                    if (valueBefore.isNaN()) return@addEventHandler
                    val value = variable.now
                    if (undoManager != null && value != valueBefore) {
                        val actionDescription = "Update $parameter"
                        undoManager.record(VariableEdit(variable, valueBefore, actionDescription))
                    }
                    dragStart = Point2D.ZERO
                    valueBefore = Decimal.NaN
                }

                MouseEvent.MOUSE_CLICKED -> {
                    if (ev.clickCount >= 2) {
                        showValueInput()
                    } else {
                        requestFocus()
                    }
                }
            }
            ev.consume()
        }
        tooltip = Tooltip(parameter)
    }

    private fun updateValueByPosDelta(transformation: SpecTransformation, delta: Double, valueBefore: Decimal) {
        val value = transformation.unmap(transformation.map(valueBefore.value) - delta)
            .coerceIn(spec.min.get().value, spec.max.get().value)
        variable.set(Decimal(value, precision = spec.precision))
    }

    private fun showValueInput() {
        val range = spec.min.get()..spec.max.get()
        val v = DecimalPrompt("$parameter ($range)", variable.get(), range).showDialog(anchorNode = this) ?: return
        variable.set(v.withPrecision(spec.precision))
    }

    private fun addShortcuts() = addEventHandler(KeyEvent.KEY_PRESSED) { ev ->
        if (ev.isShiftDown || ev.isAltDown || ev.isControlDown) return@addEventHandler
        ev.consume()
        when (ev.code.toString()) {
            "UP", "PLUS" -> increase()
            "DOWN", "MINUS" -> decrease()
        }
    }

    private fun increase() {
        val newValue = variable.get() + spec.step.get()
        variable.set(newValue.coerceIn(spec.range))
    }

    private fun decrease() {
        val newValue = (variable.get() - spec.step.get())
        variable.set(newValue.coerceIn(spec.range))
    }

    private fun setValueFromMouse(x: Double, y: Double) {
        var phi = atan(x / y)
        if (y < 0.0) phi += PI
        phi += PI / 2
        if (phi < 1.0 / 3 * PI) phi += 2 * PI
        phi = phi.coerceIn(MAX_ANGLE..MIN_ANGLE)
        val value = transform.unmap(phi).snap(spec.step.get())
        variable.set(value)
    }

    private fun getPoint(value: Decimal, r: Double): Point2D {
        val phi = transform.map(value.toDouble()) - PI / 2
        val p = Point2D(radius + r * sin(phi), radius + r * cos(phi))
        return p
    }

    private fun addDotsOrArc() {
        if (!showRange) return
        if (discreteValues <= MAX_DOTS) {
            for (i in 0..discreteValues) {
                val v = (spec.min.get() + i * spec.step.get()).withPrecision(spec.precision)
                val dot = Circle(DOT_RADIUS, color) styleClass "knob-dot"
                val p = getPoint(v, radius - 5)
                dot.centerX = p.x
                dot.centerY = p.y
                knobDots.add(dot)
            }
            children.addAll(knobDots)
        } else {
            val arc = Arc(radius, radius, radius - 5, radius - 5, 210.0, -240.0) styleClass "knob-arc"
            arc.stroke = color
            children.add(arc)
        }
    }

    private fun updatedValue(value: Decimal) {
        valueLabel.text = value.toString()
        val start = getPoint(value, radius / 3)
        val end = getPoint(value, radius - 10.0)
        indicator.startX = start.x
        indicator.startY = start.y
        indicator.endX = end.x
        indicator.endY = end.y
    }

    enum class InputMethod {
        Angle, Horizontal, Vertical;
    }

    companion object {
        private const val DEFAULT_RADIUS = 24.0
        private const val MIN_ANGLE = 6.5 / 3 * PI
        private const val MAX_ANGLE = 2.5 / 3 * PI
        private const val DOT_RADIUS = 3.0
        private const val MAX_DOTS = 20
        private const val DRAG_RANGE = 200.0
    }
}