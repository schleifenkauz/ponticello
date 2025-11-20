package ponticello.ui.flow

import fxutils.prompt.SimpleSelectorPrompt
import hextant.context.Context
import javafx.geometry.Point2D
import ponticello.model.flow.*
import ponticello.model.instr.*
import ponticello.model.server.BusRegistry
import ponticello.sc.Identifier
import ponticello.ui.registry.BusSelectorPrompt
import ponticello.ui.score.SoundProcessView
import reaktive.value.now

sealed interface FlowOption {
    fun createFlow(context: Context, anchor: Point2D): AudioFlow?

    fun defaultName(): String

    data object Send : FlowOption {
        override fun createFlow(context: Context, anchor: Point2D): AudioFlow? {
            val source = BusSelectorPrompt(context[BusRegistry], "Source bus")
                .showPopup(anchor) ?: return null
            val selected = BusSelectorPrompt(context[BusRegistry], "Target bus")
                .showPopup(anchor) ?: return null
            return SendFlow.create(source, selected)
        }

        override fun defaultName(): String = "send"
    }

    data object Utility : FlowOption {
        override fun createFlow(context: Context, anchor: Point2D): UtilityFlow? {
            val target = BusSelectorPrompt(context[BusRegistry], "Target bus")
                .showPopup(anchor) ?: return null
            return UtilityFlow.create(target)
        }

        override fun defaultName(): String = "utility"
    }

    data object Code : FlowOption {
        override fun createFlow(context: Context, anchor: Point2D): AudioFlow = CodeFlow.create()

        override fun defaultName(): String = "code"
    }

    data object Mixer : FlowOption {
        override fun createFlow(context: Context, anchor: Point2D): MixerFlow? {
            val out = BusSelectorPrompt(context[BusRegistry], "Output bus")
                .showPopup(anchor) ?: return null
            return MixerFlow.create(out)
        }

        override fun defaultName(): String = "mixer"
    }

    data class VSTPlugin(val pluginName: String) : FlowOption {
        override fun createFlow(context: Context, anchor: Point2D): AudioFlow? {
            val presets = VSTPlugins.globalPresetList(context, pluginName)
            val preset = if (presets.isNotEmpty()) {
                SimpleSelectorPrompt(listOf("<no preset>") + presets, "Select preset")
                    .showPopup(anchor)
                    .takeIf { it != "<no preset>" }
            } else null
            val bus = BusSelectorPrompt(context[BusRegistry], "Target bus")
                .showPopup(anchor) ?: return null
            return VSTPluginFlow.create(pluginName, preset, bus)
        }

        override fun toString(): String = "VST: $pluginName"

        override fun defaultName(): String = Identifier.truncate(pluginName)
    }

    data class Instrument(val def: InstrumentObject) : FlowOption {
        override fun createFlow(context: Context, anchor: Point2D): InstrumentFlow? {
            val controls = SoundProcessView.getInitialControls(def, context, defaultBus = null, anchor) ?: return null
            return InstrumentFlow.create(def, controls)
        }

        override fun toString(): String = when (def) {
            is SynthDefObject -> "SynthDef ${def.name.now}"
            is ProcessDefObject -> "Process ${def.name.now}"
            is VSTInstrumentObject -> "VST: ${def.name.now}"
            is NoInstrument -> throw AssertionError("NoInstrument should not be here")
        }

        override fun defaultName(): String = def.name.now
    }

    companion object {
        private val simpleOptions = listOf(Code, Send, Utility, Mixer)

        fun getOptions(context: Context): List<FlowOption> {
            val vstOptions = VSTPlugins.availablePlugins(context).map(::VSTPlugin)
            val synthOptions = context[InstrumentRegistry].map(::Instrument)
            return simpleOptions + synthOptions + vstOptions
        }
    }
}