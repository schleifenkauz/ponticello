package xenakis.impl

import javafx.scene.layout.StackPane
import javafx.scene.shape.Circle
import javafx.scene.shape.Line
import xenakis.model.KnobControl
import xenakis.sc.NumericalControlSpec
import xenakis.ui.styleClass

class Knob(private val control: KnobControl, private val spec: NumericalControlSpec) : StackPane() {
    private val knobDots = Circle(RADIUS, RADIUS, RADIUS) styleClass "knob-dots"
    private val knob = Circle(RADIUS, RADIUS, RADIUS - 5) styleClass "knob-mass"
    private val indicator = Line(RADIUS, RADIUS, RADIUS, 0.0) styleClass "knob-indicator"

    init {
        control.views.addView { v -> updatedValue(v) }
        knobDots.strokeDashArray.addAll(2.0, 5.0)
        children.addAll(knobDots, knob, indicator)
    }

    fun updatedValue(v: Double) {

    }

    companion object {
        private const val RADIUS = 32.0
    }
}