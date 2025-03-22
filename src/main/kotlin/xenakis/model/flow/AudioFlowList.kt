package xenakis.model.flow

import kotlinx.serialization.Serializable
import xenakis.model.registry.NamedObjectList
import xenakis.model.registry.NamedObjectListSerializer

@Serializable(with = AudioFlowList.Serializer::class)
class AudioFlowList(
    override val objects: MutableList<AudioFlow> = mutableListOf(),
) : NamedObjectList<AudioFlow>() {
    override val objectType: String
        get() = "Audio flow"

    object Serializer : NamedObjectListSerializer<AudioFlow, AudioFlowList>(
        kotlinx.serialization.serializer(), ::AudioFlowList
    )
}