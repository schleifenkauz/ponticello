package xenakis.ui.registry

import bundles.PublicProperty
import fxutils.SubWindow
import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.prompt.SimpleSearchableListView
import fxutils.prompt.YesNoPrompt
import hextant.fx.initHextantScene
import javafx.scene.paint.Color
import org.kordamp.ikonli.material2.Material2MZ
import org.kordamp.ikonli.materialdesign2.MaterialDesignE
import reaktive.value.reactiveValue
import xenakis.model.obj.ConfigurableParameterizedObjectDef
import xenakis.model.obj.ParameterizedObjectDef
import xenakis.model.obj.ProcessDefObject
import xenakis.model.obj.SynthDefObject
import xenakis.model.registry.GlobalDefinitionLibrary
import xenakis.model.registry.NamedObject
import xenakis.model.registry.ObjectRegistry
import xenakis.sc.Identifier
import xenakis.ui.registry.NamedObjectListView.DisplayMode

abstract class ParameterizedObjectDefRegistryPane<T : ParameterizedObjectDef>(
    private val defs: ObjectRegistry<T>,
    private val globalLibrary: PublicProperty<out GlobalDefinitionLibrary<out T>>,
) : ObjectRegistryPane<T>(defs) {
    override val supportedModes: Set<DisplayMode>
        get() = setOf(DisplayMode.DetailsPane, DisplayMode.SubWindow)

    override fun configureSubWindow(window: SubWindow) {
        window.scene.initHextantScene(registry.context, applyStyle = false)
        window.scene.fill = Color.BLACK
    }

    override fun addObject() {
        val globalLib = registry.context[globalLibrary]
        val synthDefsFromGlobal = globalLib.getNames().map(AddObjectOption::ObjectFromGlobalLib)
        val searchableList = AddObjectOptionListView(synthDefsFromGlobal)
        searchableList.enterText(searchText.text)
        val option = searchableList.showPopup(anchorNode = actionBar) ?: return
        createObject(option)
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

    private fun createObject(option: AddObjectOption) {
        when (option) {
            is AddObjectOption.NewObject -> {
                this.createNewObject(option.name)?.let { def ->
                    registry.add(def)
                    listView.select(def)
                }
            }

            is AddObjectOption.ObjectFromGlobalLib -> {
                val name = option.name
                val def = registry.context[globalLibrary].get(name) ?: return
                if (registry.has(name)) {
                    if (YesNoPrompt("Overwrite ${defs.objectType} $name?").showDialog(anchorNode = actionBar) == true) {
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

    override fun getActions(box: ObjectBox<T>): List<ContextualizedAction> = actions.withContext(box)

    companion object {
        private val actions = collectActions<ObjectBox<out ParameterizedObjectDef>> {
            addAction("Sync") {
                icon(Material2MZ.SYNC)
                shortcuts("Ctrl+U")
                executes { box -> box.obj.sync() }
            }
            addAction("Save to global library") {
                icon(MaterialDesignE.EXPORT_VARIANT)
                applicableIf { box -> reactiveValue(box.obj is ConfigurableParameterizedObjectDef) }
                executes { box ->
                    val def = box.obj
                    @Suppress("UNCHECKED_CAST")
                    val library = when (def) {
                        is ProcessDefObject -> def.context[GlobalDefinitionLibrary.processDefs]
                        is SynthDefObject -> def.context[GlobalDefinitionLibrary.synthDefs]
                        else -> error("Cannot save $def to global library")
                    } as GlobalDefinitionLibrary<NamedObject>
                    library.saveToGlobalLib(def, anchorNode = box)
                }
            }
        }
    }
}