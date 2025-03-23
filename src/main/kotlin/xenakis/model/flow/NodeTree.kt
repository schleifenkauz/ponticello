package xenakis.model.flow

import reaktive.Observer
import reaktive.value.now
import xenakis.sc.client.SuperColliderClient

class NodeTree(private val client: SuperColliderClient) {
    private val nameObserver = mutableMapOf<AudioNode, Observer>()
    private val activeNodes = mutableSetOf<AudioNode>()

    fun addNode(node: AudioNode, placement: NodePlacement) {
        client.run {
            node.run { writeCode(placement) }
        }
        nameObserver[node] = node.superColliderName.observe { _, old, new -> rename(old, new) }
        activeNodes.add(node)
    }

    fun removeNode(node: AudioNode) {
        if (!(activeNodes.remove(node))) return
        client.run {
            +"${node.superColliderName.now}.free"
            +"${node.superColliderName.now} = nil"
        }
        nameObserver.remove(node)!!.kill()
    }

    fun isActive(node: AudioNode) = node in activeNodes

    fun moveAfter(target: AudioNode, node: AudioNode) {
        client.run {
            +"${node.superColliderName.now}.moveAfter(${target.superColliderName.now})"
        }
    }

    fun moveToHead(group: AudioNode, flow: AudioNode) {
        client.run {
            +"${flow.superColliderName.now}.moveToHead(${group.superColliderName.now})"
        }
    }

    private fun rename(oldName: String, newName: String) {
        client.run {
            +"$newName = $oldName"
            +"$oldName = nil"
        }
    }
}