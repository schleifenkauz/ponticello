package ponticello.ui.registry

import bundles.createBundle
import hextant.context.Context
import hextant.core.view.ChoiceEditorControl
import javafx.scene.Node
import ponticello.model.obj.ParameterDefObject
import ponticello.sc.editor.ControlSpecEditor
import reaktive.Observer
import reaktive.value.now

open class ParameterListConfig(private val context: Context) : ObjectListDisplayConfig<ParameterDefObject> {
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
        editor.setResult(parameter.spec.now)
        editor.initialize(context)
        return editor
    }

    private fun syncSpecWithEditor(parameter: ParameterDefObject, editor: ControlSpecEditor) {
        observers[parameter] =
            parameter.spec.observe { _, _, spec ->
                if (editor.result.now != spec) editor.setResult(spec)
            } and editor.result.observe { _, _, new ->
                if (new != parameter.spec.now) parameter.spec.now = new
            }
    }

    override fun onRemoved(obj: ParameterDefObject) {
        observers.remove(obj)?.kill()
    }
}