package ponticello.sc.view

import bundles.Bundle
import hextant.core.view.SimpleChoiceEditorControl
import reaktive.value.now
import ponticello.model.registry.NamedObject
import ponticello.model.registry.ObjectReference
import ponticello.model.registry.reference
import ponticello.sc.editor.ObjectSelector
import ponticello.ui.registry.SearchableRegistryView

class ObjectSelectorControl<O : NamedObject>(
    private val selector: ObjectSelector<O>, arguments: Bundle,
) : SimpleChoiceEditorControl<ObjectReference<O>>(selector, arguments) {
    public override fun showChoicePopup() {
        val registry = selector.getList()
        val view = object : SearchableRegistryView<O>(registry, "Select ${registry.objectType}") {
            override fun createObject(name: String): O? = selector.createNewObject(name)

            override fun displayText(option: O): String = selector.toString(option).now
        }
        view.setFilter { obj -> selector.filter(obj) }
        val option = view.showPopup(anchorNode = this, initialOption = selector.result.now.get())
        if (option != null) selector.select(option.reference())
    }
}