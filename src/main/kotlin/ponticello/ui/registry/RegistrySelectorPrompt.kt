package ponticello.ui.registry

import fxutils.prompt.SelectorPrompt
import fxutils.prompt.YesNoPrompt
import fxutils.registerShortcuts
import fxutils.runFXWithTimeout
import ponticello.model.obj.RenamableObject
import ponticello.model.registry.NamedObject
import ponticello.model.registry.NamedObjectList
import ponticello.sc.Identifier
import ponticello.ui.controls.RenamePrompt
import reaktive.value.now

abstract class RegistrySelectorPrompt<O : NamedObject>(
    val registry: NamedObjectList<O>, title: String
) : SelectorPrompt<O>(title) {
    private var excluded: () -> Collection<O> = { emptyList() }

    init {
        content.registerShortcuts {
            val obj = selectedOption
            if (obj is RenamableObject) {
                on("F2") {
                    hide()
                    runFXWithTimeout {
                        RenamePrompt(
                            obj,
                            "Rename ${registry.objectType} ${obj.name.now}",
                        ).showDialog(anchorNode = getBox(obj)!!)
                    }
                }
            }
            if (obj != null) {
                on("DELETE") {
                    hide()
                    runFXWithTimeout(25) {
                        val question = "Delete ${registry.objectType} ${obj.name.now}?"
                        val really = YesNoPrompt(question).showDialog(anchorNode = getBox(obj)!!)
                        if (really == true) registry.remove(obj)
                    }
                }
            }
        }
    }

    fun exclude(excluded: () -> Collection<O>): RegistrySelectorPrompt<O> {
        this.excluded = excluded
        return this
    }

    override fun options(): List<O> = registry.all() - excluded().toSet()

    override fun makeOption(text: String): O? {
        if (!Identifier.isValid(text) || registry.has(text)) return null
        val obj = createObject(text) ?: return null
        registry.add(obj)
        return obj
    }

    protected abstract fun createObject(name: String): O?

    override fun extractText(option: O): String = option.name.now
}