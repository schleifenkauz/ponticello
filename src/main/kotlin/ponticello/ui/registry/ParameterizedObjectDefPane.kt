package ponticello.ui.registry

import fxutils.actions.registerShortcuts
import fxutils.registerShortcuts
import javafx.event.Event
import javafx.scene.Node
import javafx.scene.control.ScrollPane
import javafx.scene.layout.VBox
import ponticello.model.GlobalSettings
import ponticello.model.obj.ConfigurableInstrumentObject
import ponticello.model.obj.ParameterDefObject
import ponticello.model.obj.withName
import ponticello.model.registry.ObjectList
import reaktive.value.now

abstract class ParameterizedObjectDefPane<T : ConfigurableInstrumentObject>(
    protected val def: T,
) : ScrollPane() {
    private val config: ParameterListConfig = object : ParameterListConfig() {
        override fun createNewObject(ev: Event?, list: ObjectList<ParameterDefObject>): ParameterDefObject? {
            val defaultParameters = def.context[GlobalSettings].defaultParametersDefs
                .filter { param -> !def.hasParameter(param.name.now) && def.supports(param.spec.now.type) }
            val listView = SearchableParameterDefListView(
                defaultParameters, "New parameter",
                instrumentObject = def, context = def.context
            )
            val param = listView.showPopup(ev) ?: return null
            return param.copy().withName(param.name.now)
        }

        override val enableAddObjectButton: Boolean
            get() = true
    }
    private val parametersList = ObjectListView(def.parameters, config, scrollable = false)

    init {
        val layout = VBox(parametersList, this.getContent(def))
        this.content = layout
//        val actions = InstrumentRegistryPane.actions.withContext(def)
//        if (ownWindow) {
//            val actionsBar = ActionBar(actions, "medium-icon-button")
//            check(def is RenamableObject) { "$def doesn't implement RenambleObject" }
//            val nameControl = NameControl(def)
//            val header = HBox(nameControl, infiniteSpace(), actionsBar).centerChildren()
//            layout.children.add(0, header)
//            registerShortcuts(actions)
//        }
        registerShortcuts {
            on("Ctrl+U") {
                def.sync()
            }
        }
        parametersList.registerShortcuts(parametersList.actions)
    }

    protected abstract fun getContent(def: T): Node
}