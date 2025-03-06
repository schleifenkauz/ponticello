package xenakis.ui.impl

import fxutils.actions.action
import fxutils.actions.makeButton
import fxutils.centerChildren
import fxutils.infiniteSpace
import fxutils.styleClass
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import reaktive.value.binding.map
import reaktive.value.now
import reaktive.value.reactiveVariable

class CollapsablePane(
    title: String,
    private val content: Node,
    initiallyExpanded: Boolean = true
) : VBox() {
    private val heading = Label(title) styleClass "heading"
    private val expanded = reactiveVariable(initiallyExpanded)
    private val actionBar = expandCollapseAction.withContext(this).makeButton("medium-icon-button")
    private val header = HBox(heading, infiniteSpace(), actionBar).centerChildren()

    init {
        children.add(header)
        if (initiallyExpanded) children.add(content)
    }

    fun collapse() {
        if (expanded.now) {
            children.remove(content)
            expanded.now = false
        }
    }

    fun expand() {
        if (!expanded.now) {
            children.add(content)
            expanded.now = true
        }
    }

    companion object {
        private val expandCollapseAction = action<CollapsablePane>("Expand/collapse") {
            icon { pane -> pane.expanded.map { isExpanded -> if (isExpanded) MaterialDesignC.CHEVRON_UP else MaterialDesignC.CHEVRON_DOWN } }
            description { pane -> pane.expanded.map { isExpanded -> if (isExpanded) "Collapse" else "Expand" } }
            executes { p -> if (p.expanded.now) p.collapse() else p.expand() }
        }
    }
}