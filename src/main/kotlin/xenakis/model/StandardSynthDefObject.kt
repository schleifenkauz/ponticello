package xenakis.model

import hextant.context.Context
import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import reaktive.list.ReactiveList
import reaktive.list.unmodifiableReactiveList
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.reactiveValue
import reaktive.value.reactiveVariable
import xenakis.impl.ColorSerializer
import xenakis.impl.ScWriter
import xenakis.sc.NumericalControlSpec
import xenakis.sc.Warp

@Serializable
class StandardSynthDefObject(
    private val _name: String,
    private val _parameters: List<ParameterDefObject>,
    override val color: ReactiveVariable<@Serializable(with = ColorSerializer::class) Color>
) : SynthDefObject {
    override val name: ReactiveValue<String>
        get() = reactiveValue(_name)
    override val parameters: ReactiveList<ParameterDefObject>
        get() = unmodifiableReactiveList(_parameters)

    override fun onAdded(context: Context) {}

    override fun initialize(context: Context) {
        for (p in _parameters) {
            p.initialize(context)
        }
    }

    override fun onRemoved() {}

    override fun ScWriter.allocateServerObject() {}

    override fun sync(writer: ScWriter) {}

    override fun sync() {}

    override fun ScWriter.freeServerObject() {}

    companion object {
        val default = StandardSynthDefObject(
            "default",
            listOf(
                ParameterDefObject(
                    "freq",
                    NumericalControlSpec(440.0, 20.0, 20000.0, Warp.Exponential, 1.0, Color.BLACK)
                ),
                ParameterDefObject(
                    "amp",
                    NumericalControlSpec(0.1, 0.0, 1.0, Warp.Linear, 0.01, Color.ORANGE)
                ),
                ParameterDefObject(
                    "pan",
                    NumericalControlSpec(0.0, -1.0, 1.0, Warp.Linear, 0.1, Color.BLUE)
                )
            ),
            reactiveVariable(Color.WHITE)
        )

        val all = listOf(default).associateBy { def -> def._name }
    }
}