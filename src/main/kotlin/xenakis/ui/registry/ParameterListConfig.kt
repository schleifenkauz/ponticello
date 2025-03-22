package xenakis.ui.registry

import hextant.context.Context
import hextant.context.createControl
import hextant.context.withoutUndo
import javafx.scene.Node
import reaktive.Observer
import reaktive.value.forEach
import reaktive.value.now
import xenakis.model.obj.ParameterDefObject
import xenakis.sc.ParameterType
import xenakis.sc.editor.ControlSpecEditor

class ParameterListConfig(private val context: Context) : ObjectBoxConfig<ParameterDefObject> {
    private val observers = mutableMapOf<ParameterDefObject, Observer>()

    override val enableReordering: Boolean
        get() = true

    override fun getContent(obj: ParameterDefObject): List<Node> {
        val editor = makeControlSpecEditor(obj)
        syncSpecWithEditor(obj, editor)
        val specControl = context.createControl(editor)
        return listOf(specControl)
    }

    private fun makeControlSpecEditor(parameter: ParameterDefObject): ControlSpecEditor {
        val editor = ControlSpecEditor()
        editor.selectInitial(ParameterType.Numerical)
        editor.initialize(context)
        context.withoutUndo { editor.setResult(parameter.spec.now) }
        return editor
    }

    private fun syncSpecWithEditor(parameter: ParameterDefObject, editor: ControlSpecEditor) {
        observers[parameter] =
            parameter.spec.forEach { spec ->
                if (editor.result.now != spec) editor.setResult(spec)
            } and editor.result.observe { _, _, new ->
                if (new != parameter.spec.now) parameter.spec.now = new
            }
    }

    override fun onRemoved(obj: ParameterDefObject) {
        observers.remove(obj)?.kill()
    }
}