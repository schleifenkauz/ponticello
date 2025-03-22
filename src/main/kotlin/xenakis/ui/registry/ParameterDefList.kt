package xenakis.ui.registry

import kotlinx.serialization.Serializable
import xenakis.model.obj.ParameterDefObject
import xenakis.model.registry.NamedObjectList
import xenakis.model.registry.NamedObjectListSerializer

@Serializable(with = ParameterDefList.Serializer::class)
class ParameterDefList(
    override val objects: MutableList<ParameterDefObject> = mutableListOf(),
) : NamedObjectList<ParameterDefObject>() {
    override val objectType: String
        get() = "Parameter"

    object Serializer : NamedObjectListSerializer<ParameterDefObject, ParameterDefList>(
        kotlinx.serialization.serializer(), ::ParameterDefList
    )
}