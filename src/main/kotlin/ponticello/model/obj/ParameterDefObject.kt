package ponticello.model.obj

import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import ponticello.impl.copy
import ponticello.impl.toDecimal
import ponticello.sc.*
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
class ParameterDefObject(val spec: ReactiveVariable<ControlSpec>) : AbstractRenamableObject() {

    var isImmutable = false
        private set

    private fun immutable() = also { isImmutable = true }

    override fun canRenameTo(newName: String): Boolean = !isImmutable

    fun defaultControl() = spec.now.defaultControl()

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

    override fun copy() = ParameterDefObject(spec.copy())

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

        operator fun invoke(name: String, spec: ControlSpec) = ParameterDefObject(reactiveVariable(spec)).withName(name)
    }
}