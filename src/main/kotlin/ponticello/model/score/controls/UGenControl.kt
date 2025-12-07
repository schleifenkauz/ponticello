package ponticello.model.score.controls

import bundles.set
import fxutils.undo.UndoManager
import hextant.context.Context
import hextant.context.extend
import hextant.serial.EditorRoot
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.model.ctx.PonticelloContext
import ponticello.model.instr.ParameterizedObject
import ponticello.model.instr.ProcessDefObject
import ponticello.model.instr.SynthDefObject
import ponticello.model.player.ActiveObject
import ponticello.model.score.controls.ParameterControlList.NamedParameterControl
import ponticello.sc.*
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.run
import ponticello.sc.editor.ScExprExpander
import reaktive.event.unitEvent
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
@SerialName("UGen")
data class UGenControl(
    val expr: EditorRoot<@Contextual ScExprExpander>,
    val display: ReactiveVariable<Boolean> = reactiveVariable(true),
) : ParameterControl() {
    @Transient
    val update = unitEvent()

    override fun copy(): ParameterControl = UGenControl(expr.clone())

    override fun initialize(context: Context, namedControl: NamedParameterControl) {
        super.initialize(context, namedControl)
        val myContext = context.extend {
            set(UndoManager, context[UndoManager]/*.createSubManager()*/)
            set(PonticelloContext, PonticelloContext.Control(namedControl))
        }
        expr.initialize(myContext)
    }

    override fun validate(spec: ControlSpec, obj: ParameterizedObject): Boolean = true

    override fun writeCode(spec: ControlSpec?, obj: ParameterizedObject): String {
        val expr = substituteParameterReferences(expr.editor.result.now)
        val references = expr.parameterReferences().joinToString(", ", "[", "]") { name -> "'$name'" }
        return "LFOControl($references) { |cutoff| ${expr.code(context)} }"
    }

    fun scope(activeObject: ActiveObject, parameter: String) {
        val client = context[SuperColliderClient]
        val uniqueName = activeObject.uniqueName
        val busName = auxilBusName(uniqueName, parameter)
        client.run {
            appendBlock("AppClock.sched(0)") {
                +"var scope"
                +"scope = Stethoscope.new(s, 1, $busName.index, rate:'control')"
                val closeScope = "AppClock.sched(0.05) { scope.window.close; scope.quit; nil  }"
                if (activeObject.associatedDef is SynthDefObject) {
                    +"${activeObject.superColliderName}.onFree { $closeScope }"
                } else if (activeObject.associatedDef is ProcessDefObject) {
                    +"${activeObject.superColliderName}.addDependant { |obj, signal| if (signal == 'stopped') { $closeScope } }"
                }
            }
        }
    }

    companion object {
        fun substituteParameterReferences(now: ScExpr) = now.transform<ParameterReference> { ref ->
            val parameter = ref.parameter.name.now
            SymbolLiteral(parameter).send("kr")
        }
    }
}