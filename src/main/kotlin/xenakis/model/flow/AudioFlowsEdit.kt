package xenakis.model.flow

import hextant.undo.AbstractEdit
import xenakis.impl.Point
import xenakis.model.obj.BusObject

sealed class AudioFlowsEdit(protected val flows: AudioFlows) : AbstractEdit() {
    class AddFlow(graph: AudioFlows, private val flow: AudioFlow) : AudioFlowsEdit(graph) {
        override val actionDescription: String
            get() = "Add audio flow"

        override fun doRedo() {
            flows.addFlow(flow)
        }

        override fun doUndo() {
            flows.removeFlow(flow)
        }
    }

    class RemoveFlow(flows: AudioFlows, private val flow: AudioFlow) : AudioFlowsEdit(flows) {
        override val actionDescription: String
            get() = "Remove audio flow"

        override fun doRedo() {
            flows.removeFlow(flow)
        }

        override fun doUndo() {
            flows.addFlow(flow)
        }
    }

    class MoveFlow(
        flows: AudioFlows,
        private val flow: AudioFlow,
        private val fromIndex: Int,
        private val toIndex: Int
    ) : AudioFlowsEdit(flows) {
        override val actionDescription: String
            get() = "Reorder flows"

        override fun doUndo() {
            flows.moveFlow(flow, fromIndex)
        }

        override fun doRedo() {
            flows.moveFlow(flow, toIndex)
        }
    }

    class MoveBusNode(
        flows: AudioFlows,
        private val node: BusObject,
        private val fromPosition: Point,
        private val toPosition: Point
    ) : AudioFlowsEdit(flows) {
        override val actionDescription: String
            get() = "Move Flow"

        override fun doRedo() {
            flows.move(node, toPosition)
        }

        override fun doUndo() {
            flows.move(node, fromPosition)
        }

        override fun mergeWith(other: hextant.undo.Edit): hextant.undo.Edit? =
            if (other is MoveBusNode && other.node == this.node && other.flows == this.flows)
                MoveBusNode(flows, node, this.fromPosition, other.toPosition)
            else null
    }
}