package xenakis.model

import hextant.context.Context
import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.sc.*

@Serializable
class ParameterDefObject(
    override val mutableName: ReactiveVariable<String>,
    val spec: ReactiveVariable<ControlSpec>
) : AbstractRenamableObject() {
    constructor(name: String, spec: ControlSpec) : this(reactiveVariable(name), reactiveVariable(spec))

    override fun canRenameTo(newName: String): Boolean = true

    fun defaultControl(context: Context) = when (val spec = spec.now) {
        is BufferControlSpec -> BufferControl(reactiveVariable(null))
        is BusControlSpec -> BusControl(reactiveVariable(context[BusRegistry].getDefault().createReference()))
        is NumericalControlSpec -> ConstantControl(reactiveVariable(spec.defaultValue.get()))
        is GroupControlSpec -> GroupControl(reactiveVariable(context[GroupRegistry].getDefault().createReference()))
    }

    override fun createReference(): Nothing = throw UnsupportedOperationException()

    override fun toString(): String = "${name.now}: ${spec.now}"

    companion object {
        private val freq = ParameterDefObject(
            "freq",
            NumericalControlSpec(440.0, 20.0, 20000.0, Warp.Exponential, 1.0, Color.BLACK)
        )
        private val amp = ParameterDefObject(
            "amp",
            NumericalControlSpec(0.1, 0.0, 1.0, Warp.Linear, 0.01, Color.ORANGE)
        )
        private val pan = ParameterDefObject(
            "pan",
            NumericalControlSpec(0.0, -1.0, 1.0, Warp.Linear, 0.1, Color.BLUE)
        )

        val defaults = listOf(freq, amp, pan)
    }
}