package ponticello.ui.controls

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.runFXWithTimeout
import hextant.serial.EditorRoot
import javafx.event.Event
import javafx.scene.Node
import javafx.scene.layout.Region
import org.kordamp.ikonli.materialdesign2.MaterialDesignS
import ponticello.model.instr.ParameterizedObject
import ponticello.model.score.ScoreObject
import ponticello.model.score.controls.ExprControl
import ponticello.model.score.controls.ParameterControl
import ponticello.model.score.controls.ParameterControlList.NamedParameterControl
import ponticello.model.score.controls.getNumericalValue
import ponticello.sc.*
import ponticello.sc.editor.ScExprExpander
import ponticello.ui.score.ParameterControlsPane
import ponticello.ui.score.ScoreObjectView

data object ExprControlType : ControlType<ExprControl>() {
    override fun applicableOn(obj: ParameterizedObject, spec: ControlSpec): Boolean = obj is ScoreObject &&
            (spec is NumericalControlSpec || spec is BusControlSpec || spec is BufferControlSpec || spec is ExprControlSpec)

    override fun createDetailInput(
        namedControl: NamedParameterControl, control: ExprControl, view: ScoreObjectView?,
    ): Node = Region()

    override fun createSimpleInput(
        namedControl: NamedParameterControl, control: ExprControl,
    ): Node = createDetailInput(namedControl, control, null)

    override fun createInitialControl(
        obj: ParameterizedObject,
        spec: ControlSpec?,
        oldControl: ParameterControl?,
        parameterName: String,
        ev: Event?,
    ): ExprControl {
        val editor = ScExprExpander()
        val root = EditorRoot(editor)
        if (oldControl?.getNumericalValue() != null) {
            editor.setInitialText(oldControl.getNumericalValue().toString())
        } else editor.setInitialText("")
        return ExprControl(root)
    }

    override fun onSelected(
        namedControl: NamedParameterControl,
        control: ExprControl,
        view: ScoreObjectView?,
        controlsPane: ParameterControlsPane?
    ) {
        if (controlsPane == null) return
        controlsPane.listView.getBox(namedControl).setExpanded(true)
        runFXWithTimeout {
            control.expr.control.receiveFocus()
        }
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
            shortcut("Ctrl+ENTER")
            executes { ctrl -> ctrl.update.fire() }
        }
    }
}