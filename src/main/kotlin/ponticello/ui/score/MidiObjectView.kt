package ponticello.ui.score

import fxutils.button
import fxutils.centerChildren
import fxutils.controls.IntSpinner
import fxutils.drag.setupDropArea
import fxutils.hspace
import fxutils.prompt.DetailPane
import fxutils.prompt.IntegerPrompt
import fxutils.prompt.Prompt
import fxutils.prompt.compoundPrompt
import fxutils.undo.UndoManager
import hextant.context.Context
import javafx.collections.FXCollections.observableList
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import ponticello.impl.MidiPitch
import ponticello.model.obj.MidiInstrument
import ponticello.model.obj.withName
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.score.MidiObject
import ponticello.model.score.ParameterControlList
import ponticello.model.score.Score
import ponticello.model.score.ScoreObjectInstance
import ponticello.sc.Identifier
import ponticello.ui.impl.showDialog
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
        val instrumentSelector = MidiInstrumentSelectorPopup(context).selectorButton(
            obj.instrument, undoManager = context[UndoManager], actionDescription = "Select MIDI instrument"
        )
        pane.addItem("Instrument: ", instrumentSelector)
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

    private fun showTransposeDialog() {
        val semitones = IntegerPrompt("Transpose by semitones", 0, -48..48)
            .showDialog(context) ?: return
        obj.transpose(semitones)
    }


    companion object {
        fun createNewMidiObjectDialog(instr: MidiInstrument, context: Context): Prompt<MidiObject?, *> =
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