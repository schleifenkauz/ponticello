package ponticello.model.flow

import hextant.context.Context
import javafx.scene.paint.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.ColorSerializer
import ponticello.impl.Decimal
import ponticello.model.obj.AbstractRenamableObject
import ponticello.model.obj.SuperColliderObject
import ponticello.model.obj.withName
import ponticello.model.registry.ObjectList
import ponticello.model.registry.ObjectListSerializer
import ponticello.model.registry.SuperColliderObjectRegistry
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.eval
import ponticello.sc.client.run
import reaktive.Observer
import reaktive.value.ReactiveBoolean
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
class AudioFlowGroup(
    private val active: ReactiveVariable<Boolean> = reactiveVariable(true),
    val yPosition: ReactiveVariable<Decimal>,
    val associatedColor: ReactiveVariable<@Serializable(with = ColorSerializer::class) Color>,
    val flows: AudioFlowList,
) : AbstractRenamableObject(), ObjectList.Listener<AudioFlow> {
    @SerialName("name")
    override var _name: ReactiveVariable<String>? = null

    private val audioNodeName get() = "~flows_${name.now}"
    private val groupName get() = "${audioNodeName}.node"

    val isActive: ReactiveBoolean get() = active

    @Transient
    private lateinit var client: SuperColliderClient

    @Transient
    private lateinit var yObserver: Observer

    override val registry: AudioFlows
        get() = context[AudioFlows]

    override fun initialize(context: Context) {
        super.initialize(context)
        client = context[SuperColliderClient]
        flows.initialize(context)
        flows.addListener(this, initialize = false)
        for (flow in flows) {
            flow.parentGroup = this
        }
        yObserver = yPosition.observe { _, _, newY ->
            client.run {
                +"AudioNodeOrder.move($audioNodeName, $newY)"
            }
        }
    }

    fun toggleActive() {
        active.now = !active.now
        if (!active.now) freeGroup()
        else createGroupOnServer()
    }

    override fun added(obj: AudioFlow, idx: Int) {
        val placement = when {
            idx == 0 -> NodePlacement.head(groupName)
            else -> NodePlacement.after(flows[idx - 1].superColliderName)
        }
        if (obj.parentGroup == this) {
            client.run("${obj.superColliderName}.${placement.moveMethod}(${placement.target})")
        } else {
            obj.addToServer(placement)
        }
        obj.parentGroup = this
    }

    override fun removed(obj: AudioFlow, idx: Int) {
        if (isActive.now && obj.parentGroup == this) obj.removeFromServer()
    }

    private fun previousActiveFlow(idx: Int) = flows.take(idx).findLast { it.isActive.now }

    override fun moved(obj: AudioFlow, idx: Int) {
        if (!obj.isActive.now) return
        val name = obj.superColliderName
        val prev = previousActiveFlow(idx)
        if (prev == null) {
            client.run("$name.moveToHead($groupName)")
        } else {
            client.run("$name.moveAfter(${prev.superColliderName})")
        }
    }

    fun getPlacement(flow: AudioFlow): NodePlacement {
        val idx = flows.indexOf(flow)
        return when (val prev = previousActiveFlow(idx)) {
            null -> NodePlacement.head(groupName)
            else -> NodePlacement.after(prev.superColliderName)
        }
    }

    private fun createFlows() {
        for (flow in flows) {
            //join enforces that the synths are added in the right order
            flow.addToServer(placement = NodePlacement.tail(groupName)).join()
        }
    }

    fun createGroupOnServer() {
        val scoreY = yPosition.now
        client.eval(description = "creating group $audioNodeName") {
            +"$audioNodeName = AudioNodeOrder.insertGroup(score_y: $scoreY)"
        }.join()
        createFlows()
    }

    override fun activate() {
        if (isActive.now) createGroupOnServer()
    }

    override fun onRemoved() {
        super.onRemoved()
        if (isActive.now) freeGroup()
    }

    private fun freeGroup() {
        client.run("$groupName.free")
    }

    fun sync() {
        if (isActive.now) {
            client.eval(description = "syncing group $audioNodeName") {
                +"$groupName.free"
            }
            createFlows()
        }
    }

    @Serializable
    @SerialName("AudioFlowList")
    class AudioFlowList(
        override val objects: MutableList<AudioFlow> = mutableListOf(),
    ) : SuperColliderObjectRegistry<AudioFlow>() {
        override val objectType: String
            get() = "Flow"

        override val liveCycleType: SuperColliderObject.LiveCycleType
            get() = SuperColliderObject.LiveCycleType.ServerBoot

        object Serializer : ObjectListSerializer<AudioFlow, AudioFlowList>(AudioFlow.serializer(), ::AudioFlowList)
    }

    companion object {
        fun create(name: String, y: Decimal, color: Color) = AudioFlowGroup(
            active = reactiveVariable(true),
            reactiveVariable(y),
            reactiveVariable(color),
            flows = AudioFlowList()
        ).withName(name)
    }
}