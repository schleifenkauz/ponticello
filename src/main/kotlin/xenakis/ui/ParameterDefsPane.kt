package xenakis.ui

import hextant.context.Context
import hextant.context.createControl
import hextant.context.withoutUndo
import hextant.serial.makeRoot
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import reaktive.Observer
import reaktive.list.MutableReactiveList
import reaktive.value.now
import xenakis.model.ParameterDefObject
import xenakis.model.Settings
import xenakis.sc.Identifier
import xenakis.sc.NumericalControlSpec
import xenakis.sc.editor.ControlSpecEditor

class ParameterDefsPane(
    private val context: Context,
    private val parameters: MutableReactiveList<ParameterDefObject>
) : VBox() {
    private val observers = mutableMapOf<ParameterDefObject, Observer>()
    private val observer: Observer

    init {
        styleClass("tool-pane")
        observer = parameters.observeList { ch ->
            if (ch.wasAdded) addedParameter(ch.added, ch.index)
            if (ch.wasRemoved) removedParameter(ch.removed, ch.index)
        }
        for ((idx, param) in parameters.now.withIndex()) addedParameter(param, idx)
        children.add(button("Add Parameter") { addParameter() })
    }

    private fun addParameter() {
        showTextPrompt("Parameter name", "", context) { name ->
            if (!Identifier.isValid(name)) return@showTextPrompt false
            if (parameters.now.any { p -> p.name.now == name }) return@showTextPrompt false
            val spec = context[Settings].getDefaultControlSpec(name) ?: NumericalControlSpec.DEFAULT
            val parameter = ParameterDefObject(name, spec)
            parameters.now.add(parameter)
            true
        }
    }

    private fun removedParameter(parameter: ParameterDefObject, index: Int) {
        children.removeAt(index)
        observers.remove(parameter)!!.kill()
    }

    private fun addedParameter(parameter: ParameterDefObject, index: Int) {
        val nameDisplay = NameControl(parameter)
        val editor = ControlSpecEditor(context)
        editor.makeRoot()
        context.withoutUndo { editor.setResult(parameter.spec.now) }
        observers[parameter] = parameter.spec.bind(editor.result)
        val specControl = context.createControl(editor)
        val removeBtn = Icon.Delete.button(action = "Remove parameter") { parameters.now.remove(parameter) }
        val box = HBox(nameDisplay, specControl, infiniteSpace(), removeBtn) styleClass "object-box"
        children.add(index, box)
    }
}