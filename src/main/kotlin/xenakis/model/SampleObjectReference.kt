package xenakis.model

import hextant.context.Context
import kotlinx.serialization.Serializable
import reaktive.value.now

@Serializable(with = SampleObjectReference.Serializer::class)
class SampleObjectReference(name: String) : AbstractObjectReference<SampleObject>(name) {
    constructor(obj: SampleObject) : this(obj.name.now) {
        this.obj = obj
    }

    override fun getRegistry(context: Context): ObjectRegistry<SampleObject> = context[SampleRegistry]

    object Serializer : ObjectReference.Serializer<SampleObjectReference?>() {
        override fun createReference(name: String): SampleObjectReference = SampleObjectReference(name)
    }
}