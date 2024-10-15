package xenakis.model.obj

import reaktive.list.MutableReactiveList

interface ConfigurableParameterizedObjectDef : ParameterizedObjectDef {
    override val parameters: MutableReactiveList<ParameterDefObject>
}