package xenakis.impl

import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import xenakis.ui.Icon
import xenakis.ui.centerChildren
import xenakis.ui.infiniteSpace
import xenakis.ui.styleClass

class CollapsablePane(title: String, private val content: Node) : VBox() {
    private val heading = Label(title) styleClass "heading"
    private val collapseBtn = Icon.Collapse.button { collapse() }
    private val expandBtn = Icon.Expand.button { expand() }
    private val header = HBox(heading, infiniteSpace(), collapseBtn).centerChildren()

    init {
        children.add(header)
        children.add(content)
    }

    fun collapse() {
        if (content in children) {
            children.remove(content)
            header.children[2] = expandBtn
        }
    }

    fun expand() {
        if (content !in children) {
            children.add(content)
            header.children[2] = collapseBtn
        }
    }
}