package xenakis.sc.view

import bundles.Bundle
import hextant.core.view.EditorControl
import javafx.scene.Node
import javafx.scene.control.Button
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import xenakis.model.NamedObject
import xenakis.model.ObjectReference
import xenakis.sc.editor.ObjectSelector
import xenakis.ui.SearchableRegistryView

class ObjectSelectorControl<O : NamedObject, R : ObjectReference?>(
    val editor: ObjectSelector<O, R>, arguments: Bundle
) : EditorControl<Node>(editor, arguments), ObjectSelectorView<O> {
    private val button = Button()
    private val listView = object : SearchableRegistryView<O>(editor.getRegistry(context)) {
        override fun createObject(name: String): O? = editor.createNewObject(name)

        override fun displayText(option: O): String = editor.extractText(option).now
    }

    init {
        editor.addView(this)
        button.setOnAction {
            val title = "Select ${editor.getRegistry(context).objectType}"
            listView.enterText(editor.selected.now?.getName() ?: "")
            listView.showPopup(context, title, anchorNode = button) { option ->
                editor.select(option.createReference() as R)
            }
        }
    }

    override fun selected(obj: O?) {
        button.textProperty().unbind()
        button.textProperty().bind(editor.extractText(obj).asObservableValue())
    }

    override fun createDefaultRoot(): Node = button
}