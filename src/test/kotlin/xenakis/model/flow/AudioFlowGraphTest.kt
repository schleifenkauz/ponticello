package xenakis.model.flow

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import xenakis.model.Utils
import xenakis.model.obj.BusObject
import xenakis.model.obj.CustomizableSynthDefObject
import xenakis.model.registry.BusRegistry
import xenakis.model.score.BusControl
import xenakis.model.score.ObjectPosition
import xenakis.model.score.SynthObject

class AudioFlowGraphTest {
    @Test
    fun test() {
        val context = Utils.createContext()
        val buses = context[BusRegistry]
        val flows = AudioFlows(mutableMapOf())
        flows.initialize(context)
        val nodeTree = mockk<NodeTree>(relaxed = true)
        val graph = AudioFlowGraph(flows, nodeTree)
        val perc = BusObject.audio("perc")
        buses.add(perc)
        flows.addSendFlow(buses.getInput(), perc)
        flows.addSendFlow(perc, buses.getOutput())
        val expectedOrder = listOf(buses.getInput(), perc, buses.getOutput())

        assertEquals(expectedOrder.map(graph::flowGroup), graph.getOrder())

        val sine = SynthObject.create("sine", CustomizableSynthDefObject.sine())
        sine.initialize(context)
        sine.controls.addControl("out", BusControl.create(perc))

        val info1 = graph.insert(sine, ObjectPosition.ZERO)
        println(graph.getOrder())
        println(info1)

        val lpf = SynthObject.create("lpf", CustomizableSynthDefObject.lpf())
        lpf.initialize(context)
        lpf.controls.addControl("bus", BusControl.create(buses.getInput()))
        val info2 = graph.insert(lpf, ObjectPosition.ZERO)
        println(graph.getOrder())
        println(info2)
    }
}