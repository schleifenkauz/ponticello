package ponticello.ui.score

import fxutils.prompt.DetailPane
import fxutils.styleClass
import javafx.scene.control.ScrollPane
import ponticello.model.score.ScoreObjectInstance
import ponticello.model.score.TaskObject

class TaskObjectView(override val obj: TaskObject, inst: ScoreObjectInstance) : ScoreObjectView(inst) {
    private val codeEditor = obj.code.control

    private val codeArea = ScrollPane(codeEditor)

    init {
        styleClass("task-object")
    }

    override fun getDisplayWidth(): Double = inlineControls.prefWidth(-1.0)

    override fun getDisplayHeight(): Double = inlineControls.prefHeight(-1.0)

    override fun setupDetailPane(pane: DetailPane) {
        pane.addLargeItem("Code: ", this.codeArea)
    }
}