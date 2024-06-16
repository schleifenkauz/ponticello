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
import xenakis.impl.copy
import xenakis.sc.editor.ScExprExpander

@Serializable
sealed class ParameterControl {
    open fun copy(): ParameterControl = this

    open fun cut(cutPos: Double, whichHalve: HorizontalDirection) = this

    open fun initialize(context: Context) {}
}

@Serializable
class KnobControl(val value: ReactiveVariable<Double>) : ParameterControl() {
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

    override fun cut(cutPos: Double, whichHalve: HorizontalDirection): ParameterControl =
        EnvelopeControl(envelope.cut(cutPos, whichHalve), displayColor, display)
}

@Serializable
class BusControl(val bus: ReactiveVariable<BusObjectReference>) : ParameterControl() {
    override fun initialize(context: Context) {
        bus.now.resolve(context)
    }
}

@Serializable
class BusValueControl(val bus: ReactiveVariable<BusObjectReference>) : ParameterControl() {
    override fun initialize(context: Context) {
        bus.now.resolve(context)
    }
}

@Serializable
data class SingleBusValueControl(val bus: ReactiveVariable<BusObjectReference>) : ParameterControl() {
    override fun initialize(context: Context) {
        bus.now.resolve(context)
    }
}

@Serializable
data class BufferControl(
    val sample: ReactiveVariable<SampleObjectReference?>,
    val display: ReactiveVariable<Boolean> = reactiveVariable(true)
) : ParameterControl() {
    override fun initialize(context: Context) {
        sample.now?.resolve(context)
    }
}

@Serializable
data class GroupControl(val group: ReactiveVariable<GroupObjectReference>) : ParameterControl() {
    override fun initialize(context: Context) {
        group.now.resolve(context)
    }
}

@Serializable
data class CustomControl(val expr: EditorRoot<ScExprExpander>) : ParameterControl()

@Serializable
data class ConstantControl(val value: ReactiveVariable<Double>) : ParameterControl()

