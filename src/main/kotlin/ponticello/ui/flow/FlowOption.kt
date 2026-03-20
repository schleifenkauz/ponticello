package ponticello.ui.flow

import fxutils.prompt.PromptPlacement
import fxutils.prompt.SimpleSelectorPrompt
import hextant.context.Context
import ponticello.model.flow.*
import ponticello.model.instr.*
import ponticello.model.server.BusRegistry
import ponticello.sc.Identifier
import ponticello.sc.Rate
import ponticello.ui.registry.BusSelectorPrompt
import ponticello.ui.score.SoundProcessView
import reaktive.value.now

sealed interface FlowOption {
    fun createFlow(context: Context, promptPlacement: PromptPlacement): AudioFlow?

    fun defaultName(): String

    fun getNameForFlow(takenFlowNames: Set<String>, promptPlacement: PromptPlacement): String? {
        val initialName = getDefaultName(takenFlowNames)
        return FlowNamePrompt(takenFlowNames, "Flow name", initialName)
            .showDialog(promptPlacement)
    }

    fun getDefaultName(takenFlowNames: Set<String>): String {
        val defaultName = defaultName()
        val idx = (1..Int.MAX_VALUE).first { idx -> "${defaultName}_$idx" !in takenFlowNames }
        val initialName = "${defaultName}_$idx"
        return initialName
    }

    data object Send : FlowOption {
        override fun createFlow(context: Context, promptPlacement: PromptPlacement): AudioFlow? {
            val source = BusSelectorPrompt(context[BusRegistry], "Source bus")
                .showDialog(promptPlacement) ?: return null
            val selected = BusSelectorPrompt(context[BusRegistry], "Target bus")
                .showDialog(promptPlacement) ?: return null
            return SendFlow.create(source, selected)
        }

        override fun defaultName(): String = "send"
    }

    data object Utility : FlowOption {
        override fun createFlow(context: Context, promptPlacement: PromptPlacement): UtilityFlow? {
            val target = BusSelectorPrompt(context[BusRegistry], "Target bus")
                .showDialog(promptPlacement) ?: return null
            return UtilityFlow.create(target)
        }

        override fun defaultName(): String = "utility"
    }

    data object Code : FlowOption {
        override fun createFlow(context: Context, promptPlacement: PromptPlacement): AudioFlow = CodeFlow.create()

        override fun defaultName(): String = "code"
    }

    data object Mixer : FlowOption {
        override fun createFlow(context: Context, promptPlacement: PromptPlacement): MixerFlow? {
            val out = BusSelectorPrompt(context[BusRegistry], "Output bus")
                .showPopup(promptPlacement) ?: return null
            return MixerFlow.create(out)
        }

        override fun defaultName(): String = "mixer"
    }

    //TODO option on BusControls to add meter before/after
    data object LevelMeter : FlowOption {
        override fun createFlow(context: Context, promptPlacement: PromptPlacement): AudioFlow? {
            val target = BusSelectorPrompt(context[BusRegistry], "Target bus", rate = Rate.Audio)
                .showPopup(promptPlacement) ?: return null
            return LevelMeterFlow.create(target)
        }

        override fun toString(): String = "Meter"

        override fun defaultName(): String = "meter"

        override fun getNameForFlow(takenFlowNames: Set<String>, promptPlacement: PromptPlacement): String =
            getDefaultName(takenFlowNames)
    }

    data class VSTPlugin(val pluginName: String) : FlowOption {
        override fun createFlow(context: Context, promptPlacement: PromptPlacement): AudioFlow? {
            val presets = VSTPlugins.globalPresetList(context, pluginName)
            val preset = if (presets.isNotEmpty()) {
                SimpleSelectorPrompt(listOf("<no preset>") + presets, "Select preset")
                    .showDialog(promptPlacement)
                    .takeIf { it != "<no preset>" }
            } else null
            val bus = BusSelectorPrompt(context[BusRegistry], "Target bus")
                .showDialog(promptPlacement) ?: return null
            return VSTPluginFlow.create(pluginName, preset, bus)
        }

        override fun toString(): String = "VST: $pluginName"

        override fun defaultName(): String = Identifier.truncate(pluginName)
    }

    data class Instrument(val def: InstrumentObject) : FlowOption {
        override fun createFlow(context: Context, promptPlacement: PromptPlacement): InstrumentFlow? {
            val controls = SoundProcessView.getInitialControls(
                def, context, defaultBus = null, promptPlacement
            ) ?: return null
            return InstrumentFlow.create(def, controls)
        }

        override fun toString(): String = when (def) {
            is SynthDefObject -> "SynthDef ${def.name.now}"
            is RoutineDefObject -> "Process ${def.name.now}"
            is MidiEffectInstrument, MidiInstrument, is NoInstrument ->
                throw AssertionError("$def should not be an option")
        }

        override fun defaultName(): String = def.name.now
    }

    data object MidiTrack : FlowOption {
        override fun createFlow(context: Context, promptPlacement: PromptPlacement): AudioFlow = MidiTrackFlow()

        override fun toString(): String = "MIDI Track"

        override fun defaultName(): String = "midi"
    }

    companion object {
        private val simpleOptions = listOf(Code, Send, Utility, Mixer, LevelMeter, MidiTrack)

        fun getOptions(context: Context): List<FlowOption> {
            val vstOptions = VSTPlugins.availablePlugins(context).map(::VSTPlugin)
            val synthOptions = context[InstrumentRegistry]
                .filter { it !is MidiEffectInstrument }
                .map(::Instrument)
            return simpleOptions + synthOptions + vstOptions
        }
    }
}