package xenakis.ui.registry

import fxutils.PseudoClasses
import fxutils.actions.ActionBar
import fxutils.actions.collectActions
import fxutils.actions.isShiftDown
import fxutils.actions.registerShortcuts
import fxutils.infiniteSpace
import fxutils.setupDragging
import fxutils.styleClass
import hextant.context.Context
import hextant.context.createControl
import hextant.context.withoutUndo
import hextant.serial.makeRoot
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.input.MouseEvent
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import reaktive.Observer
import reaktive.list.MutableReactiveList
import reaktive.value.forEach
import reaktive.value.now
import xenakis.model.Settings
import xenakis.model.obj.ParameterDefObject
import xenakis.sc.editor.ControlSpecEditor
import xenakis.ui.controls.NameControl

class ParameterDefsPane(
    private val context: Context,
    private val parameters: MutableReactiveList<ParameterDefObject>
) : VBox() {
    private val observers = mutableMapOf<ParameterDefObject, Observer>()
    private val observer: Observer
    private val parameterBoxes = VBox()
    private val header = createHeader()
    private var selectedBox: Node? = null

    private val parameterActions = collectActions {
        addAction("Reorder") {
            icon(MaterialDesignR.REORDER_HORIZONTAL)
        }
        addAction("Remove parameter") {
            icon(Material2AL.DELETE)
            executes { p -> parameters.now.remove(p) }
        }
    }

    init {
        children.addAll(header, parameterBoxes)
        observer = parameters.observeList { ch ->
            if (ch.wasAdded) addedParameter(ch.added, ch.index)
            if (ch.wasRemoved) removedParameter(ch.removed, ch.index)
        }
        for ((idx, param) in parameters.now.withIndex()) addedParameter(param, idx)
        registerShortcuts(actions.withContext(this))
    }

    private fun createHeader(): HBox = HBox(
        Label("Parameters") styleClass "heading",
        infiniteSpace(),
        ActionBar(actions.withContext(this), buttonStyle = "large-icon-button")
    )

    fun addParameter() {
        val defaultParameters = context[Settings].defaultParametersDefs.now
        val listView = SearchableParameterDefListView(defaultParameters, "New parameter")
        listView.showPopup(header) { newParam ->
            parameters.now.add(newParam)
            val idx = parameters.now.indices.last
            select(parameterBoxes.children[idx])
        }
    }

    private fun removedParameter(parameter: ParameterDefObject, index: Int) {
        parameterBoxes.children.removeAt(index)
        observers.remove(parameter)!!.kill()
    }

    private fun addedParameter(parameter: ParameterDefObject, index: Int) {
        val nameDisplay = NameControl(parameter)
        val editor = makeControlSpecEditor(parameter)
        observeSpec(parameter, editor)
        val specControl = context.createControl(editor)
        val actionBar = ActionBar(parameterActions.withContext(parameter), buttonStyle = "medium-icon-button")
        val box = HBox(nameDisplay, specControl, infiniteSpace(), actionBar) styleClass "object-box"
        box.addEventFilter(MouseEvent.MOUSE_CLICKED) { select(box) }
        setupReordering(actionBar, box, parameter)
        parameterBoxes.children.add(index, box)
    }

    private fun select(box: Node) {
        selectedBox?.pseudoClassStateChanged(PseudoClasses.SELECTED, false)
        selectedBox = box
        box.pseudoClassStateChanged(PseudoClasses.SELECTED, true)
    }

    private fun setupReordering(actionBar: ActionBar, box: HBox, parameter: ParameterDefObject) {
        val grabber = actionBar.getButton(parameterActions.getAction("Reorder"))
        grabber.setupDragging(
            onPressed = { box.viewOrder = 100.0 },
            relocateBy = { _, _, _, _, dy -> box.translateY = dy },
            onReleased = {
                box.viewOrder = 0.0
                var idx = parameterBoxes.children.binarySearchBy(box.layoutY + box.translateY) { b -> b.layoutY }
                if (idx < 0) idx = -(idx + 1)
                val oldIndex = parameterBoxes.children.indexOf(box)
                if (idx != oldIndex) {
                    parameters.now.removeAt(oldIndex)
                    if (oldIndex < idx) idx -= 1
                    parameters.now.add(idx, parameter)
                }
                box.translateY = 0.0
            }
        )
    }

    private fun makeControlSpecEditor(parameter: ParameterDefObject): ControlSpecEditor {
        val editor = ControlSpecEditor(context)
        editor.makeRoot()
        context.withoutUndo { editor.setResult(parameter.spec.now) }
        return editor
    }

    private fun observeSpec(parameter: ParameterDefObject, editor: ControlSpecEditor) {
        observers[parameter] =
            parameter.spec.forEach { spec ->
                if (editor.result.now != spec) editor.setResult(spec)
            } and editor.result.observe { _, _, new ->
                if (new != parameter.spec.now) parameter.spec.now = new
            }
    }

    companion object {
        private val actions = collectActions<ParameterDefsPane> {
            addAction("Add parameter") {
                icon(Material2MZ.PLUS)
                shortcuts("Ctrl+PLUS")
                executes { pane -> pane.addParameter() }
            }
            addAction("Delete selected") {
                shortcut("Ctrl+DELETE")
                executes { pane ->
                    if (pane.selectedBox == null) return@executes
                    val idx = pane.parameterBoxes.children.indexOf(pane.selectedBox)
                    pane.parameters.now.removeAt(idx)
                }
            }
            addAction("Navigate") {
                shortcut("Shift?+TAB")
                executes { pane, ev ->
                    val boxes = pane.parameterBoxes.children
                    if (pane.selectedBox != null) {
                        val idx = boxes.indexOf(pane.selectedBox)
                        val newIdx = when {
                            idx == 0 && ev.isShiftDown() -> boxes.indices.last
                            idx == boxes.indices.last && !ev.isShiftDown() -> 0
                            ev.isShiftDown() -> idx - 1
                            else -> idx + 1
                        }
                        pane.select(boxes[newIdx])
                    } else if (boxes.isNotEmpty()) {
                        pane.select(if (ev.isShiftDown()) boxes.last() else boxes.first())
                    }
                }
            }
        }
    }
}