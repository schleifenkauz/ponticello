package xenakis.ui.prompt

import hextant.context.Context
import javafx.geometry.Point2D
import javafx.scene.Node
import xenakis.ui.SimpleSearchableListView

fun <T : Any> showSelectorDialog(
    context: Context, title: String,
    items: List<T>, initialValue: T? = null,
    anchor: Point2D? = null, stringConverter: (T) -> String = { it.toString() }
): T? {
    val view = object : SimpleSearchableListView<T>(items, title) {
        override fun extractText(option: T): String = stringConverter(option)
    }
    var value = initialValue
    view.showPopup(context, anchor, initialValue) { v -> value = v }
    return value
}

fun <T : Any> showSelectorDialog(
    context: Context, title: String,
    items: List<T>, initialValue: T? = null,
    anchorNode: Node? = null, stringConverter: (T) -> String = { it.toString() }
): T? = showSelectorDialog(context, title, items, initialValue, anchorNode?.localToScreen(0.0, 0.0), stringConverter)

fun <R : Any> compoundInput(title: String, body: CompoundPrompt<R>.() -> Unit): CompoundPrompt<R> {
    val input = CompoundPrompt<R>(title)
    input.body()
    return input
}