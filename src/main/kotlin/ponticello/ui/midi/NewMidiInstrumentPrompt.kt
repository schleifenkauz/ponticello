package ponticello.ui.midi

import fxutils.infiniteSpace
import fxutils.prompt.PromptPlacement
import fxutils.prompt.SelectorPrompt
import fxutils.prompt.SimpleSelectorPrompt
import fxutils.setFixedWidth
import fxutils.styleClass
import hextant.context.Context
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import ponticello.model.flow.VSTPluginFlow
import ponticello.model.flow.VSTPlugins
import ponticello.model.instr.InstrumentObject
import ponticello.model.instr.MidiEffectInstrument
import ponticello.model.midi.*
import ponticello.model.obj.project
import ponticello.model.project.instruments
import ponticello.model.registry.reference
import ponticello.model.score.controls.ParameterControlList
import ponticello.model.server.BusRegistry
import ponticello.ui.registry.BusSelectorPrompt
import reaktive.value.now
import reaktive.value.reactiveVariable

class NewMidiInstrumentPrompt(
    private val context: Context,
    title: String,
) : SelectorPrompt<NewMidiInstrumentPrompt.MidiInstrumentOption>(title) {
    override fun options(): List<MidiInstrumentOption> {
        val userInstruments: List<MidiInstrumentOption> = context.project.instruments
            .filter { it !is MidiEffectInstrument }
            .map(MidiInstrumentOption::SoundProcess)
        val midiEffects: List<MidiInstrumentOption> = context.project.instruments
            .filterIsInstance<MidiEffectInstrument>()
            .map(MidiInstrumentOption::MidiEffect)
        val vstInstruments: List<MidiInstrumentOption> = VSTPlugins.availablePlugins(context, midi = true)
            .map(MidiInstrumentOption::VST)
        return userInstruments + midiEffects + vstInstruments + MidiInstrumentOption.Grid
    }

    override fun createCell(option: MidiInstrumentOption): Region {
        val type = when (option) {
            is MidiInstrumentOption.SoundProcess -> option.instrument.instrumentType
            is MidiInstrumentOption.MidiEffect -> "MIDI Effect"
            is MidiInstrumentOption.VST -> "VST"
            is MidiInstrumentOption.Grid -> "Grid"
        }
        val typeLabel = Label(type).setFixedWidth(80.0).styleClass("instrument-type-label")
        val name = extractText(option)
        return HBox(Label(name).setFixedWidth(150.0), infiniteSpace(), typeLabel)
    }

    override fun extractText(option: MidiInstrumentOption): String = when (option) {
        is MidiInstrumentOption.SoundProcess -> option.instrument.name.now
        is MidiInstrumentOption.MidiEffect -> option.instrument.name.now
        is MidiInstrumentOption.VST -> option.pluginName
        is MidiInstrumentOption.Grid -> "Grid"
    }

    sealed class MidiInstrumentOption {
        fun createInstrument(context: Context, promptPlacement: PromptPlacement): MidiInstrument? = when (this) {
            is SoundProcess ->
                SoundProcessMidiInstrument(reactiveVariable(instrument.reference()), ParameterControlList())

            is MidiEffect ->
                MidiEffectObject(reactiveVariable(instrument.reference()), ParameterControlList())

            is VST -> {
                val presets = VSTPlugins.globalPresetList(context, pluginName)
                val preset = if (presets.isNotEmpty()) {
                    SimpleSelectorPrompt(listOf("<no preset>") + presets, "Select preset")
                        .showPopup(promptPlacement)
                        .takeIf { it != "<no preset>" }
                } else null
                val bus = BusSelectorPrompt(context[BusRegistry], "Target bus")
                    .showPopup(promptPlacement) ?: return null
                val flow = VSTPluginFlow.create(pluginName, preset, bus)
                VSTMidiInstrument(flow)
            }

            is Grid -> MidiGridInstrument.createNByN(4, 1)
        }

        object Grid : MidiInstrumentOption()
        data class SoundProcess(val instrument: InstrumentObject) : MidiInstrumentOption()
        data class MidiEffect(val instrument: MidiEffectInstrument) : MidiInstrumentOption()
        data class VST(val pluginName: String) : MidiInstrumentOption()
    }
}