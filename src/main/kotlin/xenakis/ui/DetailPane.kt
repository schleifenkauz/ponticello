package xenakis.ui

import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox

class DetailPane : VBox() {
    init {
        styleClass("detail-pane", "tool-pane")
    }

    fun addItem(name: String, control: Node) {
        val label = Label(name)
        label.prefWidth = LABEL_WIDTH
        HBox.setHgrow(control, Priority.ALWAYS)
        val box = HBox(5.0, label, control) styleClass "detail-item"
        children.add(box)
    }

    fun addLargeItem(name: String, control: Node) {
        val label = Label(name)
        val box = VBox(5.0, label, control) styleClass "detail-item"
        children.add(box)
    }

    companion object {
        const val LABEL_WIDTH = 150.0
    }
}