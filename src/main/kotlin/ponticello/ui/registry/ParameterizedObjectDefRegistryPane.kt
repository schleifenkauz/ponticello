package ponticello.ui.registry

import bundles.PublicProperty
import fxutils.SubWindow
import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.prompt.SimpleSearchableListView
import fxutils.prompt.YesNoPrompt
import javafx.event.Event
import javafx.scene.paint.Color
import org.kordamp.ikonli.material2.Material2MZ
import org.kordamp.ikonli.materialdesign2.MaterialDesignE
import ponticello.model.obj.ConfigurableParameterizedObjectDef
import ponticello.model.obj.ParameterizedObjectDef
import ponticello.model.obj.ProcessDefObject
import ponticello.model.obj.SynthDefObject
import ponticello.model.registry.GlobalDefinitionLibrary
import ponticello.model.registry.NamedObject
import ponticello.model.registry.ObjectRegistry
import ponticello.sc.Identifier
import ponticello.ui.registry.ObjectListView.DisplayMode

abstract class ParameterizedObjectDefRegistryPane<T : ParameterizedObjectDef>(
    private val defs: ObjectRegistry<T>,
    private val globalLibrary: PublicProperty<out GlobalDefinitionLibrary<out T>>,
) : ObjectRegistryPane<T>(defs) {
    override val supportedModes: Set<DisplayMode>
        get() = setOf(DisplayMode.DetailsPane, DisplayMode.SubWindow)

    override fun configureSubWindow(window: SubWindow, obj: T) {
        window.scene.fill = Color.BLACK
    }

    override fun addObject(ev: Event?) {
        val globalLib = registry.context[globalLibrary]
        val synthDefsFromGlobal = globalLib.getNames().map(AddObjectOption::ObjectFromGlobalLib)
        val searchableList = AddObjectOptionListView(synthDefsFromGlobal)
        searchableList.enterText(searchText.text)
        val option = searchableList.showPopup(ev) ?: return
        createObject(option, ev)
    }

    private sealed interface AddObjectOption {
        data class NewObject(val name: String) : AddObjectOption

        data class ObjectFromGlobalLib(val name: String) : AddObjectOption
    }

    private inner class AddObjectOptionListView(
        options: List<AddObjectOption>,
    ) : SimpleSearchableListView<AddObjectOption>(options, "Add ${registry.objectType}") {
        override fun makeOption(text: String): AddObjectOption? {
            return if (Identifier.isValid(text) && !registry.has(text)) AddObjectOption.NewObject(text)
            else null
        }

        override fun displayText(option: AddObjectOption): String = when (option) {
            is AddObjectOption.ObjectFromGlobalLib -> "${defs.objectType}: ${option.name}"
            else -> "<invalid>"
        }

        override fun extractText(option: AddObjectOption): String = when (option) {
            is AddObjectOption.NewObject -> option.name
            is AddObjectOption.ObjectFromGlobalLib -> option.name
        }
    }

    private fun createObject(option: AddObjectOption, ev: Event?) {
        when (option) {
            is AddObjectOption.NewObject -> {
                this.createNewObject(option.name, ev)?.let { def ->
                    registry.add(def)
                    listView.select(def)
                }
            }

            is AddObjectOption.ObjectFromGlobalLib -> {
                val name = option.name
                val def = registry.context[globalLibrary].get(name) ?: return
                if (registry.has(name)) {
                    if (YesNoPrompt("Overwrite ${defs.objectType} $name?").showDialog(ev) == true) {
                        registry.overwrite(def)
                    } else return
                } else {
                    registry.add(def)
                }
                listView.select(def)
                def.sync()
            }
        }
    }

    override fun getActions(box: ObjectBox<T>): List<ContextualizedAction> = actions.withContext(box.obj)

    companion object {
        val actions = collectActions<ParameterizedObjectDef> {
            addAction("Sync") {
                icon(Material2MZ.SYNC)
                shortcuts("Ctrl+U")
                executes { obj -> obj.sync() }
            }
            addAction("Save to global library") {
                icon(MaterialDesignE.EXPORT_VARIANT)
                applicableIf { obj -> obj is ConfigurableParameterizedObjectDef }
                executes { def, ev ->
                    @Suppress("UNCHECKED_CAST")
                    val library = when (def) {
                        is ProcessDefObject -> def.context[GlobalDefinitionLibrary.processDefs]
                        is SynthDefObject -> def.context[GlobalDefinitionLibrary.synthDefs]
                        else -> error("Cannot save $def to global library")
                    } as GlobalDefinitionLibrary<NamedObject>
                    library.saveToGlobalLib(def, ev)
                }
            }
        }
    }
}