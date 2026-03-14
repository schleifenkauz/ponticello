package ponticello.model.instr

import kotlinx.serialization.Serializable
import ponticello.model.obj.SuperColliderObject
import reaktive.value.now

@Serializable
sealed interface SynthDefObject : InstrumentObject, SuperColliderObject {
    override val instrumentType: String
        get() = "SynthDef"

    override val superColliderName: String
        get() = "~synthdef_${name.now}"

}