package xenakis.ui.registry

import fxutils.actions.*
import hextant.context.Context
import hextant.context.createControl
import hextant.context.withoutUndo
import hextant.serial.EditorRoot
import hextant.serial.makeRoot
import javafx.scene.Node
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import org.kordamp.ikonli.material2.Material2MZ
import reaktive.Observer
import reaktive.list.MutableReactiveList
import reaktive.value.ReactiveString
import reaktive.value.forEach
import reaktive.value.now
import xenakis.model.Settings
import xenakis.model.obj.ParameterDefObject
import xenakis.sc.editor.CodeBlockEditor
import xenakis.sc.editor.ControlSpecEditor

class ParameterizedObjectDefPane(
    private val context: Context,
    private val title: ReactiveString,
    private val parameters: MutableReactiveList<ParameterDefObject>,
    private val code: EditorRoot<CodeBlockEditor>
) : ToolPane(), ObjectBoxType<ParameterDefObject> {
    private val observers = mutableMapOf<ParameterDefObject, Observer>()
    private lateinit var observer: Observer
    private val parametersList = ObjectBoxList(parameters.now, this)

    init {
        observeParametersList()
        registerShortcuts(actions.withContext(this))
    }

    private fun observeParametersList() {
        observer = parameters.observeList { ch ->
            if (ch.wasAdded) parametersList.add(ch.index, ch.added)
            if (ch.wasRemoved) {
                parametersList.remove(ch.removed)
                observers.remove(ch.removed)!!.kill()
            }
        }
    }

    override fun getTitle(): ReactiveString = title

    override fun getContent(obj: ParameterDefObject): List<Node> {
        val editor = makeControlSpecEditor(obj)
        syncSpecWithEditor(obj, editor)
        val specControl = context.createControl(editor)
        return listOf(specControl)
    }

    override fun getContent(): Node = VBox(parametersList, code.control)

    override fun deleteObject(obj: ParameterDefObject) {
        parameters.now.remove(obj)
    }

    fun addParameter() {
        val defaultParameters = context[Settings].defaultParametersDefs.now
        val listView = SearchableParameterDefListView(defaultParameters, "New parameter")
        listView.showPopup(header) { newParam ->
            parameters.now.add(newParam)
            val idx = parameters.now.indices.last
            parametersList.select(idx)
        }
    }

    override fun configure(box: ObjectBoxList<ParameterDefObject>.ObjectBox) {

    }

    private fun setupReordering(actionBar: ActionBar, box: HBox, parameter: ParameterDefObject) {

    }

    private fun makeControlSpecEditor(parameter: ParameterDefObject): ControlSpecEditor {
        val editor = ControlSpecEditor(context)
        editor.makeRoot()
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

    companion object {
        private val actions = collectActions<ParameterizedObjectDefPane> {
            addAction("Add parameter") {
                icon(Material2MZ.PLUS)
                shortcuts("Ctrl+PLUS")
                executes { pane -> pane.addParameter() }
            }
        }
    }
}