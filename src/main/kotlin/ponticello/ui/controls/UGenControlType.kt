package ponticello.ui.controls

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.actions.detailsAction
import fxutils.controls.CheckBox
import fxutils.opacity
import fxutils.prompt.PromptPlacement
import fxutils.runFXWithTimeout
import fxutils.undo.UndoManager
import hextant.context.Context
import hextant.serial.EditorRoot
import javafx.scene.Node
import javafx.scene.layout.Region
import org.kordamp.ikonli.evaicons.Evaicons
import org.kordamp.ikonli.materialdesign2.MaterialDesignS
import ponticello.impl.Logger
import ponticello.model.instr.ParameterizedObject
import ponticello.model.score.ObjectPosition
import ponticello.model.score.controls.ParameterControl
import ponticello.model.score.controls.ParameterControlList.NamedParameterControl
import ponticello.model.score.controls.UGenControl
import ponticello.model.score.controls.getCode
import ponticello.sc.ControlSpec
import ponticello.sc.NumericalControlSpec
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.run
import ponticello.sc.editor.ScExprExpander
import ponticello.sc.setDefaultExpr
import ponticello.ui.impl.DEFAULT_SCENE_FILL
import ponticello.ui.score.ParameterControlsPane
import ponticello.ui.score.ScoreObjectView
import reaktive.value.binding.map
import reaktive.value.fx.asObservableValue
import reaktive.value.now

data object UGenControlType : ControlType<UGenControl>() {
    override fun applicableOn(obj: ParameterizedObject, spec: ControlSpec): Boolean =
        spec is NumericalControlSpec

    override fun createDetailInput(
        namedControl: NamedParameterControl,
        control: UGenControl,
        view: ScoreObjectView?,
    ): Node = Region()

    override fun createInitialControl(
        obj: ParameterizedObject,
        spec: ControlSpec?,
        oldControl: ParameterControl?,
        parameterName: String,
        promptPlacement: PromptPlacement?,
    ): UGenControl {
        val expr = oldControl?.getCode() ?: run {
            val editor = ScExprExpander()
            if (spec != null) spec.setDefaultExpr(editor) else editor.setInitialText("")
            EditorRoot(editor)
        }
        return UGenControl(expr)
    }

    override fun actions(
        namedControl: NamedParameterControl,
        control: UGenControl,
        view: ScoreObjectView?,
    ): List<ContextualizedAction> = actions.withContext(Pair(namedControl, view))

    override fun onSelected(
        namedControl: NamedParameterControl, control: UGenControl,
        view: ScoreObjectView?, controlsPane: ParameterControlsPane?
    ) {
        if (controlsPane == null) return
        controlsPane.listView.getBox(namedControl).setExpanded(true)
        runFXWithTimeout {
            control.expr.control.receiveFocus()
        }
    }

    override fun toString(): String = "UGen"

    private val actions = collectActions<Pair<NamedParameterControl, ScoreObjectView?>> {
        addAction("Update") {
            icon(MaterialDesignS.SYNC)
            shortcut("Ctrl+Shift+U")
            executes { (ctrl) ->
                val ugen = ctrl.now as UGenControl
                ugen.update.fire()
                Logger.confirm("Updating control '${ctrl.name.now}'", Logger.Category.Playback)
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
            else appendLine("SoundProcess.get($processName).getSingleInstance;") //TODO
            appendBlock("if (inst != nil)", endLine = null) {
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