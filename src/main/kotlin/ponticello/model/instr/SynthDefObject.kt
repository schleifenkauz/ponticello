package ponticello.model.instr

import kotlinx.serialization.Serializable
import ponticello.model.obj.SuperColliderObject
import ponticello.model.registry.ObjectRegistry
import ponticello.model.registry.reference
import reaktive.value.now

@Serializable
sealed interface SynthDefObject : InstrumentObject, SuperColliderObject {
    override val registry: ObjectRegistry<*>?
        get() = context[InstrumentRegistry]

    override val superColliderName: String
        get() = "~synthdef_${name.now}"

    override fun onUpdated() {
        //TODO
    }

    override fun instrumentReference(): InstrumentReference = InstrumentReference.UserDefined(this.reference())
}