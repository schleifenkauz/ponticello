package xenakis.model.flow

import hextant.context.Context
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveString
import reaktive.value.binding.map
import xenakis.model.obj.BusObject
import xenakis.model.obj.GroupObject
import xenakis.model.registry.BusRegistry
import xenakis.model.registry.ObjectReference
import xenakis.sc.client.ScWriter

@Serializable
class ScoreObjectPlaceholder(
    private val busRef: ObjectReference, val group: GroupObject
) : AudioFlow() {
    override lateinit var associatedBus: BusObject
        private set
    override lateinit var superColliderName: ReactiveString
        private set

    override val canDeactivate: Boolean
        get() = false

    override fun initialize(context: Context) {
        super.initialize(context)
        busRef.resolve(context[BusRegistry])
        associatedBus = busRef.get()
        superColliderName = group.name.map { n -> "~placeholder_$n" }
    }

    override fun copyFor(associatedBus: BusObject): AudioFlow = ScoreObjectPlaceholder(busRef, group)

    override fun ScWriter.writeCode(placement: NodePlacement) {
        //+"$superColliderName = Group.new(${order.target!!}, ${order.addAction!!})" TODO move group to right place
    }

    override fun getConnectedBusses(vararg flowType: FlowType): Set<BusObject> = emptySet()
}