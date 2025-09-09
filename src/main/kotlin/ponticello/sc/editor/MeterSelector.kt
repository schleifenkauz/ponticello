package ponticello.sc.editor

import ponticello.model.obj.MeterObject
import ponticello.model.registry.MeterRegistry
import ponticello.model.registry.NamedObjectList
import ponticello.ui.registry.MeterSelectorPrompt

class MeterSelector : ObjectSelector<MeterObject>() {
    override fun getList(): NamedObjectList<MeterObject> = context[MeterRegistry]

    override fun createNewObject(name: String): MeterObject? =
        MeterSelectorPrompt.MeterConfigDialog(MeterObject.createDefault(), name).showDialog()
}