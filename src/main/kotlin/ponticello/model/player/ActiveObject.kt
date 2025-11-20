package ponticello.model.player

import ponticello.model.instr.InstrumentObject
import ponticello.model.instr.ParameterizedObject
import ponticello.model.obj.NamedObject

sealed class ActiveObject {
    abstract val associatedObject: NamedObject?

    abstract val uniqueName: String

    abstract val superColliderName: String

    open val associatedDef: InstrumentObject?
        get() = (associatedObject as? ParameterizedObject)?.def
}