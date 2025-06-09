package ponticello.model.obj

import ponticello.ui.registry.ParameterDefList

sealed interface ConfigurableInstrumentObject : InstrumentObject {
    override val parameters: ParameterDefList
}