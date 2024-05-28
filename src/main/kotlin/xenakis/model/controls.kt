package xenakis.model

import hextant.core.editor.ViewManager
import javafx.geometry.HorizontalDirection
import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import xenakis.impl.ColorSerializer
import xenakis.sc.*
import xenakis.ui.KnobControlView

@Serializable
sealed class ParameterControl {
    abstract val parameter: String

    open fun copy(): ParameterControl = this

    open fun cut(cutPos: Double, whichHalve: HorizontalDirection) = this
}

@Serializable
class KnobControl(override val parameter: String, private var value: Double) : ParameterControl() {
    @Transient
    val views = ViewManager.createWeakViewManager<KnobControlView>()

    override fun copy(): ParameterControl = KnobControl(parameter, value)

    fun set(value: Double) {
        if (value == this.value) return
        this.value = value
        views.notifyViews { updatedValue(this@KnobControl, value) }
    }

    fun get() = value

    fun addView(view: KnobControlView) {
        views.addView(view)
        view.updatedValue(this, value)
    }
}

@Serializable
class EnvelopeControl(
    override val parameter: String,
    val envelope: Envelope,
    @Serializable(with = ColorSerializer::class) val displayColor: Color,
    val display: Boolean
) : ParameterControl() {
    override fun copy(): ParameterControl =
        EnvelopeControl(parameter, envelope = envelope.copy(), displayColor, display)

    override fun cut(cutPos: Double, whichHalve: HorizontalDirection): ParameterControl =
        EnvelopeControl(parameter, envelope.cut(cutPos, whichHalve), displayColor, display)
}

@Serializable
class BusControl(override val parameter: String, val bus: Bus) : ParameterControl()

@Serializable
class BusValueControl(override val parameter: String, val bus: Bus) : ParameterControl()

@Serializable
data class SingleBusValueControl(override val parameter: String, val bus: Bus) : ParameterControl()

@Serializable
data class BufferControl(override val parameter: String, val buffer: Buffer) : ParameterControl()

@Serializable
data class CustomControl(override val parameter: String, val expr: ScExpr) : ParameterControl()

@Serializable
data class ConstantControl(override val parameter: String, val value: Double) : ParameterControl()

fun SynthDef.defaultControls() = parameters.map { p ->
    when (val spec = p.spec) {
        is BufferControlSpec -> BufferControl(p.name.text, spec.defaultValue)
        is BusControlSpec -> BusControl(p.name.text, spec.defaultValue)
        is NumericalControlSpec -> ConstantControl(p.name.text, spec.defaultValue.value)
        is ControlSpecUnspecified -> error("no control spec available")
    }
}