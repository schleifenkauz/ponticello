package xenakis.ui.score

import fxutils.label
import fxutils.prompt.DetailPane
import fxutils.styleClass
import javafx.scene.control.ScrollPane
import xenakis.model.score.ScoreObjectInstance
import xenakis.model.score.TaskObject

class TaskObjectView(override val obj: TaskObject, inst: ScoreObjectInstance) : ScoreObjectView(inst) {
    private val nameLabel = label(obj.name) styleClass "task-label"

    private val codeEditor = obj.code.control

    private val codeArea = ScrollPane(codeEditor)

    init {
        styleClass("task-object")
        nameLabel.widthProperty().addListener { _ -> resizedObject(obj) }
        nameLabel.heightProperty().addListener { _ -> resizedObject(obj) }
        children.add(nameLabel)
    }

    override fun getDisplayWidth(): Double = nameLabel.width

    override fun getDisplayHeight(): Double = nameLabel.height

    override fun setupDetailPane(pane: DetailPane) {
        pane.addLargeItem("Code: ", this.codeArea)
    }
}