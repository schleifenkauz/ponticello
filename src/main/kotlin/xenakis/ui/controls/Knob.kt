package xenakis.ui.controls

import hextant.context.Context
import hextant.fx.setRoot
import javafx.geometry.Point2D
import javafx.scene.control.Control
import javafx.scene.control.Label
import javafx.scene.control.Tooltip
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Pane
import javafx.scene.shape.Arc
import javafx.scene.shape.Circle
import javafx.scene.shape.Line
import reaktive.Observer
import reaktive.value.forEach
import xenakis.impl.*
import xenakis.model.score.KnobControl
import xenakis.sc.NumericalControlSpec
import xenakis.sc.SpecTransformation
import xenakis.ui.impl.centerHorizontally
import xenakis.ui.impl.styleClass
import xenakis.ui.prompt.DecimalPrompt
import kotlin.math.*

class Knob(
    val parameter: String,
    val control: KnobControl,
    private val spec: NumericalControlSpec,
    private val context: Context,
    private val radius: Double = DEFAULT_RADIUS
) : Control() {
    private val knobDots = mutableListOf<Circle>()
    private val knob = Circle(radius, radius, radius - 10) styleClass "knob-mass"
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
        valueObserver = control.value.forEach { value -> updatedValue(value) }
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
        val v = DecimalPrompt("$parameter ($range)", control.get(), range).showDialog(context, this) ?: return
        control.value.set(v.withPrecision(spec.precision))
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
        control.value.set(newValue.coerceIn(spec.range))
    }

    private fun decrease() {
        val newValue = (control.get() - spec.step.get())
        control.value.set(newValue.coerceIn(spec.range))
    }

    private fun setValueFromMouse(x: Double, y: Double) {
        var phi = atan(x / y)
        if (y < 0.0) phi += PI
        phi += PI / 2
        if (phi < 1.0 / 3 * PI) phi += 2 * PI
        phi = phi.coerceIn(MAX_ANGLE..MIN_ANGLE)
        val value = transform.unmap(phi).snap(spec.step.get())
        control.value.set(value)
    }

    private fun getPoint(value: Decimal, r: Double): Point2D {
        val phi = transform.map(value.toDouble()) - PI / 2
        val p = Point2D(radius + r * sin(phi), radius + r * cos(phi))
        return p
    }

    private fun addDotsOrArc() {
        if (discreteValues <= MAX_DOTS) {
            for (i in 0..discreteValues) {
                val v = (spec.min.get() + i * spec.step.get()).withPrecision(spec.precision)
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

    private fun updatedValue(value: Decimal) {
        valueLabel.text = value.toString()
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