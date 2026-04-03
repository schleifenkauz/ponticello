package ponticello.ui.controls

import fxutils.centerHorizontally
import fxutils.setRoot
import fxutils.styleClass
import fxutils.undo.UndoManager
import fxutils.undo.VariableEdit
import javafx.geometry.Point2D
import javafx.scene.Cursor
import javafx.scene.control.Control
import javafx.scene.control.Label
import javafx.scene.control.Tooltip
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.robot.Robot
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
    private val undoManager: UndoManager? = null,
) : Control() {
    private val knobDots = mutableListOf<Circle>()
    private val valueArc = Arc(radius, radius, radius - 5.0, radius - 5.0, 210.0, 0.0) styleClass "knob-arc"
    private val indicator = Line(radius, radius, 0.0, 0.0) styleClass "knob-indicator"
    private val valueLabel = Label() styleClass "knob-value"

    @Suppress("EmptyRange")
    private val angleTransform = SpecTransformation(spec, MIN_ANGLE..MAX_ANGLE)

    private val transform = when (inputMethod) {
        InputMethod.Angle -> angleTransform
        InputMethod.Horizontal, InputMethod.Vertical -> SpecTransformation(spec, -DRAG_RANGE..DRAG_RANGE)
    }
    private val root = Pane(indicator, valueLabel)

    private val nDiscreteValues = ((spec.max.get() - spec.min.get()) / spec.step.get()).roundToInt()

    private val valueObserver: Observer

    init {
        styleClass("control-knob")
        setRoot(root)
        isFocusTraversable = true
        setPrefSize(radius * 2, radius * 2)
        setMinSize(radius * 2, radius * 2)
        valueObserver = variable.forEach { value -> updatedValue(value) }
        valueLabel.centerHorizontally(this)
        valueLabel.layoutY = radius * 1.3
        tooltip = Tooltip(parameter)
        addDotsOrArc()
        listenForMouseEvents()
        addShortcuts()
    }

    private fun listenForMouseEvents() {
        var valueBefore = Decimal.NaN
        var dragStart = Point2D.ZERO
        addEventHandler(MouseEvent.ANY) { ev ->
            when (ev.eventType) {
                MouseEvent.DRAG_DETECTED -> {
                    if (inputMethod == InputMethod.Vertical || inputMethod == InputMethod.Horizontal) {
                        valueBefore = variable.now
                        dragStart = Point2D(ev.screenX, ev.screenY)
                        cursor = Cursor.NONE
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
                                updateValueByPosDelta(deltaX, valueBefore)
                            }
                        }

                        InputMethod.Vertical -> {
                            if (dragStart != Point2D.ZERO) {
                                val deltaY = ev.screenY - dragStart.y
                                updateValueByPosDelta(-deltaY, valueBefore)
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
                    cursor = Cursor.DEFAULT
                    robot.mouseMove(dragStart.x, dragStart.y)
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

    private fun updateValueByPosDelta(delta: Double, valueBefore: Decimal) {
        val value = transform.unmap(transform.map(valueBefore.value) + delta)
            .coerceIn(spec.min.get().value, spec.max.get().value).snap(spec.step.get())
        variable.set(value)
    }

    private fun showValueInput() {
        val range = spec.min.get()..spec.max.get()
        val v = DecimalPrompt("$parameter ($range)", variable.get(), spec.precision, range)
            .showDialog(anchorNode = this) ?: return
        variable.set(v.withPrecision(spec.precision))
    }

    private fun addShortcuts() = addEventHandler(KeyEvent.ANY) { ev ->
        if (ev.isShiftDown || ev.isAltDown || ev.isControlDown) return@addEventHandler
        ev.consume()
        val increaseKey = when (inputMethod) {
            InputMethod.Vertical, InputMethod.Angle -> "UP"
            InputMethod.Horizontal -> "RIGHT"
        }
        val decreaseKey = when (inputMethod) {
            InputMethod.Vertical, InputMethod.Angle -> "DOWN"
            InputMethod.Horizontal -> "LEFT"
        }
        when (ev.code.toString()) {
            increaseKey, "PLUS" -> {
                if (ev.eventType == KeyEvent.KEY_PRESSED) {
                    increase()
                }
                ev.consume()
            }

            decreaseKey, "MINUS" -> {
                if (ev.eventType == KeyEvent.KEY_PRESSED) {
                    decrease()
                }
                ev.consume()
            }
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
        val phi = angleTransform.map(value.toDouble()) - PI / 2
        val p = Point2D(radius + r * sin(phi), radius + r * cos(phi))
        return p
    }

    private fun addDotsOrArc() {
        if (nDiscreteValues <= MAX_DOTS) {
            for (i in 0..nDiscreteValues) {
                val v = getDiscreteValue(i)
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
            valueArc.stroke = spec.associatedColor
            children.add(valueArc)
        }
    }

    private fun getDiscreteValue(step: Int) = (spec.min.get() + step * spec.step.get()).withPrecision(spec.precision)

    private fun updatedValue(value: Decimal) {
        valueLabel.text = value.toString()
        val p = getPoint(value, radius - 5.0)
        indicator.endX = p.x
        indicator.endY = p.y

        if (nDiscreteValues <= MAX_DOTS) {
            for (i in 0..nDiscreteValues) {
                val v = getDiscreteValue(i)
                val dot = knobDots[i]
                if (v <= value) {
                    dot.fill = spec.associatedColor
                } else break
            }
        } else {
            valueArc.length = spec.mapOnto(0.0, -240.0).map(value.value)
        }
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

        private val robot = Robot()
    }
}