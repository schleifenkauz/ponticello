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

    override fun initialize(context: Context) {
        context[AudioNodeTree] = this
        super.initialize(context)
        context[SuperColliderClient].addListener { ev ->
            context.withoutUndo {
                when (ev.message.address) {
                    "/inserted_flow_group" -> insertedFlowGroup(ev.message)
                    "/inserted_instance" -> insertedSoundProcessInstance(ev.message)
                    "/moved_node" -> movedNode(ev.message)
                    "/removed_node" -> removedNode(ev.message)
                    "/cleared_node_tree" -> clear()
                }
            }
        }
    }

    private fun movedNode(message: OSCMessage) {
        val oldIdx = message.getArgument<Int>(0, "old index") ?: return
        val newIdx = message.getArgument<Int>(1, "new index") ?: return
        val node = get(oldIdx)
        move(node, newIdx)
    }

    private fun removedNode(message: OSCMessage) {
        val idx = message.getArgument<Int>(0, "index") ?: return
        val node = get(idx)
        remove(node)
    }

    private fun insertedSoundProcessInstance(message: OSCMessage) {
        val idx = message.getArgument<Int>(0, "index") ?: return
        val name = message.getArgument<String>(1, "process name") ?: return
        val process = context[ScoreObjectRegistry].getOrNull(name) ?: return
        if (process !is SoundProcess) {
            Logger.warn(
                "Received /inserted_instance message: Process '$name' is not a sound process.",
                Logger.Category.OSC
            )
            return
        }
        val scoreTime = message.arguments[2] as? Float
        val scoreY = message.arguments[3] as? Float
        val position = if (scoreTime != null && scoreY != null) {
            ObjectPosition(scoreTime.toDecimal(), scoreY.toDecimal())
        } else null
        add(AudioNode.SoundProcessInstance(process, position), idx)
    }

    private fun insertedFlowGroup(msg: OSCMessage) {
        val idx = msg.getArgument<Int>(0, "index") ?: return
        val name = msg.getArgument<String>(1, "name") ?: return
        val group = context[AudioFlows].getOrNull(name)
        if (group == null) {
            Logger.warn(
                "Received inserted_flow_group message: Flow group '$name' not found.",
                Logger.Category.OSC
            )
            return
        }
        add(AudioNode.FlowGroup(group), idx)
    }

    companion object : PublicProperty<AudioNodeTree> by publicProperty("AudioNodeTree")
}