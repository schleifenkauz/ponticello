package xenakis.ui

import hextant.fx.registerShortcuts
import hextant.fx.runFXWithTimeout
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import reaktive.value.now
import xenakis.model.NamedObject
import xenakis.model.ObjectRegistry
import xenakis.model.RenamableObject
import xenakis.sc.Identifier
import xenakis.ui.prompt.NamePrompt
import xenakis.ui.prompt.YesNoPrompt

abstract class SearchableRegistryView<O : NamedObject>(
    private val registry: ObjectRegistry<O>, title: String
) : SearchableListView<O>(title) {
    init {
        registerShortcuts {
            val obj = selectedOption
            if (obj is RenamableObject) {
                on("F2") {
                    hide()
                    runFXWithTimeout {
                        val newName = NamePrompt(
                            registry,
                            "Rename ${registry.objectType} ${obj.name.now}",
                            initialName = obj.name.now
                        ).showDialog(registry.context, anchorNode = getBox(obj)) ?: return@runFXWithTimeout
                        obj.rename(newName)
                    }
                }
            }
            if (obj != null) {
                on("DELETE") {
                    hide()
                    runFXWithTimeout(25) {
                        val question = "Delete ${registry.objectType} ${obj.name.now}?"
                        val really = YesNoPrompt(question).showDialog(registry.context, anchorNode = getBox(obj))
                        if (really == true) registry.remove(obj)
                    }
                }
            }
        }
    }

    override fun options(): List<O> = registry.all()

    override fun createCell(option: O): Node {
        val label = Label(displayText(option))
        val box = HBox(label)
        return box
    }

    override fun makeOption(text: String): O? {
        if (!Identifier.isValid(text) || registry.has(text)) return null
        val obj = createObject(text) ?: return null
        registry.add(obj)
        return obj
    }

    protected abstract fun createObject(name: String): O?

    override fun extractText(option: O): String = option.name.now
}