package ponticello.ui.registry

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.prompt.SimpleSearchableListView
import fxutils.prompt.YesNoPrompt
import fxutils.setFixedWidth
import javafx.event.Event
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.ScrollPane
import javafx.scene.input.DataFormat
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.kordamp.ikonli.materialdesign2.MaterialDesignE
import org.kordamp.ikonli.materialdesign2.MaterialDesignS
import ponticello.impl.canSuperColliderTalkToMe
import ponticello.model.obj.*
import ponticello.model.project.PonticelloProject
import ponticello.model.project.instruments
import ponticello.model.registry.GlobalDefinitionLibrary
import ponticello.model.registry.InstrumentRegistry
import ponticello.sc.Identifier
import ponticello.ui.dock.SearchableToolPaneState
import ponticello.ui.dock.Side
import ponticello.ui.dock.ToolPane
import ponticello.ui.dock.ToolPaneState
import ponticello.ui.impl.colorPicker
import ponticello.ui.registry.ObjectListView.DisplayMode
import reaktive.list.toReactiveList
import reaktive.value.binding.impl.notNull
import reaktive.value.binding.map
import reaktive.value.now

class InstrumentRegistryPane(
    private val instruments: InstrumentRegistry,
) : ObjectRegistryPane<InstrumentObject>(instruments) {
    override val type: Type
        get() = InstrumentRegistryPane

    override val supportedModes: Set<DisplayMode>
        get() = setOf(DisplayMode.DetailsPane, DisplayMode.SubWindow, DisplayMode.Collapsable)

    override fun defaultState(): ToolPaneState = SearchableToolPaneState.window

    override fun detailWindowIcon(obj: InstrumentObject): Ikon =
        if (obj is CustomizableSynthDefObject || obj is ProcessDefObject) Material2AL.CODE
        else MaterialDesignE.EYE

    override fun getContent(obj: InstrumentObject, mode: DisplayMode): Parent? = when (obj) {
        is CustomizableSynthDefObject -> {
            val enableActions = mode == DisplayMode.SubWindow
            SynthDefObjectPane(obj, enableActions)
        }

        is ProcessDefObject -> {
            val enableActions = mode == DisplayMode.SubWindow
            ProcessDefObjectPane(obj, enableActions)
        }

        is ReferencedSynthDefObject -> ScrollPane(ParameterInfoPane(obj.parameters.toReactiveList()))
        is NoInstrument -> null
    }

    override val dataFormat: DataFormat
        get() = InstrumentObject.DATA_FORMAT

    override fun createNewObject(name: String, ev: Event?): InstrumentObject? {
        when {
            canSuperColliderTalkToMe && instruments.synthDescLibContains(name) -> {
                val reference = YesNoPrompt(
                    "SynthDef '$name' is already defined in the global SynthDescLib. " +
                            "Import SynthDef '$name' from SynthDescLib? A new SynthDef will be created otherwise.",
                    default = true
                ).showDialog(anchorNode = this) ?: return null
                return if (reference) ReferencedSynthDefObject.get(name)
                else CustomizableSynthDefObject.create(name)
            }

            else -> return CustomizableSynthDefObject.create(name)
        }
    }

    override fun getHeaderContent(obj: InstrumentObject): List<Node> = listOf(colorPicker(obj.color).setFixedWidth(30.0))

    override fun addObject(ev: Event?) {
        val globalLib = registry.context[GlobalDefinitionLibrary.instruments]
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
            is AddObjectOption.ObjectFromGlobalLib -> "${instruments.objectType}: ${option.name}"
            else -> "<invalid>"
        }

        override fun extractText(option: AddObjectOption): String = when (option) {
            is AddObjectOption.NewObject -> option.name
            is AddObjectOption.ObjectFromGlobalLib -> option.name
        }
    }

    private fun createObject(option: AddObjectOption, ev: Event?): InstrumentObject? {
        when (option) {
            is AddObjectOption.NewObject -> {
                return createNewObject(option.name, ev)
            }

            is AddObjectOption.ObjectFromGlobalLib -> {
                val name = option.name
                val def = registry.context[GlobalDefinitionLibrary.instruments].get(name) ?: return null
                return def.takeIf {
                    !registry.has(name) || YesNoPrompt("Overwrite ${instruments.objectType} $name?").showDialog(ev) == true
                }
            }
        }
    }

    override fun extraHeaderActions(): List<ContextualizedAction> =
        super.extraHeaderActions() + actions.withContext(this)

    companion object : Type(8, "Instruments") {
        override val icon: Ikon
            get() = MaterialDesignS.SINE_WAVE

        override val shortcuts: Array<String>
            get() = arrayOf("F3")

        override val defaultSide: Side
            get() = Side.RIGHT

        override fun createToolPane(project: PonticelloProject): ToolPane = InstrumentRegistryPane(project.instruments)

        val actions = collectActions<InstrumentRegistryPane> {
            addAction("Sync") {
                icon(Material2MZ.SYNC)
                shortcuts("Ctrl+U")
                enableWhen { p -> p.listView.selectedBox().notNull() }
                executes { p ->
                    val selected = p.listView.selectedBox().now?.obj ?: return@executes
                    selected.sync()
                }
            }
            addAction("Save to global library") {
                icon(MaterialDesignE.EXPORT_VARIANT)
                enableWhen { p -> p.listView.selectedBox().map { box -> box?.obj is ConfigurableInstrumentObject } }
                executes { p, ev ->
                    val def = p.listView.selectedBox().now?.obj as? ConfigurableInstrumentObject ?: return@executes
                    val library = def.context[GlobalDefinitionLibrary.instruments]
                    library.saveToGlobalLib(def, ev)
                }
            }
        }
    }
}