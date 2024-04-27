package xenakis.ui

import hextant.context.createControl
import javafx.scene.control.ScrollPane
import javafx.stage.StageStyle
import xenakis.model.TaskObject
import xenakis.model.XenakisProject

class TaskObjectView(obj: TaskObject, project: XenakisProject) : ScoreObjectView(obj, project) {
    private val codeEditor = project.context.createControl(obj.code)
    private val codeArea = ScrollPane(codeEditor)
    private val codeWindow = codeArea.makeWindow(
        "Code: ${obj.name}", project.context,
        style = StageStyle.DECORATED, parent = this
    )

    init {
        styleClass("task-object")
        codeArea.prefWidthProperty().bind(codeEditor.prefWidthProperty())
        codeArea.prefHeightProperty().bind(codeEditor.prefHeightProperty())
    }

    override fun repaint() {
        super.repaint()
        children.add(1, codeArea)
        addFunction(Icon.ExtraWindow, "Open in separate window") { codeWindow.show() }
    }
}