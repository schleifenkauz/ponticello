package xenakis.sc.view

import bundles.Bundle
import hextant.core.view.SimpleChoiceEditorControl
import reaktive.value.now
import xenakis.model.registry.NamedObject
import xenakis.model.registry.ObjectReference
import xenakis.model.registry.reference
import xenakis.sc.editor.ObjectSelector
import xenakis.ui.registry.SearchableRegistryView

class ObjectSelectorControl<O : NamedObject>(
    private val selector: ObjectSelector<O>, arguments: Bundle,
) : SimpleChoiceEditorControl<ObjectReference<O>>(selector, arguments) {
    public override fun showChoicePopup() {
        val registry = selector.getRegistry()
        val view = object : SearchableRegistryView<O>(registry, "Select ${registry.objectType}") {
            override fun createObject(name: String): O? = selector.createNewObject(name)

            override fun displayText(option: O): String = selector.toString(option).now
        }
        view.setFilter { obj -> selector.filter(obj) }
        val option = view.showPopup(anchorNode = this, initialOption = selector.result.now.get())
        if (option != null) selector.select(option.reference())
    }
}