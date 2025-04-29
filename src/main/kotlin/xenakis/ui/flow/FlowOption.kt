package xenakis.ui.flow

import hextant.context.Context
import javafx.event.Event
import reaktive.value.now
import xenakis.model.flow.*
import xenakis.model.obj.SynthDefObject
import xenakis.model.registry.BusRegistry
import xenakis.model.registry.SynthDefRegistry
import xenakis.ui.registry.SimpleSearchableRegistryView

sealed interface FlowOption {
    fun createFlow(context: Context, ev: Event?): AudioFlow?

    data object Send : FlowOption {
        override fun createFlow(context: Context, ev: Event?): AudioFlow? {
            val source = SimpleSearchableRegistryView(context[BusRegistry], "Source bus")
                .showPopup(ev) ?: return null
            val selected = SimpleSearchableRegistryView(context[BusRegistry], "Target bus")
                .showPopup(ev) ?: return null
            return SendFlow.create(source, selected, context)
        }
    }

    data object Utility : FlowOption {
        override fun createFlow(context: Context, ev: Event?): UtilityFlow? {
            val target = SimpleSearchableRegistryView(context[BusRegistry], "Target bus")
                .showPopup(ev) ?: return null
            return UtilityFlow.create(target)
        }
    }

    data object Code : FlowOption {
        override fun createFlow(context: Context, ev: Event?): AudioFlow = CodeFlow.create()
    }

    data object Mixer: FlowOption {
        override fun createFlow(context: Context, ev: Event?): AudioFlow = MixerFlow.create()
    }

    data class Synth(val def: SynthDefObject) : FlowOption {
        override fun createFlow(context: Context, ev: Event?): AudioFlow {
            return SynthFlow.create(def, context)
        }

        override fun toString(): String = "Synth ${def.name.now}"
    }

    companion object {
        private val simpleOptions = listOf(Code, Send, Utility, Mixer)

        fun getOptions(context: Context): List<FlowOption> = simpleOptions + context[SynthDefRegistry].map(::Synth)
    }
}