package ponticello.ui.midi

import fxutils.infiniteSpace
import fxutils.prompt.SelectorPrompt
import fxutils.setFixedWidth
import fxutils.styleClass
import hextant.context.Context
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import ponticello.model.flow.VSTPluginFlow
import ponticello.model.instr.InstrumentObject
import ponticello.model.obj.project
import ponticello.model.project.flows
import ponticello.model.project.instruments
import ponticello.model.registry.reference
import ponticello.model.score.controls.ParameterControlList
import reaktive.value.now
import reaktive.value.reactiveVariable

class MidiInstrumentSelectorPrompt(
    private val context: Context,
    title: String,
) : SelectorPrompt<MidiInstrumentSelectorPrompt.MidiInstrumentOption>(title) {
    override fun options(): List<MidiInstrumentOption> {
        val userInstruments: List<MidiInstrumentOption> = context.project.instruments
            .map(MidiInstrumentOption::SoundProcess)
        val vstInstruments: List<MidiInstrumentOption> = context.project.flows.allFlows()
            .filterIsInstance<VSTPluginFlow>()
            .filter { flow -> flow.supportsMidiInput }
            .map(MidiInstrumentOption::VST)
        return userInstruments + vstInstruments
    }

    override fun createCell(option: MidiInstrumentOption): Region {
        val type = when (option) {
            is MidiInstrumentOption.SoundProcess -> option.instrument.instrumentType
            is MidiInstrumentOption.VST -> "VST"
        }
        val typeLabel = Label(type).setFixedWidth(70.0).styleClass("instrument-type-label")
        val name = extractText(option)
        return HBox(Label(name).setFixedWidth(150.0), infiniteSpace(), typeLabel)
    }

    override fun extractText(option: MidiInstrumentOption): String = when (option) {
        is MidiInstrumentOption.SoundProcess -> option.instrument.name.now
        is MidiInstrumentOption.VST -> option.flow.name.now
    }

    sealed class MidiInstrumentOption {
        fun createInstrument(): MidiInstrument = when (this) {
            is SoundProcess ->
                SoundProcessMidiInstrument(reactiveVariable(instrument.reference()), ParameterControlList())

            is VST -> VSTMidiInstrument(flow.reference())
        }

        data class SoundProcess(val instrument: InstrumentObject) : MidiInstrumentOption()
        data class VST(val flow: VSTPluginFlow) : MidiInstrumentOption()
    }
}