package ponticello.ui.midi

import hextant.context.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ponticello.model.flow.VSTPluginFlow
import ponticello.model.obj.VSTPluginReference
import ponticello.model.obj.project
import ponticello.model.project.flows
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.run
import reaktive.value.now

@SerialName("VSTMidiInstrument")
@Serializable
class VSTMidiInstrument(
    @SerialName("flow")
    private var _flow: VSTPluginReference,
) : MidiInstrument() { //TODO is this needed?
    var flow
        get() = _flow
        set(value) {
            _flow = value
            resolveFlow()
            context[SuperColliderClient.Companion] //TODO update
        }

    var vst: VSTPluginFlow? = null
        private set

    override fun initialize(context: Context) {
        super.initialize(context)
        resolveFlow()
    }

    override fun toString(): String {
        val flowName = flow.getName()
        return "VSTMidiInstrument <$flowName>"
    }

    private fun resolveFlow() {
        val vstFlows = context.project.flows.allFlows().filterIsInstance<VSTPluginFlow>()
        vst = flow.resolve(vstFlows)
    }

    override fun activate() {
        context[SuperColliderClient].run {
            +"~${name.now} = VSTMidiInstrument({${vst?.controllerVar}}, enabled: ${isEnabled.now})"
        }
        super.activate()
    }

    override fun copy(): MidiInstrument = VSTMidiInstrument(flow)
}