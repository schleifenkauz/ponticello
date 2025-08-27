package ponticello.model.flow

import hextant.context.Context
import javafx.scene.paint.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.ColorSerializer
import ponticello.impl.Decimal
import ponticello.model.obj.AbstractRenamableObject
import ponticello.model.obj.withName
import ponticello.model.registry.NamedObjectList
import ponticello.model.registry.ObjectList
import ponticello.model.registry.ObjectListSerializer
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.eval
import reaktive.Observer
import reaktive.value.*
import reaktive.value.binding.map

@Serializable
class AudioFlowGroup(
    private val active: ReactiveVariable<Boolean> = reactiveVariable(true),
    override val yPosition: ReactiveVariable<Decimal>,
    val associatedColor: ReactiveVariable<@Serializable(with = ColorSerializer::class) Color>,
    val flows: AudioFlowList,
) : AudioNode, AbstractRenamableObject(), ObjectList.Listener<AudioFlow> {
    @SerialName("name")
    override var _name: ReactiveVariable<String>? = null

    override lateinit var superColliderName: ReactiveString
        private set

    val isActive: ReactiveBoolean get() = active

    override val isStillActive: Boolean
        get() = isActive.now

    override val startedAt: Decimal
        get() = Decimal.NINF

    @Transient
    private val observers = mutableMapOf<AudioFlow, Observer>()

    @Transient
    private lateinit var client: SuperColliderClient

    private val nodeTree: NodeTree get() = context[NodeTree]

    override val registry: AudioFlows
        get() = context[AudioFlows]

    override fun initialize(context: Context) {
        super.initialize(context)
        superColliderName = name.map { name -> "~flows_$name" }
        client = context[SuperColliderClient]
        flows.initialize(context, this)
        flows.addListener(this, initialize = false)
        for (flow in flows) observeFlow(flow)
    }

    fun toggleActive() {
        active.now = !active.now
        if (!active.now) freeGroup()
        else createFlows()
    }

    override fun added(obj: AudioFlow, idx: Int) {
        val placement = when {
            idx == 0 -> NodePlacement.head(superColliderName.now)
            else -> NodePlacement.after(flows[idx - 1].superColliderName)
        }
        obj.addToServer(placement)
        observeFlow(obj)
    }

    private fun observeFlow(obj: AudioFlow) {
        observers[obj] = obj.isActive.observe { _, _, active ->
            if (!this.isActive.now) return@observe
            obj.setRunning(active)
        }
    }

    override fun removed(obj: AudioFlow) {
        if (isActive.now && obj.isActive.now) obj.removeFromServer()
        observers.remove(obj)?.kill()
    }

    private fun previousActiveFlow(idx: Int) = flows.take(idx).findLast { it.isActive.now }

    override fun moved(obj: AudioFlow, idx: Int) {
        if (!obj.isActive.now) return
        val name = obj.superColliderName
        val prev = previousActiveFlow(idx)
        if (prev == null) {
            val groupName = superColliderName.now
            client.run("$name.moveToHead($groupName)")
        } else {
            client.run("$name.moveAfter(${prev.superColliderName})")
        }
    }

    fun getPlacement(flow: AudioFlow): NodePlacement {
        val idx = flows.indexOf(flow)
        return when (val prev = previousActiveFlow(idx)) {
            null -> NodePlacement.head(superColliderName.now)
            else -> NodePlacement.after(prev.superColliderName)
        }
    }

    private fun addToServer(placement: NodePlacement) {
        val groupName = superColliderName.now
        client.eval(description = "creating group $groupName") {
            +"$groupName = Group.new(${placement.target}, ${placement.addAction})"
        }.join()
        for (flow in flows) {
            //join enforces that the synths are added in the right order
            flow.addToServer(placement = NodePlacement.tail(groupName)).join()
        }
    }

    fun createFlows() {
        val placement = nodeTree.addNode(this)
        addToServer(placement)
    }

    override fun onAdded() {
        if (isActive.now) createFlows()
    }

    override fun onRemoved() {
        super.onRemoved()
        if (isActive.now) {
            freeGroup()
        }
    }

    private fun freeGroup() {
        context[NodeTree].removeNode(this)
        client.run("${superColliderName.now}.free")
    }

    fun sync() {
        if (isActive.now) {
            val placement = NodePlacement.replace(superColliderName.now)
            addToServer(placement)
        }
    }

    @Serializable
    @SerialName("AudioFlowList")
    class AudioFlowList(
        override val objects: MutableList<AudioFlow> = mutableListOf(),
    ) : NamedObjectList<AudioFlow>() {
        override val objectType: String
            get() = "Flow"

        @Transient
        private lateinit var parentGroup: AudioFlowGroup

        fun initialize(context: Context, parent: AudioFlowGroup) {
            this.parentGroup = parent
            super.initialize(context)
        }

        override fun initializeObject(obj: AudioFlow) {
            obj.setParentGroup(parentGroup)
            if (!obj.initialized) {
                obj.initialize(context)
            }
        }

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