package xenakis.model.obj

import xenakis.ui.registry.ParameterDefList

interface ConfigurableParameterizedObjectDef : ParameterizedObjectDef {
    override val parameters: ParameterDefList

    fun sync()
}