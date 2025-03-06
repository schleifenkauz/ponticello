package xenakis.ui.registry

import fxutils.SubWindow
import fxutils.setFixedWidth
import fxutils.styleClass
import hextant.fx.initHextantScene
import javafx.scene.layout.VBox
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import reaktive.value.binding.map
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import xenakis.model.obj.ProcessDefObject
import xenakis.model.registry.ProcessDefRegistry
import xenakis.ui.controls.NamePrompt
import xenakis.ui.impl.CollapsablePane
import xenakis.ui.impl.colorPicker
import xenakis.ui.impl.registerSyncShortcuts

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
        addAction(MaterialDesignC.CONTENT_DUPLICATE, "Duplicate ProcessDef") {
            val initialName = obj.name.now + "_copy"
            val name = NamePrompt(registry, "Name for new duplicate instrument", initialName)
                .showDialog(anchorNode = this) ?: return@addAction
            val copy = obj.copy(name)
            registry.add(copy)
        }
        addAction(Material2AL.CODE, "Edit ProcessDef") { editProcessDef(obj) }
    }

    fun editProcessDef(obj: ProcessDefObject) {
        val window = subWindows.getOrPut(obj) {
            val pane = VBox(
                CollapsablePane("Parameters", ParameterDefsPane(registry.context, obj.parameters)),
                obj.processCode.control
            ) styleClass "synth-def-pane"
            SubWindow(pane, "").apply {
                initOwner(scene.window)
                titleProperty().bind(obj.name.map { name -> "ProcessDef $name" }.asObservableValue())
                resize(900.0, 800.0)
                scene.initHextantScene(registry.context, applyStyle = false)
                registerSyncShortcuts(obj, obj.processCode)
            }
        }
        window.show()
    }
}