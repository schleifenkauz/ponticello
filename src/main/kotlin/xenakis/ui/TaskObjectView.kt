package xenakis.ui

import javafx.scene.control.ScrollPane
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Priority
import javafx.stage.StageStyle
import xenakis.model.TaskObject
import xenakis.model.XenakisProject

class TaskObjectView(override val obj: TaskObject, project: XenakisProject) : ScoreObjectView(obj, project) {
    private val codeEditor = obj.code.control

    private val codeArea = ScrollPane(codeEditor)
    private val codeWindow = codeArea.makeWindow(
        "Code: ${obj.name}", project.context,
        style = StageStyle.DECORATED, parent = contents
    )

    init {
        styleClass("task-object")
        contents.styleClass.add("task-object-content")
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

    override fun setObjectWidth(width: Double, ev: MouseEvent, resizeFromLeft: Boolean) {
        obj.width = width.coerceAtLeast(100.0)
    }

    override fun getDisplayWidth(): Double = obj.width
}