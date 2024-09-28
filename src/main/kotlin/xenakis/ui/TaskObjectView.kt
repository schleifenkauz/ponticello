package xenakis.ui

import javafx.scene.control.ScrollPane
import javafx.scene.layout.BorderPane
import xenakis.model.ScoreObjectInstance
import xenakis.model.TaskObject

class TaskObjectView(inst: ScoreObjectInstance, val obj: TaskObject) : ScoreObjectView(inst) {
    private val nameLabel = label(obj.name)

    private val codeEditor = obj.code.control

    private val codeArea = ScrollPane(codeEditor)

    init {
        styleClass("task-object")
        nameLabel.widthProperty().addListener { _ -> resizedObject(obj) }
        nameLabel.heightProperty().addListener { _ -> resizedObject(obj) }
        val layout = BorderPane(nameLabel)
        children.add(layout)
    }

    override fun getDisplayWidth(): Double = nameLabel.width

    override fun getDisplayHeight(): Double = nameLabel.height

    override fun DetailPane.setupDetailPane() {
        addLargeItem("Code: ", codeArea)
    }
}