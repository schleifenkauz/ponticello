package xenakis.model

import hextant.codegen.UseEditor
import hextant.context.Context
import kotlinx.serialization.Serializable
import reaktive.value.now
import xenakis.sc.editor.BufferSelector

@UseEditor(BufferSelector::class)
@Serializable(with = BufferObjectReference.Serializer::class)
class BufferObjectReference(name: String) : AbstractObjectReference<BufferObject>(name) {
    constructor(obj: BufferObject) : this(obj.name.now) {
        this.obj = obj
    }

    override fun getRegistry(context: Context): ObjectRegistry<BufferObject> = context[BufferRegistry]

    object Serializer : ObjectReference.Serializer<BufferObjectReference>() {
        override fun createReference(name: String): BufferObjectReference = BufferObjectReference(name)
    }
}