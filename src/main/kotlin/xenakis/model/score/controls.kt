package xenakis.model.score

import hextant.context.Context
import hextant.serial.EditorRoot
import javafx.geometry.HorizontalDirection
import javafx.scene.paint.Color
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
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

    open fun cut(cutPos: Decimal, whichHalve: HorizontalDirection, spec: NumericalControlSpec?) = this
}

@Serializable
@SerialName("Envelope")
class EnvelopeControl(
    val points: Envelope,
    val displayColor: ReactiveVariable<@Serializable(with = ColorSerializer::class) Color> = reactiveVariable(Color.BLACK),
    val display: ReactiveVariable<Boolean> = reactiveVariable(true)
) : ParameterControl() {
    override fun copy(): ParameterControl =
        EnvelopeControl(points = points.copy(), displayColor, display)

    override fun cut(cutPos: Decimal, whichHalve: HorizontalDirection, spec: NumericalControlSpec?): ParameterControl {
        val warp = spec?.warp ?: error("No spec provided")
        return EnvelopeControl(points.cut(cutPos, whichHalve, warp), displayColor, display)
    }

    override fun initialize(context: Context) {
        points.initialize(context)
    }
}

@Serializable
@SerialName("Bus")
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
@SerialName("BusValue")
class BusValueControl(val bus: ReactiveVariable<BusReference>) : ParameterControl() {
    override fun initialize(context: Context) {
        bus.now.resolve(context[BusRegistry])
    }

    override fun copy(): ParameterControl = BusValueControl(bus.copy())
}

@Serializable
@SerialName("SingleBusValue")
data class SingleBusValueControl(val bus: ReactiveVariable<BusReference>) : ParameterControl() {
    override fun initialize(context: Context) {
        bus.now.resolve(context[BusRegistry])
    }

    override fun copy(): ParameterControl = SingleBusValueControl(bus.copy())
}

@Serializable
@SerialName("Buffer")
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
@SerialName("Group")
data class GroupControl(val group: ReactiveVariable<GroupReference>) : ParameterControl() {
    override fun initialize(context: Context) {
        group.now.resolve(context[GroupRegistry])
    }

    override fun copy(): ParameterControl = GroupControl(group.copy())
}

@Serializable
@SerialName("Custom")
data class CustomControl(val expr: EditorRoot<@Contextual ScExprExpander>) : ParameterControl() {
    override fun copy(): ParameterControl = CustomControl(expr.clone(context))

    override fun initialize(context: Context) {
        expr.initialize(context)
    }
}

@Serializable
@SerialName("Value")
data class ValueControl(val value: ReactiveVariable<Decimal>) : ParameterControl() {
    override fun copy(): ParameterControl = ValueControl(value.copy())

    companion object {
        fun create(value: Decimal) = ValueControl(reactiveVariable(value))
    }
}

fun ParameterControl.makeExpr(spec: NumericalControlSpec?): ScExpr? {
    return when (this) {
        is BufferControl -> sample.now.get()?.superColliderExpr
        is BusControl -> bus.now.get()?.superColliderExpr
        is BusValueControl -> {
            val busObject = bus.now.get() ?: return null
            RawScExpr("In.ar(${busObject.superColliderName})")
        }

        is ValueControl -> DecimalLiteral(value.now)
        is CustomControl -> expr.editor.result.now
        is EnvelopeControl -> {
            val warp = spec?.warp ?: error("No spec provided")
            RawScExpr(points.code(warp = warp))
        }
        is GroupControl -> group.now.get()?.let { grp -> Identifier(grp.superColliderName) }
        is SingleBusValueControl -> bus.now.get()?.superColliderExpr?.send("getSynchronous")
    }
}

fun ParameterControl.getNumericalValue() = when (this) {
    is ValueControl -> value.now
    is EnvelopeControl -> points.points.first().value
    else -> null
}

fun ParameterControl.getBus() = when (this) {
    is BusControl -> bus.now
    is BusValueControl -> bus.now
    is SingleBusValueControl -> bus.now
    else -> null
}
