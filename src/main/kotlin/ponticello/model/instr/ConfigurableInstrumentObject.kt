package ponticello.model.instr

import javafx.scene.paint.Color
import ponticello.sc.NumericalControlSpec
import ponticello.sc.ParameterType
import ponticello.ui.registry.ParameterDefList
import reaktive.value.ReactiveVariable
import reaktive.value.now

sealed interface ConfigurableInstrumentObject : InstrumentObject {
    override val parameters: ParameterDefList

    override val color: ReactiveVariable<Color>

    fun supports(type: ParameterType): Boolean

    fun createDefaultValueMap(): String = parameters.filter { p -> p.spec.now is NumericalControlSpec }
        .joinToString(", ", "(", ")") { param ->
            val spec = param.spec.now as NumericalControlSpec
            "${param.name.now}: ${spec.defaultValue.text}"
        }
}