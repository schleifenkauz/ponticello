package xenakis.sc.editor

import xenakis.model.obj.ProcessDefObject
import xenakis.model.registry.ObjectRegistry
import xenakis.model.registry.ProcessDefRegistry

class ProcessDefSelector : ObjectSelector<ProcessDefObject>() {
    override fun getRegistry(): ObjectRegistry<ProcessDefObject> = context[ProcessDefRegistry]

    override fun createNewObject(name: String): ProcessDefObject = ProcessDefObject.newEmpty(name, context)
}