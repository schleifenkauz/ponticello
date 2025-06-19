package ponticello.ui.controls

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.actions.makeButton
import hextant.serial.EditorRoot
import javafx.scene.Node
import javafx.scene.layout.Region
import org.kordamp.ikonli.materialdesign2.MaterialDesignS
import ponticello.model.obj.ParameterizedObject
import ponticello.model.score.ParameterControlList.NamedParameterControl
import ponticello.model.score.ScoreObject
import ponticello.model.score.controls.ExprControl
import ponticello.model.score.controls.ParameterControl
import ponticello.model.score.controls.getNumericalValue
import ponticello.sc.BufferControlSpec
import ponticello.sc.BusControlSpec
import ponticello.sc.ControlSpec
import ponticello.sc.NumericalControlSpec
import ponticello.sc.editor.ScExprExpander
import ponticello.ui.score.ScoreObjectView

data object ExprControlType : ControlType<ExprControl>() {
    override fun applicableOn(obj: ParameterizedObject, spec: ControlSpec): Boolean = obj is ScoreObject &&
            (spec is NumericalControlSpec || spec is BusControlSpec || spec is BufferControlSpec)

    override fun createDetailInput(
        namedControl: NamedParameterControl, control: ExprControl, view: ScoreObjectView?,
    ): Node {
        val actions = actions.withContext(control)
        val window by lazy { makeCodePaneWindow(control.expr, control.context, namedControl, actions) }
        return showWindowAction.withContext { window }.makeButton("medium-icon-button")
    }

    override fun createSimpleInput(
        namedControl: NamedParameterControl, control: ExprControl,
    ): Node = createDetailInput(namedControl, control, null)

    override fun createInitialControl(
        obj: ParameterizedObject,
        spec: ControlSpec?,
        oldControl: ParameterControl,
        namedControl: NamedParameterControl,
        anchorNode: Region,
    ): ExprControl {
        val editor = ScExprExpander()
        val root = EditorRoot(editor)
        if (oldControl.getNumericalValue() != null) {
            editor.setInitialText(oldControl.getNumericalValue().toString())
        } else editor.setInitialText("")
        return ExprControl(root)
    }

    override fun onSelected(namedControl: NamedParameterControl, control: ExprControl, view: ScoreObjectView?) {
        val actions = actions.withContext(control)
        val window = makeCodePaneWindow(control.expr, control.context, namedControl, actions)
        window.show()
    }

    override fun actions(
        namedControl: NamedParameterControl,
        control: ExprControl,
        view: ScoreObjectView?,
    ): List<ContextualizedAction> = actions.withContext(control)

    override fun toString(): String = "Expr"

    private val actions = collectActions<ExprControl> {
        addAction("Update") {
            icon(MaterialDesignS.SYNC)
            shortcut("Ctrl+U")
            executes { ctrl -> ctrl.update.fire() }
        }
    }
}