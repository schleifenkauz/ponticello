package ponticello.model.score.controls

import bundles.set
import fxutils.undo.UndoManager
import hextant.context.Context
import hextant.context.extend
import hextant.core.editor.defaultState
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
import reaktive.value.now

@Serializable
@SerialName("Expr")
class ExprControl(val expr: EditorRoot<@Contextual ScExprExpander>) : ParameterControl() {
    @Transient
    val update = unitEvent()

    override fun copy(): ParameterControl = ExprControl(expr.clone())

    override fun validate(spec: ControlSpec, obj: ParameterizedObject): Boolean = true

    override fun initialize(context: Context, namedControl: NamedParameterControl) {
        if (initialized) return
        super.initialize(context, namedControl)
        val myContext = context.extend {
            set(UndoManager, context[UndoManager]/*.createSubManager()*/)
            set(PonticelloContext, PonticelloContext.Control(namedControl))
        }
        expr.initialize(myContext)
    }

    override fun writeCode(parameter: String, spec: ControlSpec?, obj: ParameterizedObject): String {
        val expr = substituteParameterReferences(expr.editor.result.now)
        return "ExprControl('$parameter') { |inst, t| ${expr.code(context)} }"
    }

    companion object {
        fun create() = ExprControl(EditorRoot(ScExprExpander().defaultState()))

        fun substituteParameterReferences(expr: ScExpr) = expr.transform<ParameterReference> { ref ->
            val parameter = ref.parameter.name.now
            Identifier("inst").send("getControlValue($parameter)")
        }
    }
}