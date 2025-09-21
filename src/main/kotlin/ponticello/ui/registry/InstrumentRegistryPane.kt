package ponticello.ui.registry

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.prompt.SimpleSelectorPrompt
import fxutils.prompt.YesNoPrompt
import fxutils.setFixedWidth
import javafx.event.Event
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.ColorPicker
import javafx.scene.control.ScrollPane
import javafx.scene.input.DataFormat
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.kordamp.ikonli.materialdesign2.MaterialDesignE
import org.kordamp.ikonli.materialdesign2.MaterialDesignF
import org.kordamp.ikonli.materialdesign2.MaterialDesignS
import ponticello.model.obj.*
import ponticello.model.project.INSTRUMENTS
import ponticello.model.project.PonticelloProject
import ponticello.model.project.instruments
import ponticello.model.registry.GlobalDefinitionLibrary
import ponticello.model.registry.InstrumentRegistry
import ponticello.model.registry.ObjectList
import ponticello.ui.controls.NamePrompt
import ponticello.ui.dock.ListToolPaneState
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
) : ObjectRegistryPane<InstrumentObject>(instruments, INSTRUMENTS.serializer) {
    override val type: Type
        get() = InstrumentRegistryPane

    override val supportedModes: Set<DisplayMode>
        get() = setOf(DisplayMode.DetailsPane, DisplayMode.SubWindow, DisplayMode.Collapsable)

    override val canDuplicate: Boolean
        get() = true

    override fun defaultState(): ToolPaneState = ListToolPaneState.window

    override fun detailWindowIcon(obj: InstrumentObject): Ikon =
        if (obj is CustomizableSynthDefObject || obj is ProcessDefObject) Material2AL.CODE
        else MaterialDesignE.EYE

    override fun getContent(obj: InstrumentObject, box: ObjectBox<InstrumentObject>): Parent? = when (obj) {
        is CustomizableSynthDefObject -> {
            SynthDefObjectPane(obj)
        }

        is ProcessDefObject -> {
            val enableActions = box.currentMode == DisplayMode.SubWindow
            ProcessDefObjectPane(obj, enableActions)
        }

        is ReferencedSynthDefObject -> ScrollPane(ParameterInfoPane(obj.parameters.toReactiveList()))
        is VSTInstrumentObject -> null
        is NoInstrument -> null
    }

    override val dataFormat: DataFormat
        get() = InstrumentObject.DATA_FORMAT

    override fun getHeaderContent(obj: InstrumentObject): List<Node> {
        val picker =
            if (obj is ConfigurableInstrumentObject) colorPicker(obj.color).setFixedWidth(30.0)
            else ColorPicker(obj.color.now).also { it.isDisable = true }
        return listOf(picker)
    }

    override fun createNewObject(ev: Event?, list: ObjectList<InstrumentObject>): InstrumentObject? {
        val option = SimpleSelectorPrompt(InstrumentType.entries, "Instrument type").showPopup(ev) ?: return null
        val name = NamePrompt(instruments, "$option name", "")
            .showDialog(ev) ?: return null
        return when (option) {
            InstrumentType.SynthDef -> when {
                instruments.synthDescLibContains(name) -> {
                    val reference = YesNoPrompt(
                        "SynthDef '$name' is already defined in the global SynthDescLib. " +
                                "Import SynthDef '$name' from SynthDescLib? A new SynthDef will be created otherwise.",
                        default = true
                    ).showDialog(anchorNode = this) ?: return null
                    return if (reference) ReferencedSynthDefObject.get(name)
                    else CustomizableSynthDefObject.create(name)
                }

                else -> CustomizableSynthDefObject.create(name)
            }

            InstrumentType.ProcessDef -> ProcessDefObject.newEmpty(name)
        }
    }

    private enum class InstrumentType {
        SynthDef, ProcessDef;
    }

    override fun extraHeaderActions(): List<ContextualizedAction> =
        super.extraHeaderActions() + actions.withContext(this)

    companion object : Type(8, "Instruments") {
        override val icon: Ikon
            get() = MaterialDesignS.SINE_WAVE

        override val shortcut: String
            get() = "F3"

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
                icon(MaterialDesignF.FILE_UPLOAD_OUTLINE)
                enableWhen { p -> p.listView.selectedBox().map { box -> box?.obj is ConfigurableInstrumentObject } }
                executes { p, ev ->
                    val def = p.listView.selectedBox().now?.obj as? ConfigurableInstrumentObject ?: return@executes
                    val library = def.context[GlobalDefinitionLibrary.instruments]
                    library.saveToGlobalLib(def, ev)
                }
            }
            addAction("Load from global library") {
                icon(MaterialDesignF.FILE_DOWNLOAD_OUTLINE)
                executes { p, ev ->
                    val library = p.registry.context[GlobalDefinitionLibrary.instruments]
                    val names = library.getNames()
                    val selected =
                        SimpleSelectorPrompt(names, "Select instrument to load").showPopup(ev) ?: return@executes
                    val def = library.get(selected) ?: return@executes
                    if (p.instruments.has(def.name.now)) {
                        val overwrite = YesNoPrompt(
                            "Instrument '${def.name.now}' already exists in project. Overwrite?", default = false
                        ).showDialog(ev) ?: return@executes
                        if (overwrite) {
                            p.instruments.overwrite(def)
                            def.sync()
                        }
                    } else {
                        p.instruments.add(def)
                        def.sync()
                    }
                }
            }
        }
    }
}