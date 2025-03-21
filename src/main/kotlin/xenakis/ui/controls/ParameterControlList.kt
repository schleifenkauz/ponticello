package xenakis.ui.controls

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.actions.registerShortcuts
import javafx.scene.Node
import javafx.scene.layout.Region
import org.kordamp.ikonli.codicons.Codicons
import reaktive.value.binding.map
import reaktive.value.now
import xenakis.model.score.ParameterControl
import xenakis.model.score.ParameterControls
import xenakis.model.score.ParameterControls.NamedParameterControl
import xenakis.sc.ControlSpec
import xenakis.sc.NumericalControlSpec
import xenakis.ui.registry.ObjectBoxList
import xenakis.ui.registry.ObjectBoxSource

class ParameterControlList(
    private val controls: ParameterControls
) : ParameterControls.Listener, ObjectBoxSource<NamedParameterControl> {
    override val items: List<NamedParameterControl> get() = controls.all()

    private val editors = mutableMapOf<NamedParameterControl, ControlAssignmentEditor>()
    private val list = ObjectBoxList(this)

    override val enableReordering: Boolean
        get() = true

    init {
        controls.addListener(this, initialize = false)
        list.userData = this //avoid garbage collection, [addListener] stores as weak reference
    }

    fun getContent(): Region = list

    fun addShortcutsTo(node: Node) {
        node.registerShortcuts(list.actions)
    }

    override fun addedControl(control: NamedParameterControl, idx: Int) {
        if (control.spec.now == null) return
        val editor = ControlAssignmentEditor(control)
        editor.setControl(control.now)
        list.added(idx, control)
    }

    override fun removedControl(control: NamedParameterControl) {
        list.removed(control)
    }

    override fun movedControl(control: NamedParameterControl, fromIdx: Int, toIdx: Int) {
        list.moved(control, toIdx)
    }

    override fun reassignedControl(
        namedControl: NamedParameterControl,
        oldControl: ParameterControl,
        control: ParameterControl
    ) {
        val editor = editors.getValue(namedControl)
        editor.setControl(control)
    }

    override fun moveObject(obj: NamedParameterControl, idx: Int) {
        obj.controls.moveControl(obj, idx)
    }

    override fun changedSpec(control: NamedParameterControl, oldSpec: ControlSpec?, newSpec: ControlSpec?) {
        val editor = editors.getValue(control)
        editor.setControl(control.now) //TODO can this be done in a better way?
    }

    override fun getContent(obj: NamedParameterControl): List<Node> {
        val editor = editors.getOrPut(obj) { ControlAssignmentEditor(obj) }
        editor.setControl(obj.now)
        return listOf(editor)
    }

    override fun getActions(obj: NamedParameterControl): List<ContextualizedAction> = actions.withContext(obj)

    override fun removeObject(obj: NamedParameterControl) {
        controls.removeControl(obj.name.now)
    }

    override fun addObject(obj: NamedParameterControl, idx: Int) {
        controls.addControl(obj, idx)
    }

    companion object {
        private val actions = collectActions<NamedParameterControl> {
            addAction("Edit spec") {
                shortcut("Ctrl+P")
                applicableIf { control -> control.spec.map { s -> s is NumericalControlSpec } }
                //editor.obj.def.getParameter(editor.parameter)!!.spec.map { s -> s is NumericalControlSpec }
                icon(Codicons.SYMBOL_PROPERTY)
                executes { control: NamedParameterControl, ev ->
                    ControlSpecPrompt(control).showDialog(ev)
                }
            }
        }
    }
}