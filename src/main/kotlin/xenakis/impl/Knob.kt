package xenakis.impl

import hextant.context.Context
import hextant.fx.setRoot
import javafx.scene.control.Control
import javafx.scene.control.Label
import javafx.scene.control.Tooltip
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Pane
import javafx.scene.shape.Arc
import javafx.scene.shape.Circle
import javafx.scene.shape.Line
import xenakis.model.KnobControl
import xenakis.sc.NumericalControlSpec
import xenakis.sc.SpecTransformation
import xenakis.ui.*
import kotlin.math.*

class Knob(
    val parameter: String,
    val control: KnobControl,
    private val spec: NumericalControlSpec,
    private val radius: Double = DEFAULT_RADIUS,
    private val context: Context
) : Control(), KnobControlView {
    private val knobDots = mutableListOf<Circle>()
    private val knob = Circle(radius, radius, radius - 10) styleClass "knob-mass"
    private val indicator = Line(radius, radius, 0.0, 0.0) styleClass "knob-indicator"
    private val valueLabel = Label() styleClass "knob-value"
    @Suppress("EmptyRange")
    private val transform = SpecTransformation(spec, MIN_ANGLE..MAX_ANGLE)
    private val root = Pane(knob, indicator, valueLabel)

    private val discreteValues = ((spec.max.get() - spec.min.get()) / spec.step.get()).roundToInt()

    init {
        styleClass("control-knob")
        setRoot(root)
        isFocusTraversable = true
        setPrefSize(radius * 2, radius * 2)
        setMinSize(radius * 2, radius * 2)
        control.addView(this)
        indicator.stroke = spec.associatedColor
        valueLabel.centerHorizontally(this)
        valueLabel.layoutY = radius * 1.4
        addDotsOrArc()
        listenForMouseEvents()
        addShortcuts()
    }

    private fun listenForMouseEvents() {
        addEventHandler(MouseEvent.MOUSE_PRESSED) { ev ->
            val x = ev.x - radius
            val y = ev.y - radius
            if (sqrt(x * x + y * y) < radius / 2) return@addEventHandler
            setValueFromMouse(x, y)
            ev.consume()
        }
        addEventHandler(MouseEvent.MOUSE_DRAGGED) { ev ->
            setValueFromMouse((ev.x - radius), (ev.y - radius))
            ev.consume()
        }
        addEventHandler(MouseEvent.MOUSE_CLICKED) { ev ->
            if (ev.clickCount >= 2) {
                showValueInput()
            } else {
                requestFocus()
            }
            ev.consume()
        }
        tooltip = Tooltip(parameter)
    }

    private fun showValueInput() {
        val range = spec.min.get()..spec.max.get()
        showNumberPrompt("$parameter ($range)", range, control.get(), context) { v ->
            control.set(v)
        }
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
        val newValue = control.get() + spec.step.get()
        control.set(newValue.coerceIn(spec.range))
    }

    private fun decrease() {
        val newValue = (control.get() - spec.step.get())
        control.set(newValue.coerceIn(spec.range))
    }

    private fun setValueFromMouse(x: Double, y: Double) {
        var phi = atan(x / y)
        if (y < 0.0) phi += PI
        phi += PI / 2
        if (phi < 1.0 / 3 * PI) phi += 2 * PI
        phi = phi.coerceIn(MAX_ANGLE..MIN_ANGLE)
        val value = transform.unmap(phi).snap(spec.step.get())
        control.set(value)
    }

    private fun getPoint(value: Double, r: Double): Point {
        val phi = transform.map(value) - PI / 2
        val p = Point(radius + r * sin(phi), radius + r * cos(phi))
        return p
    }

    private fun addDotsOrArc() {
        if (discreteValues <= MAX_DOTS) {
            for (i in 0..discreteValues) {
                val v = (spec.min.get() + i * spec.step.get()).round(spec.accuracy)
                val dot = Circle(DOT_RADIUS) styleClass "knob-dot"
                val p = getPoint(v, radius - 5)
                dot.centerX = p.x
                dot.centerY = p.y
                knobDots.add(dot)
            }
            children.addAll(knobDots)
        } else {
            val arc = Arc(radius, radius, radius - 5, radius - 5, 210.0, -240.0) styleClass "knob-arc"
            children.add(arc)
        }
    }

    override fun updatedValue(control: KnobControl, value: Double) {
        valueLabel.text = value.format(spec.accuracy)
        val start = getPoint(value, radius / 3)
        val end = getPoint(value, radius - 10.0)
        indicator.startX = start.x
        indicator.startY = start.y
        indicator.endX = end.x
        indicator.endY = end.y
    }

    companion object {
        private const val DEFAULT_RADIUS = 24.0
        private const val MIN_ANGLE = 6.5 / 3 * PI
        private const val MAX_ANGLE = 2.5 / 3 * PI
        private const val DOT_RADIUS = 3.0
        private const val MAX_DOTS = 20
    }
}