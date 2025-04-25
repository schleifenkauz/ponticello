package xenakis.ui.registry

import bundles.createBundle
import hextant.context.Context
import hextant.context.withoutUndo
import hextant.core.view.ChoiceEditorControl
import javafx.scene.Node
import reaktive.Observer
import reaktive.value.forEach
import reaktive.value.now
import xenakis.model.obj.ParameterDefObject
import xenakis.sc.ParameterType
import xenakis.sc.editor.ControlSpecEditor

open class ParameterListConfig(private val context: Context) : NamedObjectListConfig<ParameterDefObject> {
    private val observers = mutableMapOf<ParameterDefObject, Observer>()

    override val enableReordering: Boolean
        get() = true

    override fun getItemContent(obj: ParameterDefObject): List<Node> {
        val editor = makeControlSpecEditor(obj)
        syncSpecWithEditor(obj, editor)
        val specControl = ChoiceEditorControl(editor, createBundle())
        specControl.canChoose = false
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