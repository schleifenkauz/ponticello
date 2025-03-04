package xenakis.model.flow

import reaktive.value.now
import xenakis.sc.client.ScWriter
import xenakis.sc.client.SuperColliderClient

class NodeTree(private val client: SuperColliderClient) {
    private fun ScWriter.clearAudioFlow() {
        +"ServerTree.remove(~setup_flow)"
//        for (flow in flows.all()) {
//            +"${flow.uni}.free"
//            +"${flow.synthName} = nil"
//        }
    }

    private fun ScWriter.defineAudioFlow() {
        appendBlock("~setup_flow = ") {
            //setupAudioFlow()
            +"'setup audio flow'.postln"
        }
        +"ServerTree.add(~setup_flow)"
        +"if (s.serverRunning) { ~setup_flow.value }"

    }

    fun redefineAudioFlow(writer: ScWriter) {
        writer.clearAudioFlow()
        writer.defineAudioFlow()
    }

    fun moveAfter(node: ServerNode, target: ServerNode) {
        client.run {
            //+"${node.superColliderName}.moveAfter(${target.superColliderName})"
        }
    }

    fun renameNode(node: ServerNode, oldName: String) {
        val newName = "~${node.superColliderName}"
        rename("~${oldName}", newName)
        if (node is ServerNode.FlowGroup) {
            for (flow in node.flows()) {
                if (flow.isActive.now) {
                    rename("${oldName}_${flow.index}", "${newName}_${flow.index}")
                }
            }
        }
    }

    fun free(node: ServerNode) {
        client.run {
            val name = "~${node.superColliderName}"
            free(name)
            if (node is ServerNode.FlowGroup) {
                for (flow in node.flows()) {
                    free("${name}_${flow.index}")
                }
            }
        }
    }

    private fun ScWriter.free(name: String) {
        +"$name.free"
        +"$name = nil"
    }

    fun rename(oldName: String, newName: String) {
        client.run {
            +"$newName = $oldName"
            +"$oldName = nil"
        }
    }

    fun addFlow(flow: AudioFlow, placement: NodePlacement) {
        //TODO
    }

    fun removeFlow(flow: AudioFlow) {
        client.run {
            free(flow.superColliderName.now)
        }
    }
}