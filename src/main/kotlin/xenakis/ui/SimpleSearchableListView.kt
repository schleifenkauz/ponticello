package xenakis.ui

import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.layout.HBox

open class SimpleSearchableListView<E>(private val options: List<E>) : SearchableListView<E>() {
    override fun options(): List<E> = options

    protected open fun displayText(option: E): String = extractText(option)

    override fun extractText(option: E): String = option.toString()

    override fun createCell(option: E): Node = HBox(Label(displayText(option)).styleClass("option-label"))
}