package xenakis.ui

import hextant.fx.registerShortcuts
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import reaktive.value.now
import xenakis.model.NamedObject
import xenakis.model.ObjectRegistry
import xenakis.model.RenamableObject
import xenakis.sc.Identifier

abstract class SearchableRegistryView<O : NamedObject>(
    private val registry: ObjectRegistry<O>
) : SearchableListView<O>() {
    init {
        registerShortcuts {
            val obj = selectedOption
            if (obj is RenamableObject) {
                on("F2") {
                    hide()
                    showNamePrompt(registry, defaultName = obj.name.now) { newName ->
                        obj.rename(newName)
                    }
                }
            }
            if (obj != null) {
                on("DELETE") {
                    hide()
                    if (showYesNoDialog("Delete ${registry.objectType} ${obj.name.now}?") == true) {
                        registry.remove(obj)
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

    protected open fun displayText(option: O): String = extractText(option)

    override fun extractText(option: O): String = option.name.now
}