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
    val spec: NumericalControlSpec,
    @Serializable(with = ColorSerializer::class) val displayColor: Color,
    val display: Boolean
) : ParameterControl() {
    override fun clone(): ParameterControl = copy()
}

@Serializable
data class BusControl(override val parameter: String, var bus: Bus) : ParameterControl() {
    override fun clone(): ParameterControl = copy()
}

@Serializable
data class BufferControl(override val parameter: String, var buffer: Buffer) : ParameterControl() {
    override fun clone(): ParameterControl = copy()
}

@Serializable
data class CustomControl(override val parameter: String, var expr: ScExpr) : ParameterControl() {
    override fun clone(): ParameterControl = copy()
}

@Serializable
data class ConstantControl(override val parameter: String, var value: Double) : ParameterControl() {
    override fun clone(): ParameterControl = copy()
}

fun SynthDef.defaultControls() = parameters.mapTo(mutableListOf()) { p ->
    when (val spec = p.spec) {
        is BufferControlSpec -> BufferControl(p.name, spec.default)
        is BusControlSpec -> BusControl(p.name, spec.default)
        is NumericalControlSpec -> ConstantControl(p.name, spec.default)
    }
}