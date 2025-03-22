package xenakis.ui.registry

import fxutils.SubWindow
import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.setFixedWidth
import fxutils.showRightOf
import fxutils.undecoratedSubWindow
import hextant.fx.initHextantScene
import javafx.scene.Node
import org.kordamp.ikonli.material2.Material2AL
import reaktive.value.binding.map
import xenakis.model.obj.ProcessDefObject
import xenakis.model.registry.ProcessDefRegistry
import xenakis.ui.impl.colorPicker

class ProcessDefRegistryPane(
    registry: ProcessDefRegistry
) : SuperColliderObjectRegistryPane<ProcessDefObject>(registry) {
    private val subWindows = mutableMapOf<ProcessDefObject, SubWindow>()

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
            val title = obj.name.map { n -> "ProcessDef $n" }
            val pane = ParameterizedObjectDefPane(registry.context, title, obj.parameters, obj.processCode, obj::sync)
            undecoratedSubWindow(pane).apply {
                initOwner(scene.window)
                resize(900.0, 800.0)
                scene.initHextantScene(registry.context, applyStyle = false)
            }
        }
        if (window.isShowing) window.toFront()
        else window.showRightOf(this)
    }
}