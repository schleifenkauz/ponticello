package xenakis.ui.registry

import fxutils.SubWindow
import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.setFixedWidth
import hextant.fx.initHextantScene
import javafx.scene.Node
import javafx.scene.control.ScrollPane
import javafx.scene.layout.VBox
import org.kordamp.ikonli.material2.Material2AL
import reaktive.value.binding.map
import reaktive.value.fx.asObservableValue
import xenakis.model.obj.ProcessDefObject
import xenakis.model.registry.ProcessDefRegistry
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
        val processDef = ProcessDefObject.newEmpty(name)
        registry.add(processDef)
        return processDef
    }

    override fun getContent(obj: ProcessDefObject): List<Node> {
        val colorPicker = colorPicker(obj.color)
        colorPicker.setFixedWidth(30.0)
        return listOf(colorPicker)
    }

    private val actions = collectActions {
        addAction("Edit ProcessDef") {
            icon(Material2AL.CODE)
            executes { obj -> editProcessDef(obj) }
        }
    }

    override fun getActions(obj: ProcessDefObject): List<ContextualizedAction> = actions.withContext(obj)

    fun editProcessDef(obj: ProcessDefObject) {
        val window = subWindows.getOrPut(obj) {
            val pane = ScrollPane(
                VBox(
                    ParameterDefsPane(registry.context, obj.parameters),
                    obj.processCode.control
                )
            )
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