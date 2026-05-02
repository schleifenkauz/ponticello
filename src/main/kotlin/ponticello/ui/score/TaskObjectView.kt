package ponticello.ui.score

import fxutils.label
import fxutils.prompt.DetailPane
import fxutils.styleClass
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.value.ObservableValue
import javafx.scene.control.ScrollPane
import ponticello.model.project.InlineControlsDisplay
import ponticello.model.score.ScoreObjectInstance
import ponticello.model.score.TaskObject
import ponticello.ui.midi.MidiContext
import reaktive.value.ReactiveVariable

class TaskObjectView(override val obj: TaskObject, inst: ScoreObjectInstance) : ScoreObjectView(inst) {
    private val codeEditor = obj.code.control

    private val codeArea = ScrollPane(codeEditor)
    private val nameLabel = label(obj.name)

    init {
        styleClass("task-object")
        children.add(nameLabel)
    }

    override fun getDisplayWidth(): Double = nameLabel.width

    override fun getDisplayHeight(): Double = nameLabel.height

    override fun setupDetailPane(pane: DetailPane, midiContext: MidiContext?) {
        pane.addLargeItem("Code: ", this.codeArea)
    }

    override fun inlineControlsVisibilityCondition(
        controlsDisplay: ReactiveVariable<InlineControlsDisplay>,
    ): ObservableValue<Boolean> = SimpleBooleanProperty(false)
}