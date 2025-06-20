package ponticello.model.obj

import ponticello.sc.ParameterType
import ponticello.ui.registry.ParameterDefList

sealed interface ConfigurableInstrumentObject : InstrumentObject {
    fun supports(type: ParameterType): Boolean

    override val parameters: ParameterDefList
}