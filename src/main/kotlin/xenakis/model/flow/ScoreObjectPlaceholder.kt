package xenakis.model.flow

import hextant.context.Context
import hextant.context.withoutUndo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.now
import xenakis.model.obj.BusObject
import xenakis.model.obj.GroupObject
import xenakis.model.obj.GroupReference
import xenakis.model.registry.GroupRegistry
import xenakis.model.registry.ObjectReference
import xenakis.sc.client.ScWriter

@Serializable
class ScoreObjectPlaceholder(@SerialName("group") val groupRef: GroupReference) : AudioFlow() {
    override val canDeactivate: Boolean
        get() = false

    @Transient
    lateinit var group: GroupObject
        private set

    override fun getSuperColliderName(name: String): String = group.superColliderName

    override fun initialize(context: Context, bus: BusObject) {
        group = groupRef.resolve(context[GroupRegistry]) ?: context[GroupRegistry].getDefault()
        mutableName.set(group.name.now)
        super.initialize(context, bus)
    }

    override fun rename(newName: String) {
        context.withoutUndo {
            group.rename(newName)
        }
        super.rename(newName)
    }

    override fun copy(): AudioFlow = ScoreObjectPlaceholder(groupRef)

    override fun ScWriter.writeCode(placement: NodePlacement) {
        +"${group.superColliderName} = Group.new(${placement.target}, ${placement.addAction})"
    }

    override fun getDefaultName(): String = "Group ${groupRef.getName()}"

    override fun getInputs(): Collection<BusObject> = emptySet()

    override fun getOutputs(): Collection<BusObject> = emptySet()

    override fun addListener(listener: AudioNode.Listener) {

    }
}