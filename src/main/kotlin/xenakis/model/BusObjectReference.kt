package xenakis.model

import hextant.codegen.UseEditor
import hextant.context.Context
import kotlinx.serialization.Serializable
import reaktive.value.now
import xenakis.sc.editor.BusSelector

@UseEditor(BusSelector::class)
@Serializable(with = BusObjectReference.Serializer::class)
class BusObjectReference(name: String) : AbstractObjectReference<BusObject>(name) {
    constructor(obj: BusObject) : this(obj.name.now) {
        this.obj = obj
    }

    override fun getRegistry(context: Context): ObjectRegistry<BusObject> = context[BusRegistry]

    object Serializer : ObjectReference.Serializer<BusObjectReference>() {
        override fun createReference(name: String): BusObjectReference = BusObjectReference(name)
    }
}