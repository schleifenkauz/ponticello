package ponticello.model.obj

import hextant.context.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ponticello.model.flow.VSTPluginFlow
import ponticello.model.project.PonticelloProject
import ponticello.model.project.flows
import ponticello.model.project.instruments
import ponticello.model.registry.reference

@Serializable
sealed interface MidiInstrument {
    fun getName(): String

    @Serializable
    @SerialName("SynthDef")
    data class SynthDef(val reference: SynthDefReference) : MidiInstrument {
        override fun getName(): String = reference.getName()
    }

    @Serializable
    @SerialName("VST")
    data class VST(val flow: VSTPluginReference) : MidiInstrument {
        override fun getName(): String = flow.getName()
    }

    @Serializable
    @SerialName("None")
    data object None : MidiInstrument {
        override fun getName(): String = "None"
    }

    fun resolve(context: Context) {
        when (this) {
            is SynthDef -> reference.resolve(context.project.instruments.filterIsInstance<SynthDefObject>())
            is VST -> flow.resolve(context.project.flows.allFlows().filterIsInstance<VSTPluginFlow>())
            None -> {}
        }
    }

    companion object {
        fun getOptions(project: PonticelloProject): List<MidiInstrument> {
            val synthDefs = project.instruments
                .filterIsInstance<SynthDefObject>()
                .filter { def -> def.hasParameter("freq") }
                .map { def -> SynthDef(def.reference()) }
            val midiInstruments = project.flows.allFlows()
                .filterIsInstance<VSTPluginFlow>()
                .filter { flow -> flow.supportsMidiInput }
                .map { flow -> VST(flow.reference()) }
            return synthDefs + midiInstruments
        }
    }
}