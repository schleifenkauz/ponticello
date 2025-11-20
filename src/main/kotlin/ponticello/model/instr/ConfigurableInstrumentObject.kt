package ponticello.model.instr

import javafx.scene.paint.Color
import ponticello.sc.ParameterType
import ponticello.ui.registry.ParameterDefList
import reaktive.value.ReactiveVariable

sealed interface ConfigurableInstrumentObject : InstrumentObject {
    override val parameters: ParameterDefList

    override val color: ReactiveVariable<Color>

    fun supports(type: ParameterType): Boolean
}