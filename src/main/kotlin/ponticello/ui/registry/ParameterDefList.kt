package ponticello.ui.registry

import kotlinx.serialization.Serializable
import ponticello.model.obj.ParameterDefObject
import ponticello.model.registry.CustomNamedObjectListSerializer
import ponticello.model.registry.NamedObjectList
import ponticello.sc.ControlSpec
import reaktive.value.now

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