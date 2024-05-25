package xenakis.ui

import javafx.scene.Cursor
import javafx.scene.control.ScrollPane
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Priority
import javafx.stage.Stage
import javafx.stage.StageStyle
import xenakis.model.TaskObject

class TaskObjectView(val obj: TaskObject) : ScoreObjectView(obj) {
    private val codeEditor = obj.code.control

    private val codeArea = ScrollPane(codeEditor)

    private lateinit var codeWindow: Stage

    init {
        styleClass("task-object")
        codeEditor.styleClass("code-box")
        codeArea.styleClass("code-area")
        setVgrow(codeArea, Priority.SOMETIMES)
        codeArea.prefWidthProperty().bind(codeEditor.prefWidthProperty())
        codeArea.prefHeightProperty().bind(codeEditor.prefHeightProperty())
        children.add(0, codeArea)
        addAction(Icon.ExtraWindow, "Open in separate window") { codeWindow.show() }
    }

    override fun initialize(parent: ScorePane) {
        super.initialize(parent)
        codeWindow = SubWindow(
            codeArea, "Code: ${obj.name}", context,
            style = StageStyle.DECORATED, parent = this
        )
    }

    override fun resizeObject(width: Double, height: Double, ev: MouseEvent, cursor: Cursor): Boolean {
        if (width < 100.0) return false
        obj.width = width
        return true
    }

    override fun getDisplayWidth(): Double = obj.width
}