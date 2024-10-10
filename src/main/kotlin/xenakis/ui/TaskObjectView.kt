package xenakis.ui

import javafx.scene.control.ScrollPane
import xenakis.model.ScoreObjectInstance
import xenakis.model.TaskObject

class TaskObjectView(inst: ScoreObjectInstance, val obj: TaskObject) : ScoreObjectView(inst) {
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

    override fun DetailPane.setupDetailPane() {
        addLargeItem("Code: ", codeArea)
    }
}