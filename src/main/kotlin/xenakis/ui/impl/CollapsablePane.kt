package xenakis.ui.impl

import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.ui.actions.ActionBar
import xenakis.ui.actions.collectActions

class CollapsablePane(
    title: String,
    private val content: Node,
    initiallyExpanded: Boolean = true
) : VBox() {
    private val heading = Label(title) styleClass "heading"
    private val expanded = reactiveVariable(initiallyExpanded)
    private val actionBar = ActionBar(actions.withContext(this))
    private val header = HBox(heading, infiniteSpace(), actionBar).centerChildren()

    init {
        children.add(header)
        if (initiallyExpanded) children.add(content)
    }

    fun collapse() {
        if (expanded.now) {
            children.remove(content)
            expanded.now = true
        }
    }

    fun expand() {
        if (!expanded.now) {
            children.add(content)
            expanded.now = true
        }
    }

    companion object {
        private val actions = collectActions<CollapsablePane> {
            addAction("Collapse") {
                icon(MaterialDesignC.CHEVRON_UP)
                applicableIf { p -> p.expanded }
                execute { p -> p.collapse() }
            }
            addAction("Expand") {
                icon(MaterialDesignC.CHEVRON_DOWN)
                applicableIf { p -> p.expanded }
                execute { p -> p.expand() }
            }
        }
    }
}