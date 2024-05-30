package xenakis.sc.view

import bundles.Bundle
import hextant.codegen.ProvideImplementation
import hextant.context.ControlFactory
import hextant.core.view.SimpleChoiceEditorControl
import reaktive.value.reactiveVariable
import xenakis.model.BusObject
import xenakis.sc.Rate
import xenakis.sc.editor.BusSelector
import xenakis.ui.XenakisUI

class BusSelectorControl @ProvideImplementation(ControlFactory::class) constructor(
    editor: BusSelector, arguments: Bundle
) : SimpleChoiceEditorControl<BusObject>(editor, arguments) {
    init {
        minWidth = 150.0
        root.valueProperty().addListener { _, last, selected ->
            if (selected == createNew) {
                root.selectionModel.select(last)
                val busRegistry = editor.context[XenakisUI].busRegistryPane
                val rate = editor.preferredRate ?: Rate.Audio
                val channels = editor.preferredChannels.takeIf { it != -1 } ?: 2
                busRegistry.createNewBus(rate, channels) { new ->
                    root.items.add(new)
                    root.selectionModel.select(new)
                }
            }
        }
    }

    companion object {
        val createNew = BusObject(reactiveVariable("<create-new>"), reactiveVariable(Rate.Audio), reactiveVariable(0))
    }
}