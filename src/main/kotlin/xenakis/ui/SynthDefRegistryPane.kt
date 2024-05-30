package xenakis.ui

import bundles.PublicProperty
import bundles.publicProperty
import hextant.context.createControl
import hextant.fx.PseudoClasses
import hextant.fx.initHextantScene
import hextant.serial.EditorRoot
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import reaktive.list.reactiveList
import reaktive.value.binding.map
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.randomColor
import xenakis.model.*
import xenakis.sc.Identifier
import xenakis.sc.editor.CodeBlockEditor

class SynthDefRegistryPane(
    private val registry: SynthDefRegistry,
) : SynthDefRegistry.View, VBox() {
    private var selectedBtn: Button? = null

    private val selectorButtons = mutableMapOf<SynthDefObject, Button>()
    private val subWindows = mutableMapOf<SynthDefObject, SubWindow>()

    private val defs = VBox().styleClass("synth-def-list")

    init {
        styleClass("tool-pane")
        initializeLayout()
        registry.addView(this)
    }

    private fun initializeLayout() {
        val label = Label("Synth Definitions").styleClass("tool-pane-heading")
        val space = infiniteSpace()
        val addBtn = Icon.Add.button(action = "Add SynthDef") { addSynthDefEditor() }
        val reloadBtn = Icon.Repeat.button(action = "Reload SynthDefs") { registry.sync() }
        val header = HBox(label, space, addBtn, reloadBtn).styleClass("tool-pane-header")
        header.alignment = Pos.CENTER_LEFT
        header.spacing = 5.0
        children.addAll(header, defs)
    }

    private fun addSynthDefEditor() {
        showTextPrompt("SynthDef name", "", registry.context) { name ->
            if (!Identifier.isValid(name) || registry.hasSynthDef(name)) {
                return@showTextPrompt false
            }
            when {
                name in StandardSynthDefObject.all -> {
                    val standard = showYesNoDialog(
                        "SynthDef '$name' is a standard SynthDef. Do you want to load it? A new SynthDef will be created otherwise.",
                        default = true
                    )
                    if (standard) {
                        registry.addSynthDef(StandardSynthDefObject.all.getValue(name))
                    } else {
                        addCustomizableNewSynthDef(name)
                    }
                }

                registry.synthDescLibContains(name).join() -> {
                    val reference = showYesNoDialog(
                        "SynthDef '$name' is already defined in the global SynthDescLib. " +
                                "Import SynthDef '$name' from SynthDescLib? A new SynthDef will be created otherwise.",
                        default = true
                    )
                    if (reference) addReferencedSynthDef(name)
                    else addCustomizableNewSynthDef(name)
                }

                else -> addCustomizableNewSynthDef(name)
            }
            true
        }
    }

    private fun addCustomizableNewSynthDef(name: String) {
        val ugenGraph = CodeBlockEditor(registry.context)
        val ugenGraphControl = registry.context.createControl(ugenGraph)
        val obj = CustomizableSynthDefObject(
            mutableName = reactiveVariable(name),
            color = reactiveVariable(randomColor()),
            parameters = reactiveList(),
            ugenGraph = EditorRoot(ugenGraph, ugenGraphControl)
        )
        registry.addSynthDef(obj)
        editSynthDef(obj)
    }

    private fun addReferencedSynthDef(name: String) {
        val def = ReferencedSynthDefObject.loadFromSynthDescLib(name)
        registry.addSynthDef(def)
    }

    override fun selectedSynthDef(obj: SynthDefObject?) {
        if (obj == null) {
            selectedBtn?.pseudoClassStateChanged(PseudoClasses.SELECTED, false)
            selectedBtn = null
        } else {
            val selector = selectorButtons[obj] ?: error("selector button for SynthDef ${obj.name.now} not found")
            select(selector)
        }
    }

    override fun addedSynthDef(idx: Int, obj: SynthDefObject) {
        val selector = Button().styleClass("selector-button")
        selectorButtons[obj] = selector
        selector.setOnAction {
            if (selector == selectedBtn) return@setOnAction
            select(selector)
            registry.selectedSynthDef = obj
        }
        val nameDisplay =
            if (obj is RenamableObject) NameControl(obj)
            else label(obj.name) styleClass "name"
        val colorPicker = colorPicker(obj.color)
        colorPicker.prefWidth = 35.0
        val edit = Icon.View.button(action = "Edit SynthDef") { editSynthDef(obj) }
        val remove = Icon.Delete.button(action = "Remove this SynthDef") { registry.removeSynthDef(obj) }
        val box = HBox(selector, nameDisplay, colorPicker, infiniteSpace(), edit, remove)
            .styleClass("synth-def-box")
        defs.children.add(idx, box)
    }

    override fun removedSynthDef(idx: Int, obj: SynthDefObject) {
        defs.children.removeAt(idx)
        subWindows.remove(obj)?.hide()
        selectorButtons.remove(obj)
    }

    fun editSynthDef(obj: SynthDefObject) {
        val window = subWindows.getOrPut(obj) {
            val pane = if (obj is CustomizableSynthDefObject) {
                val pane = VBox(
                    ParameterDefsPane(registry.context, obj.parameters),
                    obj.ugenGraph.control
                ) styleClass "synth-def-pane"
                pane
            } else ParameterInfoPane(obj.parameters)
            SubWindow(pane, "", registry.context).apply {
                titleProperty().bind(obj.name.map { name -> "SynthDef $name" }.asObservableValue())
                width = 1000.0
                height = 1000.0
                setOnShown {
                    if (obj is CustomizableSynthDefObject) {
                        obj.ugenGraph.control.requestLayout()
                    }
                }
                scene.initHextantScene(registry.context, applyStyle = false)
            }
        }
        window.show()
    }

    private fun select(selector: Button) {
        selectedBtn?.pseudoClassStateChanged(PseudoClasses.SELECTED, false)
        selector.pseudoClassStateChanged(PseudoClasses.SELECTED, true)
        selectedBtn = selector
    }

    companion object : PublicProperty<SynthDefRegistryPane> by publicProperty("SynthDefRegistryPane")
}