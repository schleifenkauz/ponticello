package ponticello.ui.registry

import fxutils.SubWindow
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
import javafx.scene.paint.Color
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.kordamp.ikonli.materialdesign2.MaterialDesignE
import org.kordamp.ikonli.materialdesign2.MaterialDesignS
import ponticello.impl.canSuperColliderTalkToMe
import ponticello.model.obj.*
import ponticello.model.registry.GlobalDefinitionLibrary
import ponticello.model.registry.InstrumentRegistry
import ponticello.sc.Identifier
import ponticello.ui.dock.ToolPaneState
import ponticello.ui.impl.colorPicker
import ponticello.ui.registry.ObjectListView.DisplayMode
import reaktive.list.toReactiveList

class InstrumentRegistryPane(
    private val instruments: InstrumentRegistry,
) : ObjectRegistryPane<InstrumentObject>(instruments) {
    override val title: String
        get() = "Instruments"

    override val icon: Ikon
        get() = MaterialDesignS.SINE_WAVE

    override val supportedModes: Set<DisplayMode>
        get() = setOf(DisplayMode.DetailsPane, DisplayMode.SubWindow)

    override fun defaultState(): ToolPaneState = ToolPaneState.docked(ToolPaneState.Side.RIGHT)

    override fun configureSubWindow(window: SubWindow, obj: InstrumentObject) {
        window.scene.fill = Color.BLACK
    }

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

    override fun dataFormat(obj: InstrumentObject): DataFormat = InstrumentObject.DATA_FORMAT

    public override fun createNewObject(name: String, ev: Event?): InstrumentObject? {
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

    override fun getItemContent(obj: InstrumentObject): List<Node> = listOf(colorPicker(obj.color).setFixedWidth(30.0))

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

    private fun createObject(option: AddObjectOption, ev: Event?) {
        when (option) {
            is AddObjectOption.NewObject -> {
                this.createNewObject(option.name, ev)?.let { def ->
                    registry.add(def)
                }
            }

            is AddObjectOption.ObjectFromGlobalLib -> {
                val name = option.name
                val def = registry.context[GlobalDefinitionLibrary.instruments].get(name) ?: return
                if (registry.has(name)) {
                    if (YesNoPrompt("Overwrite ${instruments.objectType} $name?").showDialog(ev) == true) {
                        registry.overwrite(def)
                    } else return
                } else {
                    registry.add(def)
                }
                def.sync()
            }
        }
    }

    override fun getActions(box: ObjectBox<InstrumentObject>): List<ContextualizedAction> = actions.withContext(box.obj)

    companion object {
        val actions = collectActions<InstrumentObject> {
            addAction("Sync") {
                icon(Material2MZ.SYNC)
                shortcuts("Ctrl+U")
                executes { obj -> obj.sync() }
            }
            addAction("Save to global library") {
                icon(MaterialDesignE.EXPORT_VARIANT)
                applicableIf { obj -> obj is ConfigurableInstrumentObject }
                executes { def, ev ->
                    val library = def.context[GlobalDefinitionLibrary.instruments]
                    library.saveToGlobalLib(def, ev)
                }
            }
        }
    }
}