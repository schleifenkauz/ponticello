package ponticello.sc.editor

import ponticello.model.obj.ProcessDefObject
import ponticello.model.registry.ObjectRegistry
import ponticello.model.registry.ProcessDefRegistry

class ProcessDefSelector : ObjectSelector<ProcessDefObject>() {
    override fun getList(): ObjectRegistry<ProcessDefObject> = context[ProcessDefRegistry]

    override fun createNewObject(name: String): ProcessDefObject = ProcessDefObject.newEmpty(name)
}