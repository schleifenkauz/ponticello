package ponticello.model.obj

import ponticello.ui.registry.ParameterDefList

interface ConfigurableParameterizedObjectDef : ParameterizedObjectDef {
    override val parameters: ParameterDefList
}