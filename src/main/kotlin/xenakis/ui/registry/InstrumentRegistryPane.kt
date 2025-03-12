package xenakis.ui.registry

import bundles.createBundle
import fxutils.SubWindow
import fxutils.letContentFillViewPort
import fxutils.prompt.SimpleSearchableListView
import fxutils.prompt.YesNoPrompt
import fxutils.resize
import fxutils.setFixedWidth
import hextant.fx.initHextantScene
import javafx.application.Platform
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.input.TransferMode
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import org.kordamp.ikonli.materialdesign2.MaterialDesignE
import reaktive.value.binding.map
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import xenakis.impl.async
import xenakis.impl.canSuperColliderTalkToMe
import xenakis.model.Logger
import xenakis.model.obj.*
import xenakis.model.registry.GlobalSynthDefLib
import xenakis.model.registry.InstrumentRegistry
import xenakis.sc.Identifier
import xenakis.sc.view.ObjectSelectorControl
import xenakis.ui.controls.NamePrompt
import xenakis.ui.impl.colorPicker
import xenakis.ui.impl.makeSubWindow
import xenakis.ui.impl.registerSyncShortcuts

class InstrumentRegistryPane(
    private val registry: InstrumentRegistry,
) : InstrumentRegistry.Listener, ObjectRegistryPane<InstrumentObject>(registry) {
    private val subWindows = mutableMapOf<SynthDefObject, SubWindow>()

    init {
        registry.addView(this)
    }

    override fun sync() {
        registry.syncAll()
        registry.save()
    }

    override fun addObject() {
        val availablePlugins = VSTPluginObject.availablePlugins(registry.context)
            .map { name -> AddInstrumentOption.VSTPlugin(name) }
        val globalLib = registry.context[GlobalSynthDefLib]
        globalLib.reload()
        val synthDefsFromGlobal = globalLib.get()
            .map { instr -> AddInstrumentOption.SynthDefFromGlobalLib(instr) }
        val options: List<AddInstrumentOption> = synthDefsFromGlobal + availablePlugins
        val searchableList = AddInstrumentOptionListView(options)
        searchableList.enterText(searchText.text)
        searchableList.showPopup(anchorNode = this) { option ->
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
    ) : SimpleSearchableListView<AddInstrumentOption>(options, "Add instrument") {
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
                        if (YesNoPrompt("Overwrite SynthDef $name?").showDialog(anchorNode = this) == true) {
                            registry.overwrite(option.instrument)
                            option.instrument.sync()
                        }
                    }
                } else {
                    registry.add(option.instrument)
                    option.instrument.sync()
                }
            }

            is AddInstrumentOption.VSTPlugin -> {
                Platform.runLater {
                    val name = NamePrompt(registry, "Name for new VSTPlugin instance", option.pluginName)
                        .showDialog(anchorNode = this) ?: return@runLater
                    val plugin = VSTPluginObject.create(registry.context, name, option.pluginName)
                    registry.add(plugin)
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
            canSuperColliderTalkToMe && registry.synthDescLibContains(name) -> {
                val reference = YesNoPrompt(
                    "SynthDef '$name' is already defined in the global SynthDescLib. " +
                            "Import SynthDef '$name' from SynthDescLib? A new SynthDef will be created otherwise.",
                    default = true
                ).showDialog(anchorNode = this) ?: return null
                return if (reference) ReferencedSynthDefObject.get(name)
                else CustomizableSynthDefObject.create(name, registry.context)
            }

            else -> return CustomizableSynthDefObject.create(name, registry.context)
        }
    }

    override fun selected(obj: InstrumentObject?) {
    }

    override fun ObjectBox<InstrumentObject>.configureObjectBox() {
        val colorPicker = colorPicker(obj.color)
        colorPicker.setFixedWidth(30.0)
        addExtraControl(colorPicker)
        addAction(Material2AL.CODE, "Edit SynthDef") { editInstrument(obj) }
        if (obj.canCopy) {
            addAction(MaterialDesignC.CONTENT_DUPLICATE, "Duplicate SynthDef") {
                val initialName = obj.name.now + "_copy"
                val name = NamePrompt(registry, "Name for new duplicate instrument", initialName)
                    .showDialog(anchorNode = this) ?: return@addAction
                val copy = obj.copy(name)
                registry.add(copy)
            }
        }
        if (obj is VSTPluginObject) {
            val outSelectorControl = ObjectSelectorControl(obj.outputSelector, createBundle())
            addExtraControl(outSelectorControl)
            addAction(Material2MZ.SAVE, description = "Save VST plugin configuration") { obj.saveConfiguration() }
        }
        if (obj is SynthDefObject) {
            addGrabber(SynthDefObject.DATA_FORMAT, TransferMode.COPY)
        }
        if (obj is CustomizableSynthDefObject) {
            val globalLib = registry.context[GlobalSynthDefLib]
            addAction(MaterialDesignE.EXPORT_VARIANT, description = "Save to global SynthDef library") {
                async(timeLimit = 10000) {
                    synchronized(globalLib) {
                        globalLib.reload()
                        Platform.runLater {
                            val name = obj.name.now
                            if (!globalLib.has(name) ||
                                YesNoPrompt(
                                    "Overwrite SynthDef $name in global library?",
                                    default = true
                                ).showDialog(anchorNode = this) == true
                            ) {
                                globalLib.push(obj)
                                Logger.confirm(
                                    "Saved SynthDef '${obj.name.now}' to global library.",
                                    Logger.Category.Instruments
                                )
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
    }

    fun editInstrument(obj: InstrumentObject) {
        if (obj is VSTPluginObject) {
            obj.showEditor()
            return
        }
        obj as SynthDefObject
        val window = subWindows.getOrPut(obj) {
            if (obj is CustomizableSynthDefObject) {
                val pane = ScrollPane(
                    VBox(
                        ParameterDefsPane(registry.context, obj.parameters),
                        obj.ugenGraph?.control ?: Label("No code")
                    )
                ).letContentFillViewPort()
                val w = makeSubWindow(pane, "", registry.context)
                w.titleProperty().bind(obj.name.map { name -> "SynthDef $name" }.asObservableValue())
                w.scene.initHextantScene(registry.context, applyStyle = false)
                w.scene.fill = Color.web("#1e1f22")
                w.resize(800.0, 800.0)
                if (obj.ugenGraph != null) w.registerSyncShortcuts(obj, obj.ugenGraph)
                w
            } else {
                val pane = ParameterInfoPane(obj.parameters)
                makeSubWindow(pane, obj.name.now, registry.context, type = SubWindow.Type.Popup).apply {
                    initOwner(scene.window)
                    this.resize(800.0, 300.0)
                }
            }
        }
        window.show()
    }
}