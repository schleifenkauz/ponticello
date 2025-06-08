package ponticello.ui.controls

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.actions.makeButton
import fxutils.centerChildren
import fxutils.prompt.InfoPrompt
import fxutils.sync
import fxutils.undo.UndoManager
import hextant.serial.EditorRoot
import javafx.scene.Node
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import org.kordamp.ikonli.evaicons.Evaicons
import org.kordamp.ikonli.materialdesign2.MaterialDesignS
import ponticello.model.obj.ParameterizedObject
import ponticello.model.player.ActiveObject
import ponticello.model.player.ActiveScoreObject
import ponticello.model.score.ParameterControlList
import ponticello.model.score.ScoreObject
import ponticello.model.score.controls.ParameterControl
import ponticello.model.score.controls.UGenControl
import ponticello.model.score.controls.getNumericalValue
import ponticello.sc.ControlSpec
import ponticello.sc.NumericalControlSpec
import ponticello.sc.editor.ScExprExpander
import ponticello.ui.score.ScoreObjectView
import reaktive.value.binding.map
import reaktive.value.fx.asObservableValue
import reaktive.value.now

data object UGenControlType : ControlType<UGenControl>() {
    override fun applicableOn(obj: ParameterizedObject, spec: ControlSpec): Boolean =
        spec is NumericalControlSpec

    override fun createDetailInput(
        namedControl: ParameterControlList.NamedParameterControl,
        control: UGenControl,
        view: ScoreObjectView?,
    ): Node {
        val actions = actions.withContext(Pair(namedControl, view))
        val window = makeCodePaneWindow(control.expr, control.context, namedControl, actions)
        val showWindowButton = showWindowAction.withContext(window).makeButton("medium-icon-button")
        if (namedControl.parentObject is ScoreObject) {
            val displayToggle = CheckBox()
                .sync(control.display, "Display UGen", namedControl.context[UndoManager])
            displayToggle.disableProperty().bind(
                control.expr.editor.result.map { expr ->
                    expr.getLfo() == null
                }.asObservableValue()
            )
            return HBox(5.0, Label("Display"), displayToggle, showWindowButton).centerChildren()
        } else return showWindowButton
    }

    override fun createInitialControl(
        obj: ParameterizedObject,
        spec: ControlSpec?,
        oldControl: ParameterControl,
        namedControl: ParameterControlList.NamedParameterControl,
        anchorNode: Region,
    ): UGenControl {
        val editor = ScExprExpander()
        val root = EditorRoot(editor)
        if (oldControl.getNumericalValue() != null) {
            editor.setInitialText(oldControl.getNumericalValue().toString())
        } else editor.setInitialText("")
        return UGenControl(root)
    }

    override fun onSelected(namedControl: ParameterControlList.NamedParameterControl, control: UGenControl, view: ScoreObjectView?) {
        val actions = actions.withContext(Pair(namedControl, null))
        val window = makeCodePaneWindow(control.expr, control.context, namedControl, actions)
        window.show()

    }

    override fun actions(
        namedControl: ParameterControlList.NamedParameterControl,
        control: UGenControl,
        view: ScoreObjectView?,
    ): List<ContextualizedAction> = actions.withContext(Pair(namedControl, view))

    private val actions = collectActions<Pair<ParameterControlList.NamedParameterControl, ScoreObjectView?>> {
        addAction("Update") {
            icon(MaterialDesignS.SYNC)
            shortcut("Ctrl+U")
            executes { (ctrl) ->
                val ugen = ctrl.now as UGenControl
                ugen.update.fire()
            }
        }
        addAction("Scope") {
            icon(Evaicons.ACTIVITY)
            executes { (ctrl, view), ev ->
                val ugen = ctrl.now as UGenControl
                val activeObject = getActiveObject(ctrl, view)
                val parameter = ctrl.name.now
                if (activeObject != null) {
                    ugen.scope(activeObject, parameter)
                } else {
                    InfoPrompt("Object is not played currently").showDialog(ev)
                }
            }
        }
    }

    private fun getActiveObject(ctrl: ParameterControlList.NamedParameterControl, view: ScoreObjectView?): ActiveObject? {
        val activeObjects = ctrl.parentObject.activeObjects()
        return if (view != null) {
            activeObjects
                .filterIsInstance<ActiveScoreObject>()
                .find { obj ->
                    val absolutePosition = view.absolutePosition + obj.player.pane.absolutePosition
                    obj.absolutePosition == absolutePosition
                }
        } else activeObjects.singleOrNull()
    }
}