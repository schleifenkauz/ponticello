package xenakis.model.player

import xenakis.model.obj.ParameterizedObject
import xenakis.model.obj.ParameterizedObjectDef
import xenakis.model.registry.NamedObject

sealed class ActiveObject {
    abstract val associatedObject: NamedObject?

    abstract val uniqueName: String

    abstract val superColliderName: String

    open val associatedDef: ParameterizedObjectDef?
        get() = (associatedObject as? ParameterizedObject)?.def
}