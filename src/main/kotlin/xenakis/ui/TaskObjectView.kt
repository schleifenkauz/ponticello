package xenakis.ui

import hextant.context.createControl
import javafx.scene.control.ScrollPane
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.StageStyle
import xenakis.model.TaskObject
import xenakis.model.XenakisProject

class TaskObjectView(obj: TaskObject, project: XenakisProject) : ScoreObjectView(obj, project) {
    private val codeEditor = project.context.createControl(obj.code) {
        set(DISPLAY_BRACES, false)
    }
    private val codeArea = ScrollPane(codeEditor)
    private val codeWindow = codeArea.makeWindow(
        "Code: ${obj.name}", project.context,
        style = StageStyle.DECORATED, parent = contents
    )

    init {
        styleClass("task-object")
        codeEditor.styleClass("code-box")
        codeArea.styleClass("code-area")
        setVgrow(codeArea, Priority.SOMETIMES)
        codeArea.prefWidthProperty().bind(codeEditor.prefWidthProperty())
        codeArea.prefHeightProperty().bind(codeEditor.prefHeightProperty())
    }

    override fun repaint() {
        super.repaint()
        contents.children.add(0, codeArea)
        addAction(Icon.ExtraWindow, "Open in separate window") { codeWindow.show() }
    }
}