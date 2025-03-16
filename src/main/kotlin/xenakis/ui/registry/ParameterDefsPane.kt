package xenakis.ui.registry

import hextant.context.Context
import javafx.scene.Node
import reaktive.list.MutableReactiveList
import reaktive.value.ReactiveString
import reaktive.value.reactiveValue
import xenakis.model.obj.ParameterDefObject

class ParameterDefsPane(context: Context, private val parameters: MutableReactiveList<ParameterDefObject>) :
    ToolPane() {
    override fun getTitle(): ReactiveString = reactiveValue("Parameters")

    private val config = ParameterListSource(context, parameters)

    private val objectBoxList: ObjectBoxList<ParameterDefObject> = ObjectBoxList(config)

    init {
        config.syncWithBoxList(objectBoxList)
    }

    override fun getContent(): Node = objectBoxList
}