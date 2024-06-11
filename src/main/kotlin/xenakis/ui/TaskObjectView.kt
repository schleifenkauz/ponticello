package xenakis.ui

import javafx.scene.control.ScrollPane
import javafx.scene.input.MouseButton
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
        addEventHandler(MouseEvent.MOUSE_CLICKED) { ev ->
            if (ev.button == MouseButton.PRIMARY && ev.clickCount >= 2) {
                showInSubWindow()
            }
        }
    }

    override fun getSubWindowView(): Region = codeArea
}