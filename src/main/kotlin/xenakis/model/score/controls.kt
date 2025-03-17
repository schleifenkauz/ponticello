package xenakis.model.score

import hextant.context.Context
import hextant.serial.EditorRoot
import javafx.geometry.HorizontalDirection
import javafx.scene.paint.Color
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.ColorSerializer
import xenakis.impl.Decimal
import xenakis.impl.copy
import xenakis.model.obj.*
import xenakis.model.registry.BusRegistry
import xenakis.model.registry.GroupRegistry
import xenakis.model.registry.SampleRegistry
import xenakis.model.registry.reference
import xenakis.sc.*
import xenakis.sc.editor.ScExprExpander

@Serializable
sealed class ParameterControl : AbstractContextualObject() {
    open fun copy(): ParameterControl = this

    open fun cut(cutPos: Decimal, whichHalve: HorizontalDirection) = this
}

@Serializable
class KnobControl(val value: ReactiveVariable<Decimal>) : ParameterControl() {
    override fun copy(): ParameterControl = KnobControl(value.copy())

    fun get() = value.now
}

@Serializable
class EnvelopeControl(
    val envelope: Envelope,
    val displayColor: ReactiveVariable<@Serializable(with = ColorSerializer::class) Color>,
    val display: ReactiveVariable<Boolean>
) : ParameterControl() {
    override fun copy(): ParameterControl =
        EnvelopeControl(envelope = envelope.copy(), displayColor, display)

    override fun cut(cutPos: Decimal, whichHalve: HorizontalDirection): ParameterControl =
        EnvelopeControl(envelope.cut(cutPos, whichHalve), displayColor, display)

    override fun initialize(context: Context) {
        envelope.initialize(context)
    }
}

@Serializable
class BusControl(val bus: ReactiveVariable<BusReference>) : ParameterControl() {
    override fun initialize(context: Context) {
        bus.now.resolve(context[BusRegistry])
    }

    override fun copy(): ParameterControl = BusControl(bus.copy())

    companion object {
        fun create(bus: BusObject) = BusControl(reactiveVariable(bus.reference()))
    }
}

@Serializable
class BusValueControl(val bus: ReactiveVariable<BusReference>) : ParameterControl() {
    override fun initialize(context: Context) {
        bus.now.resolve(context[BusRegistry])
    }

    override fun copy(): ParameterControl = BusValueControl(bus.copy())
}

@Serializable
data class SingleBusValueControl(val bus: ReactiveVariable<BusReference>) : ParameterControl() {
    override fun initialize(context: Context) {
        bus.now.resolve(context[BusRegistry])
    }

    override fun copy(): ParameterControl = SingleBusValueControl(bus.copy())
}

@Serializable
data class BufferControl(
    val sample: ReactiveVariable<SampleReference>,
    val display: ReactiveVariable<Boolean> = reactiveVariable(true)
) : ParameterControl() {
    override fun initialize(context: Context) {
        sample.now.resolve(context[SampleRegistry])
    }

    override fun copy(): ParameterControl = BufferControl(sample.copy(), display.copy())
}

@Serializable
data class GroupControl(val group: ReactiveVariable<GroupReference>) : ParameterControl() {
    override fun initialize(context: Context) {
        group.now.resolve(context[GroupRegistry])
    }

    override fun copy(): ParameterControl = GroupControl(group.copy())
}

@Serializable
data class CustomControl(val expr: EditorRoot<@Contextual ScExprExpander>) : ParameterControl() {
    override fun copy(): ParameterControl = CustomControl(expr.clone(context))
}

@Serializable
data class ConstantControl(val value: ReactiveVariable<Decimal>) : ParameterControl() {
    override fun copy(): ParameterControl = ConstantControl(value.copy())

    companion object {
        fun create(value: Decimal) = ConstantControl(reactiveVariable(value))
    }
}

fun ParameterControl.makeExpr(): ScExpr? {
    return when (this) {
        is BufferControl -> sample.now.get()?.superColliderExpr
        is BusControl -> bus.now.get()?.superColliderExpr
        is BusValueControl -> {
            val busObject = bus.now.get() ?: return null
            RawScExpr("In.ar(${busObject.superColliderName})")
        }

        is ConstantControl -> DecimalLiteral(value.now)
        is CustomControl -> expr.editor.result.now
        is EnvelopeControl -> RawScExpr(envelope.code())
        is GroupControl -> group.now.get()?.let { grp -> Identifier(grp.superColliderName) }
        is KnobControl -> DecimalLiteral(value.now)
        is SingleBusValueControl -> bus.now.get()?.superColliderExpr?.send("getSynchronous")
    }
}

fun ParameterControl.getNumericalValue() = when (this) {
    is ConstantControl -> value.now
    is KnobControl -> get()
    is EnvelopeControl -> envelope.points.first().value
    else -> null
}

fun ParameterControl.getBus() = when (this) {
    is BusControl -> bus.now
    is BusValueControl -> bus.now
    is SingleBusValueControl -> bus.now
    else -> null
}
