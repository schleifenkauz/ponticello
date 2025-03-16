package xenakis.ui.registry

import hextant.context.Context
import hextant.context.createControl
import hextant.context.withoutUndo
import javafx.scene.Node
import reaktive.Observer
import reaktive.list.MutableReactiveList
import reaktive.value.forEach
import reaktive.value.now
import xenakis.model.obj.ParameterDefObject
import xenakis.sc.editor.ControlSpecEditor

class ParameterListSource(
    private val context: Context,
    private val parameters: MutableReactiveList<ParameterDefObject>
) : ObjectBoxSource<ParameterDefObject> {
    private val observers = mutableMapOf<ParameterDefObject, Observer>()
    private lateinit var observer: Observer

    override val items: List<ParameterDefObject>
        get() = parameters.now

    override fun getContent(obj: ParameterDefObject): List<Node> {
        val editor = makeControlSpecEditor(obj)
        syncSpecWithEditor(obj, editor)
        val specControl = context.createControl(editor)
        return listOf(specControl)
    }

    private fun makeControlSpecEditor(parameter: ParameterDefObject): ControlSpecEditor {
        val editor = ControlSpecEditor()
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

    override fun deleteObject(obj: ParameterDefObject) {
        parameters.now.remove(obj)
    }

    fun syncWithBoxList(parametersList: ObjectBoxList<ParameterDefObject>) {
        observer = parameters.observeList { ch ->
            if (ch.wasAdded) parametersList.add(ch.index, ch.added)
            if (ch.wasRemoved) {
                parametersList.remove(ch.removed)
                observers.remove(ch.removed)!!.kill()
            }
        }
    }

    override val enableReordering: Boolean
        get() = super.enableReordering
}