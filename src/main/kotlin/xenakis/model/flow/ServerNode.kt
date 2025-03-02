package xenakis.model.flow

import reaktive.value.now
import xenakis.model.obj.BusObject
import xenakis.model.score.ObjectPosition
import xenakis.model.score.SynthObject

sealed class ServerNode : Comparable<ServerNode>, Flow {
    abstract val superColliderName: String

    data class FlowGroup(
        val associatedBus: BusObject,
        private val flows: AudioFlows
    ) : ServerNode() {
        override val superColliderName: String get() = "~flows_${associatedBus.name.now}"

        override fun compareTo(other: ServerNode): Int = when (other) {
            is FlowGroup -> 0
            is ActiveSynth -> +1
        }

        override fun getConnectedBusses(vararg flowType: FlowType): Set<BusObject> {
            val busses = flows().flatMapTo(mutableSetOf()) { f -> f.getConnectedBusses(*flowType) }
            if (FlowType.InOut in flowType) busses.add(associatedBus)
            else busses.remove(associatedBus)
            return busses
        }

        fun flows() = flows.associatedFlows(associatedBus)
    }

    data class ActiveSynth(
        val obj: SynthObject,
        val absolutePosition: ObjectPosition,
        val suffix: Int
    ) : ServerNode() {
        override val superColliderName = when (suffix) {
            0 -> "~${obj.name.now}"
            else -> "~${obj.name.now}_$suffix"
        }

        override fun getConnectedBusses(vararg flowType: FlowType): Set<BusObject> =
            obj.getConnectedBusses(*flowType)

        override fun compareTo(other: ServerNode): Int = when (other) {
            is ActiveSynth -> absolutePosition.y.compareTo(other.absolutePosition.y)
            is FlowGroup -> -1
        }
    }
}