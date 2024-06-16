package xenakis.model

import hextant.context.Context
import hextant.core.editor.ListenerManager
import hextant.serial.EditorRoot
import javafx.geometry.HorizontalDirection
import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.ColorSerializer
import xenakis.sc.editor.ScExprExpander
import xenakis.ui.KnobControlView

@Serializable
sealed class ParameterControl {
    open fun copy(): ParameterControl = this

    open fun cut(cutPos: Double, whichHalve: HorizontalDirection) = this

    open fun initialize(context: Context) {}
}

@Serializable
class KnobControl(private var value: Double) : ParameterControl() {
    @Transient
    val views = ListenerManager.createWeakListenerManager<KnobControlView>()

    override fun copy(): ParameterControl = KnobControl(value)

    fun set(value: Double) {
        if (value == this.value) return
        this.value = value
        views.notifyListeners { updatedValue(this@KnobControl, value) }
    }

    fun get() = value

    fun addView(view: KnobControlView) {
        views.addListener(view)
        view.updatedValue(this, value)
    }
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

