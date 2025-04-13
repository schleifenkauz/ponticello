package xenakis.ui.registry

import fxutils.actions.collectActions
import fxutils.actions.isShiftDown
import fxutils.actions.registerShortcuts
import fxutils.letContentFillViewPort
import javafx.scene.Node
import javafx.scene.control.ScrollPane
import javafx.scene.layout.VBox
import org.kordamp.ikonli.material2.Material2MZ
import reaktive.value.ReactiveString
import xenakis.model.Settings
import xenakis.model.obj.ParameterizedObjectDef
import xenakis.ui.launcher.XenakisApp.Companion.primaryStage

abstract class ParameterizedObjectDefPane<T: ParameterizedObjectDef>(
    protected val def: T,
    title: ReactiveString,
) : ToolPane() {
    private val config = ParameterListConfig(def.context)
    private val parametersList = NamedObjectListView(def.parameters, config)

    init {
        val content = this.getContent(def)
        val scrollPane = ScrollPane(VBox(parametersList, content)).letContentFillViewPort()
        setup(title, scrollPane, actions = actions.withContext(this))
        parametersList.registerShortcuts(parametersList.actions)
    }

    protected abstract fun getContent(def: T): Node

    protected abstract fun update()

    private fun addParameter() {
        val defaultParameters = def.context[Settings].defaultParametersDefs
        val listView = SearchableParameterDefListView(
            defaultParameters, "New parameter", null,
            def.context[primaryStage], actionBar.localToScreen(0.0, actionBar.height)
        )
        val newParam = listView.showPopup() ?: return
        def.parameters.add(newParam)
        val idx = def.parameters.indices.last
        parametersList.select(idx)
    }

    companion object {
        private val actions = collectActions<ParameterizedObjectDefPane<*>> {
            addAction("Add parameter") {
                icon(Material2MZ.PLUS)
                shortcuts("Ctrl+P")
                executes { pane -> pane.addParameter() }
            }
            addAction("Update SuperCollider object") {
                icon(Material2MZ.SYNC)
                shortcuts("Ctrl+Shift?+U")
                executes { pane, ev ->
                    pane.update()
                    if (ev.isShiftDown()) pane.scene.window.hide()
                }
            }
        }
    }
}