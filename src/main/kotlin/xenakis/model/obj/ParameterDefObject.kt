package xenakis.model.obj

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

    var isImmutable = false
        private set

    private fun immutable() = also { isImmutable = true }

    override fun canRenameTo(newName: String): Boolean = !isImmutable

    fun defaultControl(defaultBus: BusReference? = null) = spec.now.defaultControl(defaultBus)

    override fun toString(): String = "${name.now}: ${spec.now}"

    fun simpleString(): String {
        val type = when (spec.now) {
            is NumericalControlSpec -> "num"
            is BufferPositionControlSpec -> "buf-pos"
            is BufferControlSpec -> "buf"
            is BusControlSpec -> "bus"
            is AttackReleaseControlSpec -> return "attack-release"
        }
        return "${name.now} ($type)"
    }

    fun copy() = ParameterDefObject(mutableName.copy(), spec.copy())

    companion object {
        private val FREQ = ParameterDefObject(
            "freq",
            NumericalControlSpec(440.0, 20.0, 20000.0, 1.0.toDecimal(), 0.02, Warp.Exponential, Color.BLACK)
        )
        val AMP = ParameterDefObject(
            "amp",
            NumericalControlSpec(0.1, 0.0, 1.0, 0.01.toDecimal(), 0.02, Warp.Linear, Color.ORANGE)
        )
        val PAN = ParameterDefObject(
            "pan",
            NumericalControlSpec(0.0, -1.0, 1.0, 0.1.toDecimal(), 0.02, Warp.Linear, Color.BLUE)
        )

        val LEVEL = ParameterDefObject("level", NumericalControlSpec.LEVEL).immutable()

        val ATTACK_RELEASE = ParameterDefObject("attack-release", AttackReleaseControlSpec()).immutable()

        val defaults = listOf(FREQ, AMP, PAN)
    }
}