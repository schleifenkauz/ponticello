package xenakis.sc.view

import bundles.Bundle
import hextant.core.view.EditorControl
import javafx.scene.Node
import javafx.scene.control.Button
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import xenakis.model.NamedObject
import xenakis.model.ObjectReference
import xenakis.model.ObjectRegistry
import xenakis.sc.editor.ObjectSelector
import xenakis.ui.SearchableRegistryView
import xenakis.ui.styleClass

class ObjectSelectorControl<O : NamedObject, R : ObjectReference?>(
    val editor: ObjectSelector<O, R>, arguments: Bundle
) : EditorControl<Node>(editor, arguments), ObjectSelectorView<O> {
    private val button = Button() styleClass "sleek-button"

    @Suppress("UNCHECKED_CAST")
    private val listView: SearchableRegistryView<O>
        get() {
            val registry = editor.getRegistry(context) as ObjectRegistry<O>
            return object : SearchableRegistryView<O>(registry, "Select ${registry.objectType}") {
                override fun createObject(name: String): O? = editor.createNewObject(name)

                override fun displayText(option: O): String = editor.extractText(option).now
            }
        }

    init {
        editor.addView(this)
        button.setOnAction {
            @Suppress("UNCHECKED_CAST")
            val initialOption = editor.selected.now?.get<NamedObject>() as O?
            listView.showPopup(context, anchorNode = button, initialOption) { option ->
                @Suppress("UNCHECKED_CAST")
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