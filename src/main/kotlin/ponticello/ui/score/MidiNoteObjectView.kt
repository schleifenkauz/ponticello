package ponticello.ui.score

import fxutils.actions.makeButton
import fxutils.centerChildren
import fxutils.prompt.DetailPane
import fxutils.undo.UndoManager
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.text.Text
import ponticello.impl.*
import ponticello.model.score.MidiNoteObject
import ponticello.model.score.ScoreObjectInstance
import ponticello.ui.actions.ScoreObjectActions
import reaktive.value.binding.map
import reaktive.value.fx.asObservableValue

class MidiNoteObjectView(override val obj: MidiNoteObject, instance: ScoreObjectInstance) : ScoreObjectView(instance) {
    private val pitchLabel = Text(pitchToText(instance.y))

    override fun setupDetailPane(pane: DetailPane) {
        val instrumentSelector = InstrumentSelectorPopup(context).selectorButton(
            obj.parentObject.instrument,
            undoManager = context[UndoManager],
            actionDescription = "Select MIDI instrument"
        )
        val viewInstrumentBtn = ScoreObjectActions.singleObjectActions.getAction("View definition")
            .withContext(actionContext)
            .makeButton("medium-icon-button")
        pane.addItem("Instrument: ", HBox(5.0, instrumentSelector, viewInstrumentBtn).centerChildren())
        pane.children.add(ParameterControlsPane(obj, this))
    }

    override fun getDisplayHeight(): Double {
        val midiPane = parentPane as? MidiScorePane ?: error("Parent of $this is not a MidiScorePane")
        return midiPane.pixelsPerPitch
    }

    override fun getDeltaY(): Decimal = -one(precision = 4)

    override fun initialize() {
        super.initialize()
        backgroundProperty().bind(backgroundColor.map { color ->
            Background(BackgroundFill(color, CornerRadii.EMPTY, null))
        }.asObservableValue())
        val pane = BorderPane(pitchLabel)
        pitchLabel.fillProperty().bind(
            obj.parentObject.associatedColor.map { c -> c?.invert() ?: Color.WHITE }.asObservableValue()
        )
        pitchLabel.visibleProperty().bind(
            prefHeightProperty().greaterThan(10.0).and(prefWidthProperty().greaterThan(15.0))
        )
        pane.prefWidthProperty().bind(this.prefWidthProperty())
        pane.prefHeightProperty().bind(this.prefHeightProperty())
        children.add(pane)
    }

    override fun moved(start: Decimal, y: Decimal) {
        super.moved(start, y)
        pitchLabel.text = pitchToText(y)
    }

    companion object {
        private fun pitchToText(pitch: Decimal): String {
            val midinote = pitch.toInt()
            val noteName = MidiPitch(midinote).getNoteName()
            val bend = ((pitch - midinote.toDecimal()) * 100).toInt()
            return if (bend == 0) noteName else "$noteName+$bend"
        }
    }
}