package ponticello.ui.flow

import hextant.context.Context
import javafx.event.Event
import ponticello.model.flow.*
import ponticello.model.obj.ProcessDefObject
import ponticello.model.obj.SynthDefObject
import ponticello.model.registry.BusRegistry
import ponticello.model.registry.SynthDefRegistry
import ponticello.ui.registry.SimpleSearchableRegistryView
import reaktive.value.now

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
        override fun createFlow(context: Context, ev: Event?): AudioFlow = SynthFlow.create(def, context)

        override fun toString(): String = "Synth ${def.name.now}"
    }

    data class Process(val def: ProcessDefObject): FlowOption {
        override fun createFlow(context: Context, ev: Event?): AudioFlow = ProcessFlow.create(def, context)

        override fun toString(): String = "Process ${def.name.now}"
    }

    companion object {
        private val simpleOptions = listOf(Code, Send, Utility, Mixer)

        fun getOptions(context: Context): List<FlowOption> = simpleOptions + context[SynthDefRegistry].map(::Synth)
    }
}