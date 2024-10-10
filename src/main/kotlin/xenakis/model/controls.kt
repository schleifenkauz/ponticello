package xenakis.model

import hextant.context.Context
import hextant.serial.EditorRoot
import javafx.geometry.HorizontalDirection
import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.ColorSerializer
import xenakis.impl.Decimal
import xenakis.impl.copy
import xenakis.sc.*
import xenakis.sc.editor.ScExprExpander

@Serializable
sealed class ParameterControl {
    open fun copy(): ParameterControl = this

    open fun cut(cutPos: Decimal, whichHalve: HorizontalDirection) = this

    open fun initialize(context: Context) {}
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
class BusControl(val bus: ReactiveVariable<ObjectReference>) : ParameterControl() {
    override fun initialize(context: Context) {
        bus.now.resolve(context[BusRegistry])
    }

    override fun copy(): ParameterControl = BusControl(bus.copy())
}

@Serializable
class BusValueControl(val bus: ReactiveVariable<ObjectReference>) : ParameterControl() {
    override fun initialize(context: Context) {
        bus.now.resolve(context[BusRegistry])
    }

    override fun copy(): ParameterControl = BusValueControl(bus.copy())
}

@Serializable
data class SingleBusValueControl(val bus: ReactiveVariable<ObjectReference>) : ParameterControl() {
    override fun initialize(context: Context) {
        bus.now.resolve(context[BusRegistry])
    }

    override fun copy(): ParameterControl = SingleBusValueControl(bus.copy())
}

@Serializable
data class BufferControl(
    val sample: ReactiveVariable<ObjectReference?>,
    val display: ReactiveVariable<Boolean> = reactiveVariable(true)
) : ParameterControl() {
    override fun initialize(context: Context) {
        sample.now?.resolve(context[SampleRegistry])
    }

    override fun copy(): ParameterControl = BufferControl(sample.copy(), display.copy())
}

@Serializable
data class GroupControl(val group: ReactiveVariable<ObjectReference>) : ParameterControl() {
    override fun initialize(context: Context) {
        group.now.resolve(context[GroupRegistry])
    }

    override fun copy(): ParameterControl = GroupControl(group.copy())
}

@Serializable
data class CustomControl(val expr: EditorRoot<ScExprExpander>) : ParameterControl() {
    override fun copy(): ParameterControl = CustomControl(expr.clone())
}

@Serializable
data class ConstantControl(val value: ReactiveVariable<Decimal>) : ParameterControl() {
    override fun copy(): ParameterControl = ConstantControl(value.copy())
}

fun ParameterControl.makeExpr(): ScExpr = when (this) {
    is BufferControl -> sample.now?.get<SampleObject>()?.superColliderExpr ?: Nil
    is BusControl -> bus.now.get<BusObject>().superColliderExpr
    is BusValueControl -> RawScExpr("In.ar(${bus.now.get<BusObject>().superColliderName})")
    is ConstantControl -> DecimalLiteral(value.now)
    is CustomControl -> expr.editor.result.now
    is EnvelopeControl -> RawScExpr(envelope.code())
    is GroupControl -> group.now.get<GroupObject>().superColliderExpr
    is KnobControl -> DecimalLiteral(value.now)
    is SingleBusValueControl -> bus.now.get<BusObject>().superColliderExpr.send("getSynchronous")
}