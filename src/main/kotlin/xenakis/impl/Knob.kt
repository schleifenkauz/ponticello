package xenakis.impl

import hextant.fx.registerShortcuts
import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Pane
import javafx.scene.shape.Arc
import javafx.scene.shape.Circle
import javafx.scene.shape.Line
import javafx.stage.Stage
import xenakis.model.KnobControl
import xenakis.sc.NumericalControlSpec
import xenakis.sc.SpecTransformation
import xenakis.sc.Warp
import xenakis.ui.*
import kotlin.math.*

class Knob(private val control: KnobControl, private val spec: NumericalControlSpec) : Pane(), KnobControlView {
    private val knobDots = mutableListOf<Circle>()
    private val knob = Circle(RADIUS, RADIUS, RADIUS - 5) styleClass "knob-mass"
    private val indicator = Line(RADIUS, RADIUS, 0.0, 0.0) styleClass "knob-indicator"
    private val accuracy = accuracy(spec.step.value)
    private val valueLabel = Label() styleClass "knob-value"
    private val nameLabel = Label(control.parameter) styleClass "knob-parameter-name"
    private val transform = SpecTransformation(spec, MIN_ANGLE..MAX_ANGLE)

    private val discreteValues = ((spec.max.value - spec.min.value) / spec.step.value).roundToInt()

    init {
        styleClass("control-kob")
        isFocusTraversable = true
        setPrefSize(RADIUS * 2, RADIUS * 2)
        setMinSize(RADIUS * 2, RADIUS * 2)
        connectToControl()
        children.addAll(knob, indicator, valueLabel, nameLabel)
        layoutLabels()
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
            if (ev.clickCount > 2) {
                control.set(spec.defaultValue.value)
            } else {
                requestFocus()
            }
            ev.consume()
        }
    }

    private fun addShortcuts() = registerShortcuts {
        on("UP") { increase() }
        on("DOWN") { decrease() }
        on("PLUS") { increase() }
        on("MINUS") { decrease() }
    }

    private fun increase() {
        control.set(control.get() + spec.step.value)
    }

    private fun decrease() {
        control.set(control.get() - spec.step.value)
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
        nameLabel.centerHorizontally(this)
        valueLabel.layoutY = RADIUS * 1.3
        nameLabel.layoutY = RADIUS * 1.7
    }

    private fun addDotsOrArc() {
        if (discreteValues <= MAX_DOTS) {
            for (i in 0..discreteValues) {
                val v = (spec.min.value + i * spec.step.value).round(accuracy)
                val dot = Circle(DOT_RADIUS) styleClass "knob-dot"
                val p = getPoint(v, RADIUS)
                dot.centerX = p.x
                dot.centerY = p.y
                knobDots.add(dot)
            }
            children.addAll(knobDots)
        } else {
            val arc = Arc(RADIUS, RADIUS, RADIUS, RADIUS, 210.0, -240.0) styleClass "knob-arc"
            children.add(arc)
        }
    }

    override fun updatedValue(value: Double) {
        valueLabel.text = value.format(accuracy)
        val start = getPoint(value, RADIUS / 3)
        val end = getPoint(value, RADIUS - 5.0)
        indicator.startX = start.x
        indicator.startY = start.y
        indicator.endX = end.x
        indicator.endY = end.y
    }

    companion object {
        private const val RADIUS = 32.0
        private const val MIN_ANGLE = 6.5 / 3 * PI
        private const val MAX_ANGLE = 2.5 / 3 * PI
        private const val DOT_RADIUS = 3.0
        private const val MAX_DOTS = 20
    }
}

class KnobTest : Application() {
    override fun start(stage: Stage) {
        val ctrl = KnobControl("x", 0.1)
        val spec = NumericalControlSpec(0.1, 0.0, 1.0, Warp.Linear, 0.01)
        val knob = Knob(ctrl, spec)
        val container = Pane(knob)
        container.setPrefSize(500.0, 500.0)
        knob.relocate(250 - 32.0, 250 - 32.0)
        stage.scene = Scene(container)
        stage.scene.stylesheets.add("xenakis/ui/style.css")
        stage.show()
    }
}