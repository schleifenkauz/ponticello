package xenakis.ui.registry

import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import xenakis.ui.impl.styleClass

open class SimpleSearchableListView<E>(private val options: List<E>, title: String) : SearchableListView<E>(title) {
    override fun options(): List<E> = options

    override fun extractText(option: E): String = option.toString()

    override fun createCell(option: E): Node = HBox(Label(displayText(option)).styleClass("option-label"))
}