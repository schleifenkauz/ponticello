package ponticello.ui.score

import fxutils.prompt.DetailPane
import fxutils.styleClass
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.value.ObservableValue
import javafx.scene.control.ScrollPane
import ponticello.model.project.InlineControlsDisplay
import ponticello.model.score.ScoreObjectInstance
import ponticello.model.score.TaskObject
import reaktive.value.ReactiveVariable

class TaskObjectView(override val obj: TaskObject, inst: ScoreObjectInstance) : ScoreObjectView(inst) {
    private val codeEditor = obj.code.control

    private val codeArea = ScrollPane(codeEditor)

    init {
        styleClass("task-object")
    }

    override fun getDisplayWidth(): Double = inlineNameLabel.prefWidth(-1.0)

    override fun getDisplayHeight(): Double = inlineNameLabel.prefHeight(-1.0)

    override fun setupDetailPane(pane: DetailPane) {
        pane.addLargeItem("Code: ", this.codeArea)
    }

    override fun inlineControlsVisibilityCondition(
        controlsDisplay: ReactiveVariable<InlineControlsDisplay>,
    ): ObservableValue<Boolean> = SimpleBooleanProperty(false)
}