package xenakis.model

import hextant.context.Context
import hextant.core.editor.ListenerManager
import javafx.geometry.HorizontalDirection
import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import xenakis.impl.ColorSerializer
import xenakis.sc.ScExpr
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
    @Serializable(with = ColorSerializer::class) val displayColor: Color,
    val display: Boolean
) : ParameterControl() {
    override fun copy(): ParameterControl =
        EnvelopeControl(envelope = envelope.copy(), displayColor, display)

    override fun cut(cutPos: Double, whichHalve: HorizontalDirection): ParameterControl =
        EnvelopeControl(envelope.cut(cutPos, whichHalve), displayColor, display)
}

@Serializable
class BusControl(val bus: BusObjectReference) : ParameterControl() {
    override fun initialize(context: Context) {
        bus.resolve(context)
    }
}

@Serializable
class BusValueControl(val bus: BusObjectReference) : ParameterControl() {
    override fun initialize(context: Context) {
        bus.resolve(context)
    }
}

@Serializable
data class SingleBusValueControl(val bus: BusObjectReference) : ParameterControl() {
    override fun initialize(context: Context) {
        bus.resolve(context)
    }
}

@Serializable
data class BufferControl(val buffer: BufferObjectReference) : ParameterControl() {
    override fun initialize(context: Context) {
        buffer.resolve(context)
    }
}

@Serializable
data class CustomControl(val expr: ScExpr) : ParameterControl()

@Serializable
data class ConstantControl(val value: Double) : ParameterControl()

