package ponticello.ui.score

import fxutils.actions.makeButton
import fxutils.button
import fxutils.centerChildren
import fxutils.controls.IntSpinner
import fxutils.drag.setupDropArea
import fxutils.hspace
import fxutils.prompt.*
import fxutils.undo.UndoManager
import hextant.context.Context
import javafx.collections.FXCollections.observableList
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import ponticello.impl.MidiPitch
import ponticello.model.obj.InstrumentReference
import ponticello.model.obj.withName
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.score.*
import ponticello.sc.Identifier
import ponticello.ui.actions.ScoreObjectActions
import ponticello.ui.impl.showDialog
import reaktive.value.now
import reaktive.value.reactiveVariable

class MidiObjectView(
    override val obj: MidiObject, instance: ScoreObjectInstance,
) : AbstractScoreObjectGroupView(instance), MidiObject.Listener {
    override lateinit var scorePane: MidiScorePane
        private set

    private val lowestPitchLabel = Label(MidiPitch(obj.lowestPitch).getNoteName())
    private val highestPitchLabel = Label(MidiPitch(obj.highestPitch).getNoteName())

    override fun initialize() {
        super.initialize()
        scorePane = MidiScorePane(instance, obj, parentPane, context)
        scorePane.prefWidthProperty().bind(prefWidthProperty())
        scorePane.prefHeightProperty().bind(prefHeightProperty())
        scorePane.initialize()
        children.add(scorePane)
        setupDropArea(ParameterizedObjectDropHandler(obj, this))
    }

    override fun rescale() {
        super.rescale()
        scorePane.repaint()
    }

    override fun updatedPitchRange() {
        lowestPitchLabel.text = MidiPitch(obj.lowestPitch).getNoteName()
        highestPitchLabel.text = MidiPitch(obj.highestPitch).getNoteName()
        scorePane.repaint()
    }

    override fun setupDetailPane(pane: DetailPane) {
        val instrumentSelector = InstrumentSelectorPopup(context).selectorButton(
            obj.instrument,
            undoManager = context[UndoManager], actionDescription = "Select MIDI instrument",
            onUpdate = ::updatedInstrument
        )
        instrumentSelector.setupDropArea(InstrumentDropHandler(obj.instrument, context))
        val viewInstrumentBtn = ScoreObjectActions.singleObjectActions.getAction("View definition")
            .withContext(actionContext)
            .makeButton("medium-icon-button")
        pane.addItem("Instrument: ", HBox(5.0, instrumentSelector, viewInstrumentBtn).centerChildren())
        pane.addItem("Color:", this.colorPicker)
        val transposeButton = button("Transpose") { showTransposeDialog() }
        pane.addItem(
            "Pitch range: ", HBox(
                lowestPitchLabel, Label(" - "), highestPitchLabel,
                hspace(50.0), transposeButton
            ).centerChildren()
        )
        pane.addItem("Latency (ms): ", IntSpinner(obj.latencyMs, -200..200, 5))
        pane.children.add(ParameterControlsPane(obj, this))
    }

    private fun updatedInstrument(oldInstr: InstrumentReference, newInstr: InstrumentReference) {
        val options = listOf("No", "Yes", "Yes but only where instrument='${oldInstr.getName()}'")
        val selectedOption = OptionsPrompt(
            "Update instrument of child MIDI notes",
            options = options,
            defaultOption = "Yes"
        ).showDialog(context)
        when (selectedOption) {
            options[0] -> {}
            options[1] -> {
                for (child in obj.score.objects) {
                    if (child is SoundProcess) {
                        child.instrumentRef.set(newInstr)
                    }
                }
            }

            options[2] -> {
                for (child in obj.score.objects) {
                    if (child is SoundProcess && child.instrumentRef.now == oldInstr) {
                        child.instrumentRef.set(newInstr)
                    }
                }
            }
        }
    }

    private fun showTransposeDialog() {
        val semitones = IntegerPrompt("Transpose by semitones", 0, -48..48)
            .showDialog(context) ?: return
        obj.transpose(semitones)
    }

    companion object {
        fun createNewMidiObjectDialog(instr: InstrumentReference, context: Context): Prompt<MidiObject?> =
            compoundPrompt("Configure MIDI object", labelWidth = 130.0) {
                val defaultName = context[ScoreObjectRegistry].availableName("midi")
                val nameField = TextField(defaultName) named "Object name"
                val rootPitchSelector =
                    ComboBox(observableList(MidiPitch.allPitchClasses())) named "Root pitch class"
                rootPitchSelector.value = MidiPitch(0)
                val registerSpinner = IntSpinner(0, 10, 4).minColumns(2) named "Base register"
                val octaves = IntSpinner(1, 12, 2).minColumns(2) named "Octaves"
                onConfirm {
                    val name = nameField.text
                    if (!Identifier.isValid(name) || context[ScoreObjectRegistry].has(name)) return@onConfirm null
                    val lowestPitch = rootPitchSelector.value.step + 12 * registerSpinner.value()
                    val highestPitch = lowestPitch + 12 * octaves.value()
                    val score = Score()
                    val controls = ParameterControlList()
                    MidiObject(
                        reactiveVariable(instr),
                        lowestPitch, highestPitch,
                        score, controls
                    ).withName(name)
                }
            }
    }
}