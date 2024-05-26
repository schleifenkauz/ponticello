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

    abstract fun clone(): ParameterControl

    open fun cut(cutPos: Double, whichHalve: HorizontalDirection) = this
}

@Serializable
data class KnobControl(override val parameter: String, private var value: Double) : ParameterControl() {
    @Transient
    val views = ViewManager.createWeakViewManager<KnobControlView>()

    override fun clone(): ParameterControl = copy()

    fun set(value: Double) {
        if (value == this.value) return
        this.value = value
        views.notifyViews { updatedValue(value) }
    }

    fun get() = value
}

@Serializable
data class EnvelopeControl(
    override val parameter: String,
    val envelope: Envelope,
    @Serializable(with = ColorSerializer::class) val displayColor: Color,
    val display: Boolean
) : ParameterControl() {
    override fun clone(): ParameterControl = copy(envelope = envelope.clone())

    override fun cut(cutPos: Double, whichHalve: HorizontalDirection): ParameterControl =
        EnvelopeControl(parameter, envelope.cut(cutPos, whichHalve), displayColor, display)
}

@Serializable
data class BusControl(override val parameter: String, val bus: Bus) : ParameterControl() {
    override fun clone(): ParameterControl = copy()
}

@Serializable
data class BusValueControl(override val parameter: String, val bus: Bus) : ParameterControl() {
    override fun clone(): ParameterControl = copy()
}

@Serializable
data class SingleBusValueControl(override val parameter: String, val bus: Bus) : ParameterControl() {
    override fun clone(): ParameterControl = copy()
}

@Serializable
data class BufferControl(override val parameter: String, val buffer: Buffer) : ParameterControl() {
    override fun clone(): ParameterControl = copy()
}

@Serializable
data class CustomControl(override val parameter: String, val expr: ScExpr) : ParameterControl() {
    override fun clone(): ParameterControl = copy()
}

@Serializable
data class ConstantControl(override val parameter: String, val value: Double) : ParameterControl() {
    override fun clone(): ParameterControl = copy()
}

fun SynthDef.defaultControls() = parameters.map { p ->
    when (val spec = p.spec) {
        is BufferControlSpec -> BufferControl(p.name.text, spec.defaultValue)
        is BusControlSpec -> BusControl(p.name.text, spec.defaultValue)
        is NumericalControlSpec -> ConstantControl(p.name.text, spec.defaultValue.value)
        is ControlSpecUnspecified -> error("no control spec available")
    }
}