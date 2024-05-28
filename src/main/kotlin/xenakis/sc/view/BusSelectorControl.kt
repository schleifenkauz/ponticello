package xenakis.sc.view

import bundles.Bundle
import hextant.codegen.ProvideImplementation
import hextant.context.ControlFactory
import hextant.core.view.SimpleChoiceEditorControl
import xenakis.sc.Bus
import xenakis.sc.Rate
import xenakis.sc.editor.BusSelector
import xenakis.ui.AudioFlowGraphPane

class BusSelectorControl @ProvideImplementation(ControlFactory::class) constructor(
    editor: BusSelector, arguments: Bundle
) : SimpleChoiceEditorControl<Bus>(editor, arguments) {
    init {
        minWidth = 150.0
        root.valueProperty().addListener { _, _, selected ->
            if (selected == createNew) {
                val flowGraphEditor = editor.context[AudioFlowGraphPane]
                val new = flowGraphEditor.createNewBus()
                if (new != null) {
                    root.items.add(new)
                    root.selectionModel.select(new)
                }
            }
        }
    }

    companion object {
        val createNew = Bus("<create-new>", Rate.Audio, 0)
    }
}