package ponticello.ui.registry

import fxutils.actions.registerShortcuts
import fxutils.button
import fxutils.centerChildren
import fxutils.prompt.PromptPlacement
import fxutils.prompt.nextToTarget
import fxutils.registerShortcuts
import fxutils.styleClass
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import ponticello.model.GlobalSettings
import ponticello.model.instr.ConfigurableInstrumentObject
import ponticello.model.instr.ParameterDefObject
import ponticello.model.obj.withName
import ponticello.model.registry.ObjectList
import reaktive.value.now

abstract class ParameterizedObjectDefPane<T : ConfigurableInstrumentObject>(
    protected val def: T,
) : ScrollPane() {
    private val config: ParameterListConfig = object : ParameterListConfig() {
        override fun createNewObject(
            promptPlacement: PromptPlacement,
            list: ObjectList<ParameterDefObject>
        ): ParameterDefObject? {
            val defaultParameters = def.context[GlobalSettings].defaultParametersDefs
                .filter { param -> !def.hasParameter(param.name.now) && def.supports(param.spec.now.type) }
            val listView = ParameterDefSelectorPrompt(
                defaultParameters, "New parameter",
                instrumentObject = def
            )
            val param = listView.showPopup(promptPlacement) ?: return null
            return param.copy().withName(param.name.now)
        }
    }
    private val parametersList = ObjectListView(def.parameters, config, scrollable = false)

    init {
        val layout = VBox(
            3.0,
            HBox(
                5.0,
                Label("Parameters") styleClass "small-heading",
                button("Add") { ev ->
                    val promptPlacement = ev.nextToTarget()
                    parametersList.addObject(promptPlacement)
                }
            ).centerChildren(),
            parametersList,
            Label("Code") styleClass "small-heading",
            this.getContent(def)
        )
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