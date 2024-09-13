package xenakis.ui

import javafx.scene.control.ScrollPane
import javafx.scene.layout.BorderPane
import xenakis.model.ScoreObjectInstance
import xenakis.model.TaskObject

class TaskObjectView(inst: ScoreObjectInstance, val obj: TaskObject) : ScoreObjectView(inst) {
    private val codeEditor = obj.code.control

    private val codeArea = ScrollPane(codeEditor)

    init {
        styleClass("task-object")
        val nameLabel = label(obj.name)
        val layout = BorderPane(nameLabel)
        children.add(layout)
    }

    override fun DetailPane.setupDetailPane() {
        addLargeItem("Code: ", codeArea)
    }
}