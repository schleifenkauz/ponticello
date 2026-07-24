package ponticello.ui.scope

import bundles.PublicProperty
import bundles.publicProperty
import com.illposed.osc.OSCMessage
import com.illposed.osc.argument.OSCTimeTag64
import hextant.context.Context
import ponticello.impl.Logger
import ponticello.model.flow.InstrumentFlow
import ponticello.model.flow.NodePlacement
import ponticello.model.instr.BusObject
import ponticello.model.score.controls.NamedParameterControl
import ponticello.sc.NumericalControlSpec
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.getArgument
import reaktive.value.ReactiveString
import reaktive.value.ReactiveValue
import reaktive.value.binding.binding
import reaktive.value.binding.map
import reaktive.value.now

class LiveGraphs(private val context: Context) {
    private var idCounter = 0
    private val panes = mutableMapOf<Target, LiveGraphPane>()
    private val panesById = mutableMapOf<Int, LiveGraphPane>()

    private val client = context[SuperColliderClient]

    init {
        client.serverReceiver?.addListener("/control_send", ::receivedControlValue)
    }

    private fun receivedControlValue(time: OSCTimeTag64, msg: OSCMessage) {
        val id = msg.getArgument<Int>(1, "id") ?: return
        val value = msg.getArgument<Float>(2, "value") ?: return
        val pane = panesById[id] ?: return
        val t = System.currentTimeMillis() / 1000.0
        pane.liveGraph.receive(value.toDouble(), t)
    }

    fun closedGraphPane(pane: LiveGraphPane) {
        panes.remove(pane.target)
        panesById.remove(pane.id)
        client.run("ControlSends.stop_control_send(${pane.id})")
    }

    private fun showGraphPane(target: Target) {
        if (target in panes) {
            val pane = panes.getValue(target)
            pane.showWindow()
            return
        }
        val id = idCounter++
        val bus = target.getBus()
        val placement = target.getNodePlacement()
        if (bus == null || placement == null) {
            Logger.warn("Cannot create a live graph for $target", Logger.Category.OSC)
            return
        }
        client.run("ControlSends.start_control_send($bus, $id, rate: 30, lag: 0, ${placement.code})")
        val pane = LiveGraphPane(context, target, id)
        pane.showWindow()
        panes[target] = pane
        panesById[id] = pane
    }

    fun showGraphPane(bus: BusObject.ControlBus) {
        showGraphPane(Target.Bus(bus))
    }

    fun showGraphPane(control: NamedParameterControl) {
        showGraphPane(Target.Control(control))
    }

    fun setLag(id: Int, lag: Double) {
        client.run("ControlSends.set_lag($id, $lag)")
    }

    fun setRate(id: Int, rate: Double) {
        client.run("ControlSends.set_rate($id, $rate)")
    }

    sealed class Target {
        abstract val title: ReactiveString

        abstract fun getBus(): String?

        abstract fun getNodePlacement(): NodePlacement?

        abstract fun getSpec(): ReactiveValue<NumericalControlSpec?>

        data class Bus(val bus: BusObject.ControlBus) : Target() {
            override val title: ReactiveString = bus.name.map { busName -> "Bus: $busName" }

            override fun getBus(): String = bus.superColliderName

            override fun getSpec(): ReactiveValue<NumericalControlSpec?> = bus.spec

            override fun getNodePlacement(): NodePlacement = NodePlacement.tail("s.defaultGroup")
        }

        data class Control(val control: NamedParameterControl) : Target() {
            override val title: ReactiveString =
                binding(control.parentObject.name, control.name) { flowName, ctrlName ->
                    "Control: $flowName.$ctrlName"
                }

            private fun getFlowInstance(): String? {
                val flow = control.parentObject as? InstrumentFlow ?: return null
                return "AudioFlow.get('${flow.superColliderName}').instance"
            }

            override fun getBus(): String? {
                val instance = getFlowInstance() ?: return null
                return "$instance.getControlBus(${control.name.now})"
            }

            override fun getNodePlacement(): NodePlacement? {
                val instance = getFlowInstance() ?: return null
                return NodePlacement.tail("$instance.node")
            }

            override fun getSpec(): ReactiveValue<NumericalControlSpec?> =
                control.spec.map { it as? NumericalControlSpec }
        }
    }

    companion object : PublicProperty<LiveGraphs> by publicProperty("LiveGraphs")
}