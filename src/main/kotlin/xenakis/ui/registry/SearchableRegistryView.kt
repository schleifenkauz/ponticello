package xenakis.ui.registry

import fxutils.prompt.SearchableListView
import fxutils.prompt.YesNoPrompt
import fxutils.registerShortcuts
import fxutils.runFXWithTimeout
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import reaktive.value.now
import xenakis.model.obj.RenamableObject
import xenakis.model.registry.NamedObject
import xenakis.model.registry.ObjectRegistry
import xenakis.sc.Identifier
import xenakis.ui.controls.NamePrompt

abstract class SearchableRegistryView<O : NamedObject>(
    val registry: ObjectRegistry<O>, title: String
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
                        ).showDialog(anchorNode = getBox(obj)) ?: return@runFXWithTimeout
                        obj.rename(newName)
                    }
                }
            }
            if (obj != null) {
                on("DELETE") {
                    hide()
                    runFXWithTimeout(25) {
                        val question = "Delete ${registry.objectType} ${obj.name.now}?"
                        val really = YesNoPrompt(question).showDialog(anchorNode = getBox(obj))
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