package xenakis.ui

import bundles.PublicProperty
import bundles.publicProperty
import hextant.context.createControl
import hextant.fx.PseudoClasses
import hextant.fx.initHextantScene
import hextant.serial.EditorRoot
import javafx.scene.control.Button
import javafx.scene.layout.VBox
import reaktive.list.reactiveList
import reaktive.value.binding.map
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.randomColor
import xenakis.model.*
import xenakis.sc.editor.CodeBlockEditor

class SynthDefRegistryPane(
    private val registry: SynthDefRegistry,
) : SynthDefRegistry.View, ObjectRegistryPane<SynthDefObject>(registry) {
    private var selectedBtn: Button? = null

    private val selectorButtons = mutableMapOf<SynthDefObject, Button>()
    private val subWindows = mutableMapOf<SynthDefObject, SubWindow>()

    init {
        registry.addView(this)
    }

    override fun reload() {
        registry.sync()
    }

    override fun addObject(name: String) {
        when {
            name in StandardSynthDefObject.all -> {
                val standard = showYesNoDialog(
                    "SynthDef '$name' is a standard SynthDef. Do you want to load it? A new SynthDef will be created otherwise.",
                    default = true
                )
                if (standard) {
                    registry.add(StandardSynthDefObject.all.getValue(name))
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
        registry.add(obj)
        editSynthDef(obj)
    }

    private fun addReferencedSynthDef(name: String) {
        val def = ReferencedSynthDefObject.loadFromSynthDescLib(name)
        registry.add(def)
    }

    override fun selected(obj: SynthDefObject?) {
        selectedBtn?.pseudoClassStateChanged(PseudoClasses.SELECTED, false)
        selectedBtn = null
        if (obj != null) {
            val selector = selectorButtons[obj] ?: error("selector button for SynthDef ${obj.name.now} not found")
            selector.pseudoClassStateChanged(PseudoClasses.SELECTED, true)
            selectedBtn = selector
        }
    }

    override fun ObjectBox<SynthDefObject>.configureObjectBox() {
        val selector = Button().styleClass("selector-button")
        selectorButtons[obj] = selector
        selector.setOnAction {
            if (selector == selectedBtn) return@setOnAction
            registry.selectedSynthDef = obj
        }
        children.add(0, selector)
        val colorPicker = colorPicker(obj.color)
        colorPicker.prefWidth = 30.0
        addExtraControl(colorPicker)
        addAction(Icon.View, "Edit SynthDef") { editSynthDef(obj) }
    }

    override fun removed(obj: SynthDefObject, idx: Int) {
        super.removed(obj, idx)
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

    companion object : PublicProperty<SynthDefRegistryPane> by publicProperty("SynthDefRegistryPane")
}