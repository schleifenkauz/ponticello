package xenakis.ui.registry

import hextant.context.Context
import reaktive.list.MutableReactiveList
import xenakis.model.obj.ParameterDefObject

class ParameterDefsPane(context: Context, parameters: MutableReactiveList<ParameterDefObject>) : ToolPane() {
    private val config = ParameterListSource(context, parameters)

    private val objectBoxList: ObjectBoxList<ParameterDefObject> = ObjectBoxList(config)

    init {
        config.syncWithBoxList(objectBoxList)
        setup(title = "Parameters", content = objectBoxList)
    }
}