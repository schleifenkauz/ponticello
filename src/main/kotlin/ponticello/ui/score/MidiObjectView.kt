package ponticello.ui.score

import fxutils.button
import fxutils.centerChildren
import fxutils.controls.IntSpinner
import fxutils.controls.OptionSpinner
import fxutils.drag.setupDropArea
import fxutils.hspace
import fxutils.prompt.*
import fxutils.undo.UndoManager
import hextant.context.Context
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import ponticello.impl.MidiPitch
import ponticello.model.obj.MidiTrackReference
import ponticello.model.obj.withName
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.score.MidiObject
import ponticello.model.score.Score
import ponticello.model.score.ScoreObjectInstance
import ponticello.model.score.controls.ParameterControlList
import ponticello.sc.Identifier
import ponticello.ui.impl.showDialog
import ponticello.ui.midi.MidiContext
import ponticello.ui.midi.MidiTrackSelectorPrompt
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
        scorePane = MidiScorePane(instance, obj, this, context)
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

    override fun setupDetailPane(pane: DetailPane, midiContext: MidiContext?): Unit = with(pane) {
        addItem("Color:", colorPicker)
        val trackSelector = MidiTrackSelectorPrompt(context).selectorButton(
            obj.track, undoManager = context[UndoManager], actionDescription = "Select MIDI track"
        )
        addItem("Track: ", trackSelector)
        val transposeButton = button("Transpose") { showTransposeDialog() }
        transposeButton.isFocusTraversable = false
        addItem(
            "Pitch range: ", HBox(
                lowestPitchLabel, Label(" - "), highestPitchLabel,
                hspace(50.0), transposeButton
            ).centerChildren()
        )
        children.add(ParameterControlsPane(obj, this@MidiObjectView, midiContext))
    }

    private fun showTransposeDialog() {
        val semitones = IntegerPrompt("Transpose by semitones", 0, -48..48)
            .showDialog(context) ?: return
        obj.transpose(semitones)
    }

    companion object {
        fun createNewMidiObjectDialog(track: MidiTrackReference, context: Context): Prompt<MidiObject?> =
            compoundPrompt("Configure MIDI object", labelWidth = 130.0) {
                val defaultName = context[ScoreObjectRegistry].availableName("midi")
                val nameField = TextField(defaultName) named "Object name"
                val rootPitch = reactiveVariable(MidiPitch(0))
                val rootPitchSelector = OptionSpinner(
                    rootPitch, MidiPitch.allPitchClasses(),
                    selectorPrompt = SimpleSelectorPrompt(MidiPitch.allPitchClasses(), "Select root pitch class")
                )
                addItem("Root pitch class", rootPitchSelector)
                val registerSpinner = IntSpinner(0, 10, 4).minColumns(2) named "Base register"
                val octaves = IntSpinner(1, 12, 2).minColumns(2) named "Octaves"
                onConfirm {
                    val name = nameField.text
                    if (!Identifier.isValid(name) || context[ScoreObjectRegistry].has(name)) return@onConfirm null
                    val lowestPitch = rootPitch.now.step + 12 * registerSpinner.value()
                    val highestPitch = lowestPitch + 12 * octaves.value()
                    val score = Score()
                    val controls = ParameterControlList()
                    MidiObject(
                        reactiveVariable(track),
                        lowestPitch, highestPitch,
                        score, controls
                    ).withName(name)
                }
            }
    }
}