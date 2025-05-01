package xenakis.sc.editor

import xenakis.model.obj.GlobalPatternObject
import xenakis.model.registry.GlobalPatternRegistry
import xenakis.model.registry.ObjectRegistry

class GlobalPatternSelector : ObjectSelector<GlobalPatternObject>() {
    override fun getList(): ObjectRegistry<GlobalPatternObject> = context[GlobalPatternRegistry]

    override fun createNewObject(name: String): GlobalPatternObject = GlobalPatternObject.create(name)
}