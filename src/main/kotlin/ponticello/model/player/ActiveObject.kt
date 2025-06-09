package ponticello.model.player

import ponticello.model.obj.InstrumentObject
import ponticello.model.obj.ParameterizedObject
import ponticello.model.registry.NamedObject

sealed class ActiveObject {
    abstract val associatedObject: NamedObject?

    abstract val uniqueName: String

    abstract val superColliderName: String

    open val associatedDef: InstrumentObject?
        get() = (associatedObject as? ParameterizedObject)?.def
}