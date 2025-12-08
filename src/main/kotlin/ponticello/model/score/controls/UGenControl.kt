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
import ponticello.model.score.controls.ParameterControlList.NamedParameterControl
import ponticello.sc.*
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

    override fun writeCode(parameter: String, spec: ControlSpec?, obj: ParameterizedObject): String {
        val expr = expr.editor.result.now
        val references = expr.parameterReferences().joinToString(", ", "[", "]") { name -> "'${name.getName()}'" }
        val subst = substituteParameterReferences(expr)
        return "LFOControl('$parameter', $references) { |cutoff| ${subst.code(context)} }"
    }

    companion object {
        fun substituteParameterReferences(now: ScExpr) = now.transform<ParameterReference> { ref ->
            val parameter = ref.parameter.name.now
            SymbolLiteral(parameter).send("kr")
        }
    }
}