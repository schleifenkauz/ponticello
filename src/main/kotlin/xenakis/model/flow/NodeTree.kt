package xenakis.model.flow

import bundles.PublicProperty
import bundles.publicProperty
import reaktive.Observer
import reaktive.value.now
import xenakis.impl.Decimal
import xenakis.impl.Logger
import xenakis.impl.zero
import xenakis.sc.client.SuperColliderClient

class NodeTree(private val client: SuperColliderClient) {
    private val observers = mutableMapOf<AudioNode, Observer>()
    private val activeNodes = mutableListOf<AudioNode>()

    fun addNode(node: AudioNode): NodePlacement {
        var idx = activeNodes.binarySearch(node)
        if (idx >= 0) {
            if (activeNodes[idx] == node) {
                removeNode(node)
                Logger.warn("Node $node already in node tree", Logger.Category.Playback)
            }
        } else {
            idx = -(idx + 1)
        }
        while (idx >= 1) {
            val prevNode = activeNodes[idx]
            when {
                !prevNode.isStillActive -> idx--
                prevNode.yPosition.now >= node.yPosition.now -> idx--
                prevNode.startedAt >= (prevNode.player?.currentTime ?: zero) -> idx--
                else -> break
            }
        }
        activeNodes.add(idx, node)
        observeNode(node)
        return if (idx == 0) NodePlacement.head("s.defaultGroup")
        else NodePlacement.after(activeNodes[idx - 1].superColliderName.now)
    }

    private fun observeNode(node: AudioNode) {
        observers[node] = node.yPosition.observe { _, _, newY ->
            changedYPosition(node, newY)
        }
    }

    private fun changedYPosition(node: AudioNode, newY: Decimal) {
        val oldIndex = activeNodes.indexOf(node)
        if (oldIndex < 0) {
            Logger.warn("Node $node not found in node tree", Logger.Category.Playback)
            return
        }
        val belowPreviousNode = oldIndex == 0 || activeNodes[oldIndex - 1].yPosition.now < newY
        val aboveNextNode = oldIndex == activeNodes.lastIndex || activeNodes[oldIndex + 1].yPosition.now > newY
        if (belowPreviousNode && aboveNextNode) return
        activeNodes.removeAt(oldIndex)
        var newIndex = activeNodes.binarySearchBy(newY) { n -> n.yPosition.now  }
        newIndex = -(newIndex + 1)
        activeNodes.add(newIndex, node)
        if (newIndex == oldIndex) return
        if (newIndex == 0) {
            client.run("${node.superColliderName.now}.moveToHead(s.defaultGroup)")
        } else {
            val prevName = activeNodes[newIndex - 1].superColliderName.now
            client.run("${node.superColliderName.now}.moveAfter($prevName)")
        }
    }

    fun removeNode(node: AudioNode) {
        if (!(activeNodes.remove(node))) {
            Logger.warn("Node $node not found in node tree", Logger.Category.Playback)
            return
        }
        observers.remove(node)!!.kill()
    }

    fun clear() {
        for (node in activeNodes) {
            observers.remove(node)!!.kill()
        }
        activeNodes.clear()
    }

    companion object: PublicProperty<NodeTree> by publicProperty("NodeTree")
}