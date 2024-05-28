package xenakis.ui

import javafx.scene.Cursor
import javafx.scene.control.ScrollPane
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Priority
import xenakis.model.TaskObject

class TaskObjectView(val obj: TaskObject) : ScoreObjectView(obj) {
    private val codeEditor = obj.code.control

    private val codeArea = ScrollPane(codeEditor)

    init {
        styleClass("task-object")
        codeEditor.styleClass("code-box")
        codeArea.styleClass("code-area")
        setVgrow(codeArea, Priority.SOMETIMES)
        codeArea.prefWidthProperty().bind(codeEditor.prefWidthProperty())
        codeArea.prefHeightProperty().bind(codeEditor.prefHeightProperty())
        children.add(0, codeArea)
    }

    override fun resizeObject(width: Double, height: Double, ev: MouseEvent, cursor: Cursor) {
        obj.width = width.coerceAtLeast(100.0)
        obj.height = height.coerceAtLeast(100.0)
    }

    override fun getDisplayWidth(): Double = obj.width
}