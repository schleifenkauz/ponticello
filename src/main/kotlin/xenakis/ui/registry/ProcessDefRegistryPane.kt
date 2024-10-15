package xenakis.ui.registry

import hextant.fx.initHextantScene
import javafx.scene.layout.VBox
import reaktive.value.binding.map
import reaktive.value.fx.asObservableValue
import xenakis.model.obj.ProcessDefObject
import xenakis.model.registry.ProcessDefRegistry
import xenakis.ui.Icon
import xenakis.ui.impl.*

class ProcessDefRegistryPane(
    registry: ProcessDefRegistry
) : SuperColliderObjectRegistryPane<ProcessDefObject>(registry) {
    private val subWindows = mutableMapOf<ProcessDefObject, SubWindow>()

    init {
        registry.addListener(this)
    }

    override fun addObject(name: String): ProcessDefObject {
        val processDef = ProcessDefObject.newEmpty(name, registry.context)
        registry.add(processDef)
        return processDef
    }

    override fun ObjectBox<ProcessDefObject>.configureObjectBox() {
        val colorPicker = colorPicker(obj.color)
        colorPicker.setFixedWidth(30.0)
        addExtraControl(colorPicker)
        addAction(Icon.View, "Edit ProcessDef") { editProcessDef(obj) }
    }

    fun editProcessDef(obj: ProcessDefObject) {
        val window = subWindows.getOrPut(obj) {
            val pane = VBox(
                CollapsablePane("Parameters", ParameterDefsPane(registry.context, obj.parameters)),
                obj.processCode.control
            ) styleClass "synth-def-pane"
            SubWindow(pane, "", registry.context, owner = scene.window).apply {
                titleProperty().bind(obj.name.map { name -> "ProcessDef $name" }.asObservableValue())
                resize(900.0, 800.0)
                scene.initHextantScene(registry.context, applyStyle = false)
                registerSyncShortcuts(obj, obj.processCode)
            }
        }
        window.show()
    }
}