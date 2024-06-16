package xenakis.ui

import javafx.scene.control.ScrollPane
import javafx.scene.layout.BorderPane
import xenakis.model.TaskObject

class TaskObjectView(val obj: TaskObject) : ScoreObjectView(obj) {
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