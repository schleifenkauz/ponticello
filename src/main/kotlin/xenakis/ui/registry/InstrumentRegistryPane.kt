package xenakis.ui.registry

import bundles.createBundle
import fxutils.SubWindow
import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.letContentFillViewPort
import fxutils.prompt.SimpleSearchableListView
import fxutils.prompt.YesNoPrompt
import fxutils.resize
import fxutils.setFixedWidth
import fxutils.showBelow
import fxutils.showRightOf
import fxutils.undecoratedSubWindow
import hextant.fx.initHextantScene
import javafx.application.Platform
import javafx.scene.Node
import javafx.scene.control.ScrollPane
import javafx.scene.input.DataFormat
import javafx.scene.paint.Color
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.kordamp.ikonli.materialdesign2.MaterialDesignE
import reaktive.value.binding.map
import reaktive.value.now
import reaktive.value.reactiveValue
import xenakis.impl.async
import xenakis.impl.canSuperColliderTalkToMe
import xenakis.impl.Logger
import xenakis.model.obj.*
import xenakis.model.obj.CustomizableSynthDefObject
import xenakis.model.obj.InstrumentObject
import xenakis.model.obj.VSTPluginObject
import xenakis.model.registry.GlobalSynthDefLib
import xenakis.model.registry.InstrumentRegistry
import xenakis.sc.Identifier
import xenakis.sc.view.ObjectSelectorControl
import xenakis.ui.controls.NamePrompt
import xenakis.ui.impl.colorPicker

class InstrumentRegistryPane(
    private val registry: InstrumentRegistry,
) : InstrumentRegistry.Listener, ObjectRegistryPane<InstrumentObject>(registry) {
    private val subWindows = mutableMapOf<SynthDefObject, SubWindow>()

    init {
        registry.addView(this)
    }

    override fun dataFormat(obj: InstrumentObject): DataFormat? =
        if (obj is SynthDefObject) SynthDefObject.DATA_FORMAT else null

    private fun saveToGlobalLibrary(obj: CustomizableSynthDefObject) {
        val globalLib = registry.context[GlobalSynthDefLib]
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
                else CustomizableSynthDefObject.create(name)
            }

            else -> return CustomizableSynthDefObject.create(name)
        }
    }

    override fun selected(obj: InstrumentObject?) {
    }

    override fun getContent(obj: InstrumentObject): List<Node> = buildList {
        val colorPicker = colorPicker(obj.color)
        colorPicker.setFixedWidth(30.0)
        add(colorPicker)
        if (obj is VSTPluginObject) {
            add(ObjectSelectorControl(obj.outputSelector, createBundle()))
        }
    }

    override fun getActions(obj: InstrumentObject): List<ContextualizedAction> = collectActions<InstrumentObject> {
        addAction("Edit Instrument") {
            icon { instr ->
                if (instr is CustomizableSynthDefObject) reactiveValue(Material2AL.CODE)
                else reactiveValue(MaterialDesignE.EYE)
            }
            executes { instr -> this@InstrumentRegistryPane.editInstrument(instr) }
        }
        addAction("Save VST plugin configuration") {
            icon(Material2MZ.SAVE)
            executesOn<VSTPluginObject> { obj -> obj.saveConfiguration() }
        }
        addAction("Save to global library") {
            icon(MaterialDesignE.EXPORT_VARIANT)
            executesOn(::saveToGlobalLibrary)
        }
    }.withContext(obj)

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
        val window = subWindows.getOrPut(obj) { makeWindow(obj) }
        if (window.isShowing) window.toFront()
        else window.showRightOf(this)
    }

    private fun makeWindow(obj: SynthDefObject): SubWindow = when (obj) {
        is CustomizableSynthDefObject -> {
            val title = obj.name.map { n -> "SynthDef $n" }
            val pane = ParameterizedObjectDefPane(registry.context, title, obj.parameters, obj.ugenGraph!!, obj::sync)
            undecoratedSubWindow(pane).apply {
                this.scene.initHextantScene(this@InstrumentRegistryPane.registry.context, applyStyle = false)
                this.scene.fill = Color.BLACK
                this.resize(800.0, 800.0)
            }
        }

        else -> {
            val pane = ParameterInfoPane(obj.parameters)
            undecoratedSubWindow(pane).apply {
                this.resize(800.0, 300.0)
            }
        }
    }
}