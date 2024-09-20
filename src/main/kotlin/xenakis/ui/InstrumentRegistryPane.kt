package xenakis.ui

import bundles.PublicProperty
import bundles.createBundle
import bundles.publicProperty
import hextant.context.withoutUndo
import hextant.fx.PseudoClasses
import hextant.fx.initHextantScene
import hextant.fx.registerShortcuts
import hextant.serial.snapshot
import javafx.application.Platform
import javafx.scene.control.Button
import javafx.scene.layout.VBox
import reaktive.value.binding.map
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import xenakis.impl.async
import xenakis.impl.isMyComputerDumb
import xenakis.model.*
import xenakis.sc.Identifier
import xenakis.sc.view.ObjectSelectorControl
import java.util.logging.Logger

class InstrumentRegistryPane(
    private val registry: InstrumentRegistry,
) : InstrumentRegistry.Listener, ObjectRegistryPane<InstrumentObject>(registry) {
    private var selectedBtn: Button? = null

    private val selectorButtons = mutableMapOf<InstrumentObject, Button>()
    private val subWindows = mutableMapOf<SynthDefObject, SubWindow>()

    init {
        registry.addView(this)
    }

    override fun sync() {
        registry.syncAll()
    }

    override fun addObject() {
        /*            val availablePlugins = VSTPluginObject.availablePlugins(registry.context)
                        .map { name -> AddInstrumentOption.VSTPlugin(name) }*/
        val globalLib = registry.context[GlobalSynthDefLib]
        globalLib.reload()
        val synthDefsFromGlobal = globalLib.get()
            .map { instr -> AddInstrumentOption.SynthDefFromGlobalLib(instr) }
        val options: List<AddInstrumentOption> = synthDefsFromGlobal// + availablePlugins
        val searchableList = AddInstrumentOptionListView(options)
        searchableList.enterText(searchText.text)
        searchableList.showPopup(registry.context, "Add instrument", anchorNode = this) { option ->
            createObject(option)
        }
    }

    sealed interface AddInstrumentOption {
        data class NewSynthDef(val name: String) : AddInstrumentOption

        data class SynthDefFromGlobalLib(val instrument: SynthDefObject) : AddInstrumentOption

        data class VSTPlugin(val pluginName: String) : AddInstrumentOption
    }

    private inner class AddInstrumentOptionListView(
        options: List<AddInstrumentOption>
    ) : SimpleSearchableListView<AddInstrumentOption>(options) {
        override fun makeOption(text: String): AddInstrumentOption? {
            return if (Identifier.isValid(text) && !registry.has(text)) AddInstrumentOption.NewSynthDef(text)
            else null
        }

        override fun displayText(option: AddInstrumentOption): String = when (option) {
            is AddInstrumentOption.SynthDefFromGlobalLib -> "SynthDef: ${option.instrument.name.now}"
            is AddInstrumentOption.VSTPlugin -> "VSTPlugin: ${option.pluginName}"
            else -> "<invalid>"
        }

        override fun extractText(option: AddInstrumentOption): String = when (option) {
            is AddInstrumentOption.NewSynthDef -> option.name
            is AddInstrumentOption.SynthDefFromGlobalLib -> option.instrument.name.now
            is AddInstrumentOption.VSTPlugin -> option.pluginName
        }
    }

    private fun createObject(option: AddInstrumentOption) {
        when (option) {
            is AddInstrumentOption.NewSynthDef -> {
                createSynthDef(option.name)?.let { def ->
                    registry.add(def)
                    editInstrument(def)
                }
            }

            is AddInstrumentOption.SynthDefFromGlobalLib -> {
                val name = option.instrument.name.now
                if (registry.has(name)) {
                    Platform.runLater {
                        if (showYesNoDialog("Overwrite SynthDef $name?") == true) {
                            registry.overwrite(option.instrument)
                        }
                    }
                } else {
                    registry.add(option.instrument)
                }
            }

            is AddInstrumentOption.VSTPlugin -> {
                Platform.runLater {
                    showNamePrompt(registry, option.pluginName) { name ->
                        val plugin = VSTPluginObject.create(registry.context, name, option.pluginName)
                        registry.add(plugin)
                    }
                }
            }
        }
    }

    override fun addObject(name: String): SynthDefObject? {
        val obj = createSynthDef(name) ?: return null
        registry.add(obj)
        return obj
    }

    fun createSynthDef(name: String): SynthDefObject? {
        when {
            name in StandardSynthDefObject.all -> {
                val standard = showYesNoDialog(
                    "SynthDef '$name' is a standard SynthDef. Do you want to load it? A new SynthDef will be created otherwise.",
                    default = true
                ) ?: return null
                return if (standard) StandardSynthDefObject.all.getValue(name)
                else CustomizableSynthDefObject.create(name, registry.context)
            }

            !isMyComputerDumb && registry.synthDescLibContains(name).join() -> {
                val reference = showYesNoDialog(
                    "SynthDef '$name' is already defined in the global SynthDescLib. " +
                            "Import SynthDef '$name' from SynthDescLib? A new SynthDef will be created otherwise.",
                    default = true
                ) ?: return null
                return if (reference) ReferencedSynthDefObject.loadFromSynthDescLib(name)
                else CustomizableSynthDefObject.create(name, registry.context)
            }

            else -> return CustomizableSynthDefObject.create(name, registry.context)
        }
    }

    override fun selected(obj: InstrumentObject?) {
        selectedBtn?.pseudoClassStateChanged(PseudoClasses.SELECTED, false)
        selectedBtn = null
        if (obj != null) {
            val selector = selectorButtons[obj] ?: error("selector button for SynthDef ${obj.name.now} not found")
            selector.pseudoClassStateChanged(PseudoClasses.SELECTED, true)
            selectedBtn = selector
        }
    }

    override fun ObjectBox<InstrumentObject>.configureObjectBox() {
        val selector = Button().styleClass("selector-button")
        selectorButtons[obj] = selector
        selector.setOnAction {
            if (selector == selectedBtn) {
                registry.selectedInstrument = null
                return@setOnAction
            }
            registry.selectedInstrument = obj
            registry.context[XenakisUI].toolSelector.select(ToolSelector.Tool.Synth)
        }
        children.add(0, selector)
        val colorPicker = colorPicker(obj.color)
        colorPicker.setFixedWidth(30.0)
        addExtraControl(colorPicker)
        addAction(Icon.View, "Edit SynthDef") { editInstrument(obj) }
        if (obj is VSTPluginObject) {
            val outSelectorControl = ObjectSelectorControl(obj.outputSelector, createBundle())
            addExtraControl(outSelectorControl)
            addAction(Icon.Save, description = "Save VST plugin configuration") { obj.saveConfiguration() }
        }
        if (obj is CustomizableSynthDefObject) {
            val globalLib = registry.context[GlobalSynthDefLib]
            addAction(Icon.AddGlobal, description = "Save to global SynthDef library") {
                async(timeLimit = 10000) {
                    synchronized(globalLib) {
                        globalLib.reload()
                        Platform.runLater {
                            val name = obj.name.now
                            if (!globalLib.has(name) ||
                                showYesNoDialog("Overwrite SynthDef $name in global library?", default = true) == true
                            ) {
                                globalLib.push(obj)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun removed(obj: InstrumentObject, idx: Int) {
        super.removed(obj, idx)
        subWindows.remove(obj)?.hide()
        selectorButtons.remove(obj)
    }

    fun editInstrument(obj: InstrumentObject) {
        if (obj is VSTPluginObject) {
            obj.showEditor()
            return
        }
        obj as SynthDefObject
        val window = subWindows.getOrPut(obj) {
            if (obj is CustomizableSynthDefObject) {
                val pane = VBox(
                    ParameterDefsPane(registry.context, obj.parameters),
                    obj.ugenGraph.control
                ) styleClass "synth-def-pane"
                SubWindow(pane, "", registry.context).apply {
                    titleProperty().bind(obj.name.map { name -> "SynthDef $name" }.asObservableValue())
                    resize(900.0, 800.0)
                    scene.initHextantScene(registry.context, applyStyle = false)
                    scene.registerShortcuts {
                        on("Ctrl+S") {
                            val editor = obj.ugenGraph.editor
                            editor.context.withoutUndo {
                                editor.snapshot().reconstructObject(editor)
                            }
                            obj.sync()
                            notifyInfo("Synchronized SynthDef '${obj.name.now}'")
                        }
                        on("Ctrl+Shift+S") {
                            obj.sync()
                            hide()
                        }
                    }
                }
            } else {
                val pane = ParameterInfoPane(obj.parameters)
                SubWindow(pane, obj.name.now, registry.context, type = SubWindow.Type.Popup).apply {
                    resize(800.0, 300.0)
                }
            }
        }
        window.show()
    }

    companion object : PublicProperty<InstrumentRegistryPane> by publicProperty("InstrumentRegistryPane") {
        private val logger = Logger.getLogger("InstrumentRegistryPane")
    }
}