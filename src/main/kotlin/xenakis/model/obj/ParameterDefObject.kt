package xenakis.model.obj

import hextant.context.Context
import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.toDecimal
import xenakis.model.registry.ObjectReference
import xenakis.sc.ControlSpec
import xenakis.sc.NumericalControlSpec
import xenakis.sc.Warp
import xenakis.sc.defaultControl

@Serializable
class ParameterDefObject(
    override val mutableName: ReactiveVariable<String>,
    val spec: ReactiveVariable<ControlSpec>
) : AbstractRenamableObject() {
    constructor(name: String, spec: ControlSpec) : this(reactiveVariable(name), reactiveVariable(spec))

    override fun canRenameTo(newName: String): Boolean = true

    fun defaultControl(context: Context, defaultBus: ObjectReference? = null) =
        spec.now.defaultControl(context, defaultBus)

    override fun createReference(): Nothing = throw UnsupportedOperationException()

    override fun toString(): String = "${name.now}: ${spec.now}"

    companion object {
        private val freq = ParameterDefObject(
            "freq",
            NumericalControlSpec(440.0, 20.0, 20000.0, 1.0.toDecimal(), Warp.Exponential, Color.BLACK)
        )
        private val amp = ParameterDefObject(
            "amp",
            NumericalControlSpec(0.1, 0.0, 1.0, 0.01.toDecimal(), Warp.Linear, Color.ORANGE)
        )
        private val pan = ParameterDefObject(
            "pan",
            NumericalControlSpec(0.0, -1.0, 1.0, 0.1.toDecimal(), Warp.Linear, Color.BLUE)
        )

        val defaults = listOf(freq, amp, pan)
    }
}