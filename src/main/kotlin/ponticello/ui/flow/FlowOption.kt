package ponticello.ui.flow

import fxutils.prompt.SimpleSearchableListView
import hextant.context.Context
import javafx.geometry.Point2D
import ponticello.model.flow.*
import ponticello.model.obj.InstrumentObject
import ponticello.model.obj.NoInstrument
import ponticello.model.obj.ProcessDefObject
import ponticello.model.obj.SynthDefObject
import ponticello.model.registry.BusRegistry
import ponticello.model.registry.InstrumentRegistry
import ponticello.sc.Identifier
import ponticello.ui.registry.SearchableBusListView
import ponticello.ui.score.ScorePane
import reaktive.value.now

sealed interface FlowOption {
    fun createFlow(context: Context, anchor: Point2D): AudioFlow?

    fun defaultName(): String

    data object Send : FlowOption {
        override fun createFlow(context: Context, anchor: Point2D): AudioFlow? {
            val source = SearchableBusListView(context[BusRegistry], "Source bus")
                .showPopup(anchor) ?: return null
            val selected = SearchableBusListView(context[BusRegistry], "Target bus")
                .showPopup(anchor) ?: return null
            return SendFlow.create(source, selected)
        }

        override fun defaultName(): String = "send"
    }

    data object Utility : FlowOption {
        override fun createFlow(context: Context, anchor: Point2D): UtilityFlow? {
            val target = SearchableBusListView(context[BusRegistry], "Target bus")
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
            val out = SearchableBusListView(context[BusRegistry], "Output bus")
                .showPopup(anchor) ?: return null
            return MixerFlow.create(out)
        }

        override fun defaultName(): String = "mixer"
    }

    data class VSTPlugin(val pluginName: String) : FlowOption {
        override fun createFlow(context: Context, anchor: Point2D): AudioFlow? {
            val presets = VSTPlugins.globalPresetList(context, pluginName)
            val preset = if (presets.isNotEmpty()) {
                SimpleSearchableListView(listOf("<no preset>") + presets, "Select preset")
                    .showPopup(anchor)
                    .takeIf { it != "<no preset>" }
            } else null
            val bus = SearchableBusListView(context[BusRegistry], "Target bus")
                .showPopup(anchor) ?: return null
            return VSTPluginFlow.create(pluginName, preset, bus)
        }

        override fun toString(): String = "VST: $pluginName"

        override fun defaultName(): String = Identifier.truncate(pluginName)
    }

    data class Instrument(val def: InstrumentObject) : FlowOption {
        override fun createFlow(context: Context, anchor: Point2D): InstrumentFlow? {
            val controls = ScorePane.getInitialControls(def, context, defaultBus = null, anchor) ?: return null
            return InstrumentFlow.create(def, controls)
        }

        override fun toString(): String = when (def) {
            is SynthDefObject -> "SynthDef ${def.name.now}"
            is ProcessDefObject -> "Process ${def.name.now}"
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