package ponticello.ui.controls

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.actions.detailsAction
import fxutils.controls.CheckBox
import fxutils.opacity
import fxutils.prompt.InfoPrompt
import fxutils.undo.UndoManager
import hextant.serial.EditorRoot
import javafx.scene.Node
import javafx.scene.layout.Region
import org.kordamp.ikonli.evaicons.Evaicons
import org.kordamp.ikonli.materialdesign2.MaterialDesignS
import ponticello.model.obj.ParameterizedObject
import ponticello.model.player.ActiveObject
import ponticello.model.player.ActiveScoreObject
import ponticello.model.score.ParameterControlList
import ponticello.model.score.controls.ParameterControl
import ponticello.model.score.controls.UGenControl
import ponticello.model.score.controls.getNumericalValue
import ponticello.sc.ControlSpec
import ponticello.sc.NumericalControlSpec
import ponticello.sc.editor.ScExprExpander
import ponticello.ui.impl.DEFAULT_SCENE_FILL
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
    ): Node = Region()

    override fun createInitialControl(
        obj: ParameterizedObject,
        spec: ControlSpec?,
        oldControl: ParameterControl?,
        parameterName: String,
        anchorNode: Region?,
    ): UGenControl {
        val editor = ScExprExpander()
        val root = EditorRoot(editor)
        if (oldControl?.getNumericalValue() != null) {
            editor.setInitialText(oldControl.getNumericalValue().toString())
        } else editor.setInitialText("")
        return UGenControl(root)
    }

    override fun onSelected(
        namedControl: ParameterControlList.NamedParameterControl,
        control: UGenControl,
        view: ScoreObjectView?,
    ) {
        val actions = actions.withContext(Pair(namedControl, null))
        val window = makeCodePaneWindow(control.expr, control.context, namedControl, actions)
        window.show()

    }

    override fun actions(
        namedControl: ParameterControlList.NamedParameterControl,
        control: UGenControl,
        view: ScoreObjectView?,
    ): List<ContextualizedAction> = actions.withContext(Pair(namedControl, view))

    override fun toString(): String = "UGen"

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
        add(detailsAction(sceneFill = DEFAULT_SCENE_FILL.opacity(0.5)) { (namedControl) ->
            val control = namedControl.now as UGenControl
            val displayToggle = CheckBox(control.display).setupUndo(
                namedControl.context[UndoManager], variableDescription = "Display UGen")
                .named("Display")
            displayToggle.disableProperty().bind(
                control.expr.editor.result.map { expr ->
                    expr.getLfo() == null
                }.asObservableValue()
            )
        })
    }

    private fun getActiveObject(
        ctrl: ParameterControlList.NamedParameterControl,
        view: ScoreObjectView?,
    ): ActiveObject? {
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