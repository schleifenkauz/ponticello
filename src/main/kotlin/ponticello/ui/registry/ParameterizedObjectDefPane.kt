package ponticello.ui.registry

import fxutils.actions.registerShortcuts
import fxutils.letContentFillViewPort
import javafx.scene.Node
import javafx.scene.control.ScrollPane
import javafx.scene.layout.VBox
import ponticello.model.Settings
import ponticello.model.obj.ConfigurableParameterizedObjectDef
import ponticello.model.obj.ParameterDefObject
import ponticello.ui.launcher.PonticelloApp.Companion.primaryStage
import reaktive.value.now

abstract class ParameterizedObjectDefPane<T : ConfigurableParameterizedObjectDef>(
    protected val def: T,
    enableActions: Boolean,
) : ToolPane() {
    private val config: ParameterListConfig = object : ParameterListConfig(def.context) {
        override fun createNewObject(): ParameterDefObject? {
            val defaultParameters = def.context[Settings].defaultParametersDefs
                .filter { param -> !def.hasParameter(param.name.now) }
            //TODO better placement
            val anchor = parametersList.localToScreen(parametersList.height - 50.0, parametersList.width / 2.0)
            val listView = SearchableParameterDefListView(defaultParameters, "New parameter")
            return listView.showPopup(anchor, def.context[primaryStage])
        }

        override val enableAddObjectButton: Boolean
            get() = true
    }
    private val parametersList = ObjectListView(def.parameters, config)

    init {
        val content = this.getContent(def)
        val layout = VBox(parametersList, content)
        val scrollPane = ScrollPane(layout).letContentFillViewPort()
        val actions = ParameterizedObjectDefRegistryPane.actions.withContext(def)
        setup(scrollPane, title = null, headerContent = null, if (enableActions) actions else emptyList())
        if (enableActions) registerShortcuts(actions)
        parametersList.registerShortcuts(parametersList.actions)
    }

    protected abstract fun getContent(def: T): Node
}