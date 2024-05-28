package xenakis.model

import hextant.serial.EditorRoot
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.now
import xenakis.impl.SuperColliderContext
import xenakis.sc.Bus
import xenakis.sc.editor.CodeBlockEditor
import java.util.*

@Serializable
class AudioFlowGraph(
    private val _busses: MutableSet<BusObject>,
    private val _flows: MutableSet<AudioFlow>,
) {
    @Transient
    private val edges = mutableMapOf<BusObject, MutableSet<AudioFlow>>()

    val flows: Set<AudioFlow> get() = _flows
    val busses: Set<BusObject> get() = _busses

    private fun edges(source: BusObject) = edges.getOrPut(source) { mutableSetOf() }

    init {
        for (flow in flows) {
            edges(flow.source).add(flow)
        }
    }

    @Transient
    var order = findFlowOrder() ?: error("audio flow graph contains cycles")
        private set

    fun addFlow(flow: AudioFlow): Boolean {
        _flows.add(flow)
        edges(flow.source).add(flow)
        val newOrder = findFlowOrder()
        if (newOrder == null) {
            removeFlow(flow)
            return false
        }
        order = newOrder
        return true
    }

    fun removeFlow(flow: AudioFlow) {
        _flows.remove(flow)
        edges(flow.source).remove(flow)
    }

    fun addBus(bus: BusObject, context: SuperColliderContext) {
        _busses.add(bus)
        context.postAsync(bus.bus.allocationCode)
    }

    fun removeBus(bus: BusObject) {
        _busses.remove(bus)
        edges.remove(bus)
    }

    fun associatedFlows(bus: BusObject) =
        flows.filter { f -> f.source == bus || f.target == bus }

    private fun findFlowOrder(): List<AudioFlow>? {
        val q: Queue<AudioFlow> = LinkedList(flows)
        val order = mutableListOf<AudioFlow>()
        val dependencies = mutableMapOf<BusObject, Int>()
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

    fun allocateBusses(context: SuperColliderContext) = context.postAsync {
        for ((bus) in busses) {
            if (bus.name != "output") +bus.allocationCode
        }
    }

    fun setupAudioFlow(context: SuperColliderContext) = context.postAsync {
        var prev = "s.defaultGroup"
        for (flow in order) {
            val source = flow.source.bus
            val target = flow.target.bus
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

    fun getBus(busName: String): Bus = busses.find { b -> b.bus.name == busName }?.bus
        ?: error("no bus with name '$busName' found in audio flow graph")

    @Serializable
    data class BusObject(val bus: Bus, var x: Double, var y: Double)

    @Serializable
    class AudioFlow(val source: BusObject, val target: BusObject, val ugenGraph: EditorRoot<CodeBlockEditor>)

    companion object {
        fun createDefault(): AudioFlowGraph {
            val defaultGroup = Bus.output
            val defaultBusObject = BusObject(defaultGroup, 0.5, 1.0)
            return AudioFlowGraph(mutableSetOf(defaultBusObject), mutableSetOf())
        }
    }
}