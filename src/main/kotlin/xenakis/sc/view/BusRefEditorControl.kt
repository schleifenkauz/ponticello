package xenakis.sc.view

import bundles.Bundle
import hextant.codegen.ProvideImplementation
import hextant.context.ControlFactory
import hextant.core.view.SimpleChoiceEditorControl
import xenakis.sc.Bus
import xenakis.sc.Rate
import xenakis.sc.editor.BusRefEditor
import xenakis.ui.AudioFlowGraphEditor

class BusRefEditorControl @ProvideImplementation(ControlFactory::class) constructor(
    editor: BusRefEditor,
    arguments: Bundle
) : SimpleChoiceEditorControl<Bus>(editor, arguments) {
    init {
        root.items.add(createNew)
        root.valueProperty().addListener { _, _, selected ->
            if (selected == createNew) {
                val flowGraphEditor = editor.context[AudioFlowGraphEditor]
                val new = flowGraphEditor.createNewBus()
                root.items.add(new)
                if (new != null) root.selectionModel.select(new)
            }
        }
    }

    companion object {
        private val createNew = Bus("<create-new>", Rate.Audio, 0, null)
    }
}