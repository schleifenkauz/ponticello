package ponticello.ui.controls

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.actions.detailsAction
import fxutils.controls.CheckBox
import fxutils.opacity
import fxutils.undo.UndoManager
import hextant.context.Context
import hextant.serial.EditorRoot
import javafx.event.Event
import javafx.scene.Node
import javafx.scene.layout.Region
import org.kordamp.ikonli.evaicons.Evaicons
import org.kordamp.ikonli.materialdesign2.MaterialDesignS
import ponticello.model.instr.ParameterizedObject
import ponticello.model.score.ObjectPosition
import ponticello.model.score.controls.ParameterControl
import ponticello.model.score.controls.ParameterControlList
import ponticello.model.score.controls.UGenControl
import ponticello.model.score.controls.getNumericalValue
import ponticello.sc.ControlSpec
import ponticello.sc.NumericalControlSpec
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.run
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
        ev: Event?,
    ): UGenControl {
        val editor = ScExprExpander()
        val root = EditorRoot(editor)
        if (oldControl?.getNumericalValue() != null) {
            editor.setInitialText(oldControl.getNumericalValue().toString())
        } else editor.setInitialText("")
        return UGenControl(root)
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
            executes { (ctrl, view), _ ->
                val parameter = ctrl.name.now
                val processName = ctrl.parentObject.soundProcessName!!
                scope(processName, view?.absolutePosition, parameter, ctrl.context)
            }
        }
        add(detailsAction(sceneFill = DEFAULT_SCENE_FILL.opacity(0.5)) { (namedControl) ->
            val control = namedControl.now as UGenControl
            val displayToggle = CheckBox(control.display).setupUndo(
                namedControl.context[UndoManager], variableDescription = "Display UGen"
            )
                .named("Display")
            displayToggle.disableProperty().bind(
                control.expr.editor.result.map { expr ->
                    expr.getLfo() == null
                }.asObservableValue()
            )
        })
    }

    private fun scope(processName: String, absolutePosition: ObjectPosition?, parameter: String, context: Context) {
        context[SuperColliderClient].run {
            append("var inst = ")
            if (absolutePosition != null) appendLine("SoundProcess.get('$processName').getInstanceAt($absolutePosition);")
            else appendLine("SoundProcess.get($processName.getSingleInstance);") //TODO
            appendBlock("if (inst != nil)", endLine = false) {
                appendBlock("AppClock.sched(0)") {
                    +"var index = inst.getControlBus('$parameter').index"
                    +"var scope = Stethoscope.new(s, 1, index, rate:'control')"
                    appendBlock("inst.onDispose") {
                        +"AppClock.sched(0.05) { scope.window.close; scope.quit; nil  }"
                    }
                }
            }
            appendBlock {
                +"\"No instance found for $processName\".postln"
            }
        }
    }
}