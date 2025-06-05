package ponticello.ui.flow

import hextant.context.Context
import javafx.geometry.Point2D
import ponticello.model.flow.*
import ponticello.model.obj.ProcessDefObject
import ponticello.model.obj.SynthDefObject
import ponticello.model.registry.BusRegistry
import ponticello.model.registry.SynthDefRegistry
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
            val bus = SearchableBusListView(context[BusRegistry], "Target bus")
                .showPopup(anchor) ?: return null
            return VSTPluginFlow.create(pluginName, bus)
        }

        override fun toString(): String = "VST: $pluginName"

        override fun defaultName(): String = Identifier.truncate(pluginName)
    }

    data class Synth(val def: SynthDefObject) : FlowOption {
        override fun createFlow(context: Context, anchor: Point2D): SynthFlow? {
            val controls = ScorePane.getInitialControls(def, context, defaultBus = null, anchor) ?: return null
            return SynthFlow.create(def, controls)
        }

        override fun toString(): String = "Synth ${def.name.now}"

        override fun defaultName(): String = def.name.now
    }

    data class Process(val def: ProcessDefObject): FlowOption {
        override fun createFlow(context: Context, anchor: Point2D): ProcessFlow? {
            val controls = ScorePane.getInitialControls(def, context, defaultBus = null, anchor) ?: return null
            return ProcessFlow.create(def, controls)
        }

        override fun toString(): String = "Process ${def.name.now}"

        override fun defaultName(): String = def.name.now
    }

    companion object {
        private val simpleOptions = listOf(Code, Send, Utility, Mixer)

        fun getOptions(context: Context): List<FlowOption> {
            val vstOptions = VSTPluginFlow.availablePlugins(context).map(::VSTPlugin)
            val synthOptions = context[SynthDefRegistry].map(::Synth)
            return simpleOptions + synthOptions + vstOptions
        }
    }
}