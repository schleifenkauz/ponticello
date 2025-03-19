package xenakis.model.obj

import hextant.context.Context
import javafx.scene.paint.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.copy
import xenakis.impl.toDecimal
import xenakis.sc.*

@Serializable
class ParameterDefObject(
    @SerialName("name") override val mutableName: ReactiveVariable<String>,
    val spec: ReactiveVariable<ControlSpec>
) : AbstractRenamableObject() {
    constructor(name: String, spec: ControlSpec) : this(reactiveVariable(name), reactiveVariable(spec))

    override fun canRenameTo(newName: String): Boolean = true

    fun defaultControl(context: Context, defaultBus: BusReference? = null) =
        spec.now.defaultControl(context, defaultBus)

    override fun toString(): String = "${name.now}: ${spec.now}"

    fun simpleString(): String {
        val type = when (spec.now) {
            is NumericalControlSpec -> "num"
            is BufferControlSpec -> "buf"
            is BusControlSpec -> "bus"
            is GroupControlSpec -> "group"
        }
        return "${name.now} ($type)"
    }

    fun copy() = ParameterDefObject(mutableName.copy(), spec.copy())

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