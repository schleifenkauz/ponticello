package ponticello.ui.registry

import fxutils.actions.registerShortcuts
import fxutils.letContentFillViewPort
import javafx.event.Event
import javafx.scene.Node
import javafx.scene.control.ScrollPane
import javafx.scene.layout.VBox
import ponticello.model.Settings
import ponticello.model.obj.ConfigurableInstrumentObject
import ponticello.model.obj.ParameterDefObject
import ponticello.model.obj.withName
import reaktive.value.now

abstract class ParameterizedObjectDefPane<T : ConfigurableInstrumentObject>(
    protected val def: T,
    enableActions: Boolean,
) : ToolPane() {
    private val config: ParameterListConfig = object : ParameterListConfig() {
        override fun createNewObject(ev: Event?): ParameterDefObject? {
            val defaultParameters = def.context[Settings].defaultParametersDefs
                .filter { param -> !def.hasParameter(param.name.now) }
            val listView = SearchableParameterDefListView(defaultParameters, "New parameter")
            val param = listView.showPopup(ev) ?: return null
            return param.copy().withName(param.name.now)
        }

        override val enableAddObjectButton: Boolean
            get() = true
    }
    private val parametersList = ObjectListView(def.parameters, config)

    init {
        val content = this.getContent(def)
        val layout = VBox(parametersList, content)
        val scrollPane = ScrollPane(layout).letContentFillViewPort()
        val actions = InstrumentRegistryPane.actions.withContext(def)
        setup(scrollPane, title = null, headerContent = null, if (enableActions) actions else emptyList())
        if (enableActions) registerShortcuts(actions)
        parametersList.registerShortcuts(parametersList.actions)
    }

    protected abstract fun getContent(def: T): Node
}