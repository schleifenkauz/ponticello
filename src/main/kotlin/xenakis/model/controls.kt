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
    open fun copy(): ParameterControl = this

    open fun cut(cutPos: Double, whichHalve: HorizontalDirection) = this
}

@Serializable
class KnobControl(private var value: Double) : ParameterControl() {
    @Transient
    val views = ViewManager.createWeakViewManager<KnobControlView>()

    override fun copy(): ParameterControl = KnobControl(value)

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
    val envelope: Envelope,
    @Serializable(with = ColorSerializer::class) val displayColor: Color,
    val display: Boolean
) : ParameterControl() {
    override fun copy(): ParameterControl =
        EnvelopeControl(envelope = envelope.copy(), displayColor, display)

    override fun cut(cutPos: Double, whichHalve: HorizontalDirection): ParameterControl =
        EnvelopeControl(envelope.cut(cutPos, whichHalve), displayColor, display)
}

@Serializable
class BusControl(val bus: Bus) : ParameterControl()

@Serializable
class BusValueControl(val bus: Bus) : ParameterControl()

@Serializable
data class SingleBusValueControl(val bus: Bus) : ParameterControl()

@Serializable
data class BufferControl(val buffer: Buffer) : ParameterControl()

@Serializable
data class CustomControl(val expr: ScExpr) : ParameterControl()

@Serializable
data class ConstantControl(val value: Double) : ParameterControl()

fun SynthDef.defaultControls() = parameters.associateTo(mutableMapOf()) { p -> p.name.text to p.defaultControl() }

fun ParameterDef.defaultControl() = when (val spec = spec) {
    is BufferControlSpec -> BufferControl(spec.defaultValue)
    is BusControlSpec -> BusControl(spec.defaultValue)
    is NumericalControlSpec -> ConstantControl(spec.defaultValue.get())
    is ControlSpecUnspecified -> error("no control spec available")
}