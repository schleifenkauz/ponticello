package xenakis.sc.view

import bundles.Bundle
import hextant.codegen.ProvideImplementation
import hextant.context.ControlFactory
import hextant.core.editor.SimpleChoiceEditor
import hextant.core.view.SimpleChoiceEditorControl
import xenakis.sc.Group
import xenakis.ui.GroupRegistryPane

class GroupSelectorControl @ProvideImplementation(ControlFactory::class) constructor(
    editor: SimpleChoiceEditor<Group>, arguments: Bundle
) : SimpleChoiceEditorControl<Group>(editor, arguments) {
    init {
        minWidth = 150.0
        root.valueProperty().addListener { _, lastSelected, selected ->
            if (selected == createNew) {
                root.selectionModel.select(lastSelected)
                GroupRegistryPane.addNewGroup(context) { group ->
                    root.items.add(group)
                    root.selectionModel.select(group)
                }
            }
        }
    }

    companion object {
        val createNew = Group("<create-new>")
    }
}