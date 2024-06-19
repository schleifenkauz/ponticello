package xenakis.ui

import bundles.PublicProperty
import bundles.createBundle
import bundles.publicProperty
import hextant.context.withoutUndo
import hextant.fx.PseudoClasses
import hextant.fx.initHextantScene
import hextant.fx.registerShortcuts
import hextant.serial.readJson
import hextant.serial.snapshot
import javafx.application.Platform
import javafx.scene.control.Button
import javafx.scene.input.DataFormat
import javafx.scene.layout.VBox
import reaktive.value.binding.map
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import xenakis.impl.async
import xenakis.model.*
import xenakis.sc.view.ObjectSelectorControl
import java.util.logging.Level
import java.util.logging.Logger

class InstrumentRegistryPane(
    private val registry: InstrumentRegistry,
) : InstrumentRegistry.View, ObjectRegistryPane<InstrumentObject>(registry) {
    private var selectedBtn: Button? = null

    private val selectorButtons = mutableMapOf<InstrumentObject, Button>()
    private val subWindows = mutableMapOf<SynthDefObject, SubWindow>()

    init {
        registry.addView(this)
        setupDropArea(
            { db -> db.hasContent(DataFormat.FILES) && db.files.all { f -> f.name == "instruments.json" } }
        ) { ev ->
            for (file in ev.dragboard.files) {
                try {
                    val instruments = file.readJson<InstrumentRegistry>()
                    for (instr in instruments.all()) {
                        if (!registry.has(instr.name.now)) {
                            registry.add(instr)
                        }
                    }
                } catch (ex: Exception) {
                    logger.log(Level.SEVERE, ex.message, ex)
                    alertError("Error adding instruments: ${ex.message}")
                }
            }
        }
    }

    override fun reload() {
        registry.syncAll()
    }

    override fun addObject() {
        async {
            val availablePlugins = VSTPluginObject.availablePlugins(registry.context).map { name -> "Plugin: $name" }
            val default = "New SynthDef"
            val options = listOf(default) + availablePlugins
            Platform.runLater {
                showCreateNewDialog(options, default, ::createObject)
            }
        }
    }

    private fun createObject(type: String, name: String): InstrumentObject? = when (type) {
        "New SynthDef" -> createSynthDef(name)
        else -> VSTPluginObject.create(registry.context, name, type.removePrefix("Plugin: "))
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

            registry.synthDescLibContains(name).join() -> {
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
        addAction(Icon.View, "Edit SynthDef") { editSynthDef(obj) }
        if (obj is VSTPluginObject) {
            val outSelectorControl = ObjectSelectorControl(obj.outputSelector, createBundle())
            addExtraControl(outSelectorControl)
            addAction(Icon.Save, description = "Save VST plugin configuration") { obj.saveConfiguration() }
        }
    }

    override fun removed(obj: InstrumentObject, idx: Int) {
        super.removed(obj, idx)
        subWindows.remove(obj)?.hide()
        selectorButtons.remove(obj)
    }

    fun editSynthDef(obj: InstrumentObject) {
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

    companion object : PublicProperty<InstrumentRegistryPane> by publicProperty("SynthDefRegistryPane") {
        private val logger = Logger.getLogger("InstrumentRegistryPane")
    }
}