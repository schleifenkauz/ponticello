package xenakis.ui.registry

import kotlinx.serialization.Serializable
import reaktive.value.now
import xenakis.model.obj.ParameterDefObject
import xenakis.model.registry.CustomNamedObjectListSerializer
import xenakis.model.registry.NamedObjectList
import xenakis.sc.ControlSpec

@Serializable(with = ParameterDefList.Serializer::class)
class ParameterDefList(
    override val objects: MutableList<ParameterDefObject> = mutableListOf(),
) : NamedObjectList<ParameterDefObject>() {
    override val objectType: String
        get() = "Parameter"

    object Serializer : CustomNamedObjectListSerializer<ParameterDefObject, ControlSpec, ParameterDefList>(
        kotlinx.serialization.serializer()
    ) {
        override fun createList(elements: MutableList<ParameterDefObject>): ParameterDefList =
            ParameterDefList(elements)

        override fun getContent(obj: ParameterDefObject): ControlSpec = obj.spec.now

        override fun createObject(name: String, content: ControlSpec): ParameterDefObject =
            ParameterDefObject(name, content)
    }
}