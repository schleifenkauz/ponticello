package xenakis.model

import hextant.context.Context
import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import reaktive.list.ReactiveList
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.now
import xenakis.impl.SuperColliderClient

@Serializable
sealed interface SynthDefObject : ParameterizedObject, NamedObject {
    override val name: ReactiveValue<String>
    val color: ReactiveVariable<Color>
    val parameters: ReactiveList<ParameterDefObject>

    fun SuperColliderClient.sync() {}

    fun SuperColliderClient.removeSynthDef() {}

    override fun getParameter(name: String): ParameterDefObject =
        parameters.now.find { it.name.now == name } ?: error("Parameter $name not found in SynthDef '${this.name.now}'")

    fun defaultControls(context: Context) =
        parameters.now.associateTo(mutableMapOf()) { p -> p.name.now to p.defaultControl(context) }

    override fun createReference(): Reference = Reference(this)

    @Serializable(with = Reference.Serializer::class)
    class Reference(name: String) : AbstractObjectReference<SynthDefObject>(name) {
        constructor(obj: SynthDefObject) : this(obj.name.now) {
            this.obj = obj
        }

        override fun getRegistry(context: Context): ObjectRegistry<SynthDefObject> = context[SynthDefRegistry]

        object Serializer : ObjectReference.Serializer<Reference>() {
            override fun createReference(name: String): Reference = Reference(name)
        }
    }
}