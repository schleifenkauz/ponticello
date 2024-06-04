package xenakis.model

import hextant.context.Context
import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.now
import xenakis.impl.SuperColliderClient

@Serializable
sealed interface InstrumentObject : NamedObject {
    val color: ReactiveVariable<Color>

    fun SuperColliderClient.sync() {}

    fun SuperColliderClient.remove() {}

    fun createEvent(): Map<String, String>

    override fun createReference(): Reference = Reference(this)

    @Serializable(with = Reference.Serializer::class)
    class Reference(name: String) : AbstractObjectReference<InstrumentObject>(name) {
        constructor(obj: InstrumentObject) : this(obj.name.now) {
            this.obj = obj
        }

        override fun getRegistry(context: Context): ObjectRegistry<InstrumentObject> = context[InstrumentRegistry]

        object Serializer : ObjectReference.Serializer<Reference>() {
            override fun createReference(name: String): Reference = Reference(name)
        }
    }

    enum class Type {
        SynthDef, VSTPlugin
    }
}