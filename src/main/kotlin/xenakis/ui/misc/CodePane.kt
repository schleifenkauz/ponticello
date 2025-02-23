@file:Suppress("SERIALIZER_TYPE_INCOMPATIBLE")

package xenakis.ui.misc

import hextant.core.view.EditorControl
import hextant.fx.hbox
import hextant.fx.registerShortcuts
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import org.kordamp.ikonli.material2.Material2MZ
import xenakis.sc.editor.ScExprEditor
import xenakis.ui.actions.button
import xenakis.ui.impl.centerChildren
import xenakis.ui.impl.infiniteSpace
import xenakis.ui.impl.styleClass

class CodePane(title: String, val control: EditorControl<*>) : VBox() {
    private val titleLabel = Label(title).styleClass("heading")
    private val header = createHeader()
    private val scrollPane = ScrollPane(control).styleClass("code-area")

    var title: String by titleLabel::text

    init {
        styleClass("code-pane")
        children.addAll(header, scrollPane)
        registerShortcuts {
            on("F5") { evaluate() }
        }
    }

    private fun createHeader(): HBox {
        val reevaluateBtn = Material2MZ.SYNC.button(action = "Reevaluate code") { evaluate() }
        return hbox(titleLabel, infiniteSpace(), reevaluateBtn) {
            styleClass("tool-pane-header")
            centerChildren()
        }
    }

    private fun evaluate() {
        val target = control.target as ScExprEditor
        target.eval()
    }

    fun addToHeader(node: Node) {
        header.children.add(node)
    }
}