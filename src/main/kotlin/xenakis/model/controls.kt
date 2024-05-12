package xenakis.model

import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import xenakis.impl.ColorSerializer
import xenakis.sc.*

@Serializable
sealed class ParameterControl {
    abstract val parameter: String

    abstract fun clone(): ParameterControl
}

@Serializable
data class KnobControl(override val parameter: String, var value: Double) : ParameterControl() {
    override fun clone(): ParameterControl = copy()
}

@Serializable
data class EnvelopeControl(
    override val parameter: String,
    val envelope: Envelope,
    @Serializable(with = ColorSerializer::class) val displayColor: Color,
    val display: Boolean
) : ParameterControl() {
    override fun clone(): ParameterControl = copy()
}

@Serializable
data class BusControl(override val parameter: String, val bus: Bus) : ParameterControl() {
    override fun clone(): ParameterControl = copy()
}

@Serializable
data class BusValueControl(override val parameter: String, val bus: Bus): ParameterControl() {
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

fun SynthDef.defaultControls() = parameters.mapTo(mutableListOf()) { p ->
    when (val spec = p.spec) {
        is BufferControlSpec -> BufferControl(p.name.text, spec.defaultValue)
        is BusControlSpec -> BusControl(p.name.text, spec.defaultValue)
        is NumericalControlSpec -> ConstantControl(p.name.text, spec.defaultValue.value)
        is ControlSpecUnspecified -> error("no control spec available")
    }
}