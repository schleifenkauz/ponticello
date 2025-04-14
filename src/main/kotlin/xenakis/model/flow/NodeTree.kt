package xenakis.model.flow

import reaktive.Observer
import reaktive.value.now
import xenakis.sc.client.SuperColliderClient

class NodeTree(private val client: SuperColliderClient) {
    private val nameObserver = mutableMapOf<AudioNode, Observer>()
    private val activeNodes = mutableSetOf<AudioNode>()

    fun addNode(node: AudioNode, placement: NodePlacement, createSynth: Boolean = true) {
        if (!node.validate()) return
        if (createSynth) {
            client.run {
                node.run { writeCode(placement) }
            }
        }
        nameObserver[node] = node.superColliderName.observe { _, old, new -> rename(old, new) }
        activeNodes.add(node)
    }

    fun removeNode(node: AudioNode, freeSynth: Boolean = true) {
        if (!(activeNodes.remove(node))) return
        val name = node.superColliderName.now
        if (freeSynth) {
            client.run {
                +"if ($name != nil && $name.isRunning) { $name.free }"
                +"$name = nil"
            }
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