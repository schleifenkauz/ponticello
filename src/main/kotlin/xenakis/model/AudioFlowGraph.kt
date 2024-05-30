package xenakis.model

import hextant.context.Context
import hextant.core.editor.ListenerManager
import hextant.serial.EditorRoot
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.ReactiveValue
import reaktive.value.now
import xenakis.impl.Point
import xenakis.impl.SuperColliderContext
import xenakis.sc.editor.CodeBlockEditor
import java.util.*

@Serializable
class AudioFlowGraph(
    private val _nodes: MutableList<BusNode>,
    private val _flows: MutableList<AudioFlow>,
) {
    @Transient
    private lateinit var registry: BusRegistry

    @Transient
    val views = ListenerManager.createWeakListenerManager<View>()

    fun initialize(context: Context) {
        registry = context[BusRegistry]
        for (node in nodes) {
            node.busName = registry.getBus(node.busName.now).name
        }
        for (flow in flows) {
            flow.source = nodes.find { n -> n.busName.now == flow.source.busName.now }!!
            flow.target = nodes.find { n -> n.busName.now == flow.target.busName.now }!!
        }
    }

    val flows: List<AudioFlow> get() = _flows
    val nodes: List<BusNode> get() = _nodes

    private fun edges(source: BusNode) = flows.filter { f -> f.source == source }

    @Transient
    var order = findFlowOrder() ?: error("audio flow graph contains cycles")
        private set

    fun addFlow(flow: AudioFlow): Boolean {
        if (_flows.any { f -> f.source == flow.source && f.target == flow.target }) return false
        _flows.add(flow)
        val newOrder = findFlowOrder()
        if (newOrder == null) {
            removeFlow(flow)
            return false
        }
        order = newOrder
        views.notifyListeners { addedFlow(flow) }
        return true
    }

    fun removeFlow(flow: AudioFlow) {
        _flows.remove(flow)
        views.notifyListeners { removedFlow(flow) }
    }

    fun add(bus: BusObject, position: Point): Boolean {
        if (nodes.any { n -> n.busName.now == bus.name.now }) {
            return false
        }
        val obj = BusNode(bus.name, position)
        _nodes.add(obj)
        views.notifyListeners { addedNode(obj) }
        return true
    }

    fun move(node: BusNode, position: Point) {
        node.position = position
        views.notifyListeners { movedNode(node) }
    }

    fun remove(node: BusNode) {
        _nodes.remove(node)
        views.notifyListeners { removedNode(node) }
        for (flow in associatedFlows(node)) removeFlow(flow)
    }

    fun associatedFlows(bus: BusNode) =
        flows.filter { f -> f.source == bus || f.target == bus }

    private fun findFlowOrder(): List<AudioFlow>? {
        val q: Queue<AudioFlow> = LinkedList(flows)
        val order = mutableListOf<AudioFlow>()
        val dependencies = mutableMapOf<BusNode, Int>()
        for (flow in flows) dependencies[flow.target] = (dependencies[flow.target] ?: 0) + 1
        while (q.isNotEmpty()) {
            val flow = q.poll()
            if ((dependencies[flow.source] ?: 0) == 0) {
                order.add(flow)
                for (e in edges(flow.target)) q.offer(e)
            }
        }
        return if (order.size == flows.size) order else null
    }

    fun SuperColliderContext.setupAudioFlow() = run {
        var prev = "s.defaultGroup"
        for (flow in order) {
            val source = registry.getBus(flow.source.busName.now)
            val target = registry.getBus(flow.target.busName.now)
            val ugenGraph = flow.ugenGraph.editor.result.now
            val synthName = "~flow_${source.name}_${target.name}"
            appendLine("{")
            +"var sig = In.${source.rate}(${source.variableName}, ${source.channels})"
            ugenGraph.writeCode(this)
            val addAction = if (prev == "s.defaultGroup") "addToTail" else "addAfter"
            +"}.play($prev, ${target.variableName}, addAction: '$addAction')"
            prev = synthName
        }
    }

    @Serializable
    data class BusNode(var busName: ReactiveValue<String>, var position: Point) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as BusNode

            if (busName.now != other.busName.now) return false
            if (position != other.position) return false

            return true
        }

        override fun hashCode(): Int {
            var result = busName.now.hashCode()
            result = 31 * result + position.hashCode()
            return result
        }
    }

    @Serializable
    class AudioFlow(var source: BusNode, var target: BusNode, val ugenGraph: EditorRoot<CodeBlockEditor>) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as AudioFlow

            if (source != other.source) return false
            if (target != other.target) return false

            return true
        }

        override fun hashCode(): Int {
            var result = source.hashCode()
            result = 31 * result + target.hashCode()
            return result
        }
    }

    companion object {
        fun createDefault(): AudioFlowGraph = AudioFlowGraph(mutableListOf(), mutableListOf())
    }

    interface View {
        fun addedNode(node: BusNode)

        fun removedNode(node: BusNode)

        fun addedFlow(flow: AudioFlow)

        fun removedFlow(flow: AudioFlow)

        fun movedNode(node: BusNode)
    }
}