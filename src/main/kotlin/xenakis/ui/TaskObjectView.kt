package xenakis.ui

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

    override fun init(parent: ScoreView) {
        super.init(parent)
        codeArea.makeWindow(
            "Code: ${obj.name}", context,
            style = StageStyle.DECORATED, parent = this
        )
    }

    override fun setObjectWidth(width: Double, ev: MouseEvent, resizeFromLeft: Boolean) {
        obj.width = width.coerceAtLeast(100.0)
    }

    override fun getDisplayWidth(): Double = obj.width
}