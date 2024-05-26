package xenakis.impl

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

class Knob(private val control: KnobControl, private val spec: NumericalControlSpec) : Control(), KnobControlView {
    private val knobDots = mutableListOf<Circle>()
    private val knob = Circle(RADIUS, RADIUS, RADIUS - 10) styleClass "knob-mass"
    private val indicator = Line(RADIUS, RADIUS, 0.0, 0.0) styleClass "knob-indicator"
    private val accuracy = accuracy(spec.step.value)
    private val valueLabel = Label() styleClass "knob-value"
    private val transform = SpecTransformation(spec, MIN_ANGLE..MAX_ANGLE)
    private val root = Pane(knob, indicator, valueLabel)

    private val discreteValues = ((spec.max.value - spec.min.value) / spec.step.value).roundToInt()

    init {
        styleClass("control-knob")
        setRoot(root)
        isFocusTraversable = true
        setPrefSize(RADIUS * 2, RADIUS * 2)
        setMinSize(RADIUS * 2, RADIUS * 2)
        connectToControl()
        indicator.stroke = spec.associatedColor
        valueLabel.centerHorizontally(this)
        valueLabel.layoutY = RADIUS * 1.4
        addDotsOrArc()
        listenForMouseEvents()
        addShortcuts()
    }

    private fun listenForMouseEvents() {
        addEventHandler(MouseEvent.MOUSE_PRESSED) { ev ->
            val x = ev.x - RADIUS
            val y = ev.y - RADIUS
            if (sqrt(x * x + y * y) < RADIUS / 2) return@addEventHandler
            setValueFromMouse(x, y)
            ev.consume()
        }
        addEventHandler(MouseEvent.MOUSE_DRAGGED) { ev ->
            setValueFromMouse((ev.x - RADIUS), (ev.y - RADIUS))
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
        tooltip = Tooltip(control.parameter)
    }

    private fun showValueInput() {
        val range = spec.min.value..spec.max.value
        val v = showDoubleInputDialog(control.parameter, range, control.get()) ?: return
        control.set(v)
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
        val newValue = control.get() + spec.step.value
        control.set(newValue.coerceIn(spec.range))
    }

    private fun decrease() {
        val newValue = (control.get() - spec.step.value)
        control.set(newValue.coerceIn(spec.range))
    }

    private fun setValueFromMouse(x: Double, y: Double) {
        var phi = atan(x / y)
        if (y < 0.0) phi += PI
        phi += PI / 2
        if (phi < 1.0 / 3 * PI) phi += 2 * PI
        phi = phi.coerceIn(MAX_ANGLE..MIN_ANGLE)
        val value = transform.unmap(phi).snap(spec.step.value)
        control.set(value)
    }

    private fun getPoint(value: Double, r: Double): Point {
        val phi = transform.map(value) - PI / 2
        val p = Point(RADIUS + r * sin(phi), RADIUS + r * cos(phi))
        return p
    }

    private fun connectToControl() {
        control.views.addView(this)
        updatedValue(control.get())
    }

    private fun layoutLabels() {
        valueLabel.centerHorizontally(this)
        valueLabel.layoutY = RADIUS * 1.5
    }

    private fun addDotsOrArc() {
        if (discreteValues <= MAX_DOTS) {
            for (i in 0..discreteValues) {
                val v = (spec.min.value + i * spec.step.value).round(accuracy)
                val dot = Circle(DOT_RADIUS) styleClass "knob-dot"
                val p = getPoint(v, RADIUS - 5)
                dot.centerX = p.x
                dot.centerY = p.y
                knobDots.add(dot)
            }
            children.addAll(knobDots)
        } else {
            val arc = Arc(RADIUS, RADIUS, RADIUS - 5, RADIUS - 5, 210.0, -240.0) styleClass "knob-arc"
            children.add(arc)
        }
    }

    override fun updatedValue(value: Double) {
        valueLabel.text = value.format(accuracy)
        val start = getPoint(value, RADIUS / 3)
        val end = getPoint(value, RADIUS - 10.0)
        indicator.startX = start.x
        indicator.startY = start.y
        indicator.endX = end.x
        indicator.endY = end.y
    }

    companion object {
        private const val RADIUS = 24.0
        private const val MIN_ANGLE = 6.5 / 3 * PI
        private const val MAX_ANGLE = 2.5 / 3 * PI
        private const val DOT_RADIUS = 3.0
        private const val MAX_DOTS = 20
    }
}