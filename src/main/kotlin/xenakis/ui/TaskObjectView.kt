package xenakis.ui

import hextant.fx.initHextantScene
import javafx.scene.control.ScrollPane
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.BorderPane
import reaktive.value.binding.map
import reaktive.value.fx.asObservableValue
import xenakis.model.TaskObject

class TaskObjectView(val obj: TaskObject) : ScoreObjectView(obj) {
    private val codeEditor = obj.code.control

    private val codeArea = ScrollPane(codeEditor)

    private val window = SubWindow(codeArea, "", obj.context)

    init {
        styleClass("task-object")
        window.titleProperty().bind(obj.name.map { name -> "Code: $name" }.asObservableValue())
        window.resize(1000.0, 1000.0)
        val nameLabel = label(obj.name)
        val layout = BorderPane(nameLabel)
        children.add(layout)
        window.scene.initHextantScene(context, applyStyle = false)
        addEventHandler(MouseEvent.MOUSE_CLICKED) { ev ->
            if (ev.button == MouseButton.PRIMARY && ev.clickCount >= 2) {
                window.show()
            }
        }
    }
}