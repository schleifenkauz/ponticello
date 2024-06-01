package xenakis.ui

import javafx.scene.Cursor
import javafx.scene.control.ScrollPane
import javafx.scene.input.MouseEvent
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Region
import xenakis.model.TaskObject

class TaskObjectView(val obj: TaskObject) : ScoreObjectView(obj) {
    private val codeEditor = obj.code.control

    private val codeArea = ScrollPane(codeEditor)

    init {
        styleClass("task-object")
        codeEditor.styleClass("code-box")
        codeArea.styleClass("code-area")
        val nameLabel = label(obj.name)
        val layout = BorderPane(nameLabel)
        children.add(layout)
    }

    override fun getSubWindowView(): Region = codeArea

    override fun resizeObject(width: Double, height: Double, ev: MouseEvent, cursor: Cursor) {
        obj.width = width.coerceAtLeast(100.0)
        obj.height = height.coerceAtLeast(100.0)
    }

    override fun getDisplayWidth(): Double = obj.width
}