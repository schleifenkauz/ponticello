package ponticello.model.tree

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import com.illposed.osc.OSCMessage
import hextant.context.Context
import hextant.context.withoutUndo
import ponticello.impl.Logger
import ponticello.impl.toDecimal
import ponticello.model.flow.AudioFlows
import ponticello.model.registry.ObjectList
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.score.ObjectPosition
import ponticello.model.score.SoundProcess
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.getArgument

class AudioNodeTree(override val objects: MutableList<AudioNode> = mutableListOf()) : ObjectList<AudioNode>() {
    override val objectType: String
        get() = "AudioNode"

    private val byID = mutableMapOf<Int, AudioNode>()

    override fun initialize(context: Context) {
        context[AudioNodeTree] = this
        super.initialize(context)
        context[SuperColliderClient].addListener { ev ->
            context.withoutUndo {
                when (ev.message.address) {
                    "/inserted_flow_group" -> insertedFlowGroup(ev.message)
                    "/started_sound_proc" -> startedSoundProcessInstance(ev.message)
                    "/moved_node" -> movedNode(ev.message)
                    "/removed_node" -> removedNode(ev.message)
                    "/cleared_node_tree" -> clear()
                }
            }
        }
    }

    override fun onRemoved(obj: AudioNode, idx: Int) {
        if (obj.nodeID !in byID) {
            Logger.warn("Could not find AudioNode with id ${obj.nodeID}", Logger.Category.OSC)
            return
        }
        byID.remove(obj.nodeID)
    }

    override fun onAdded(obj: AudioNode, idx: Int) {
        if (obj.nodeID in byID) {
            Logger.warn("Duplicate nodeID: ${obj.nodeID}", Logger.Category.OSC)
            return
        }
        byID[obj.nodeID] = obj
    }

    private fun getNode(nodeID: Int, messageAddress: String): AudioNode? {
        val node = byID[nodeID]
        if (node == null) {
            Logger.warn("Unknown nodeID: $nodeID in message $messageAddress", Logger.Category.OSC)
        }
        return node
    }

    private fun movedNode(message: OSCMessage) {
        val nodeID = message.getArgument<Int>(0, "nodeID") ?: return
        val newScoreY = message.getArgument<Float>(1, "score_y") ?: return
        val node = getNode(nodeID, "/moved_node") ?: return
        val newIdx = binarySearchBy(newScoreY, selector = AudioNode::scoreY)
        move(node, newIdx)
        if (node is AudioNode.FlowGroup) {
            node.scoreY = newScoreY
        }
    }

    private fun removedNode(message: OSCMessage) {
        val id = message.getArgument<Int>(0, "nodeID") ?: return
        val node = getNode(id, "/removed_node") ?: return
        remove(node)
    }

    private fun insert(node: AudioNode) {
        var idx = binarySearchBy(node.scoreY, selector = AudioNode::scoreY)
        if (idx < 0) idx = -idx - 1
        add(node, idx)
    }

    private fun startedSoundProcessInstance(message: OSCMessage) {
        val nodeID = message.getArgument<Int>(0, "nodeID") ?: return
        val name = message.getArgument<String>(1, "process name") ?: return
        val process = context[ScoreObjectRegistry].getOrNull(name) ?: return
        if (process !is SoundProcess) {
            Logger.warn(
                "Received /inserted_instance message: Process '$name' is not a sound process.",
                Logger.Category.OSC
            )
            return
        }
        val scoreTime = message.getArgument<Float>(2, "score time") ?: return
        val scoreY = message.getArgument<Float>(3, "score y") ?: return
        val position = ObjectPosition(scoreTime.toDecimal(), scoreY.toDecimal())
        val node = AudioNode.SoundProcessInstance(nodeID, process, position)
        insert(node)
    }

    private fun insertedFlowGroup(msg: OSCMessage) {
        val nodeID = msg.getArgument<Int>(0, "nodeID") ?: return
        val name = msg.getArgument<String>(1, "name") ?: return
        val scoreY = msg.getArgument<Float>(2, "score_y") ?: return
        val group = context[AudioFlows].getOrNull(name)
        if (group == null) {
            Logger.warn(
                "Received inserted_flow_group message: Flow group '$name' not found.",
                Logger.Category.OSC
            )
            return
        }
        val node = AudioNode.FlowGroup(nodeID, group, scoreY)
        insert(node)
    }

    companion object : PublicProperty<AudioNodeTree> by publicProperty("AudioNodeTree")
}