package xenakis.model

import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.sc.*

@Serializable
class ParameterDefObject(
    private val _name: ReactiveVariable<String>,
    val spec: ReactiveVariable<ControlSpec>
) : RenamableObject {
    override val name: ReactiveValue<String>
        get() = _name

    constructor(name: String, spec: ControlSpec) : this(reactiveVariable(name), reactiveVariable(spec))

    override fun canRenameTo(newName: String): Boolean = true

    override fun rename(newName: String) {
        _name.set(newName)
    }

    fun defaultControl() = when (val spec = spec.now) {
        is BufferControlSpec -> BufferControl(spec.defaultValue)
        is BusControlSpec -> BusControl(spec.defaultValue)
        is NumericalControlSpec -> ConstantControl(spec.defaultValue.get())
    }

    companion object {
        val freq = ParameterDefObject(
            "freq",
            NumericalControlSpec(440.0, 20.0, 20000.0, Warp.Exponential, 1.0, Color.BLACK)
        )
        val amp = ParameterDefObject(
            "amp",
            NumericalControlSpec(0.1, 0.0, 1.0, Warp.Linear, 0.01, Color.ORANGE)
        )
        val pan = ParameterDefObject(
            "pan",
            NumericalControlSpec(0.0, -1.0, 1.0, Warp.Linear, 0.1, Color.BLUE)
        )

        val defaults = listOf(freq, amp, pan)
    }
}