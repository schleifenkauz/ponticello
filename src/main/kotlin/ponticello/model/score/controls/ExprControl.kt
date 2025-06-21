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
import ponticello.impl.Decimal
import ponticello.model.ctx.PonticelloContext
import ponticello.model.ctx.Scope
import ponticello.model.obj.ParameterizedObject
import ponticello.model.score.ParameterControlList.NamedParameterControl
import ponticello.sc.ControlSpec
import ponticello.sc.ScExpr
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

    override fun providesConstantSynthArgument(
        obj: ParameterizedObject, spec: ControlSpec, cutoff: Decimal,
    ): Boolean = true

    override fun initialize(context: Context, namedControl: NamedParameterControl) {
        super.initialize(context, namedControl)
        val myContext = context.extend {
            set(UndoManager, context[UndoManager]/*.createSubManager()*/)
            set(PonticelloContext, PonticelloContext(namedControl.parentObject, Scope.createEmpty()))
        }
        expr.initialize(myContext)
    }

    override fun generateArgumentExpr(
        obj: ParameterizedObject, uniqueName: String,
        parameter: String, spec: ControlSpec,
        cutoff: Decimal, context: CodegenContext,
    ): ScExpr = expr.editor.result.now

    companion object {
        fun create() = ExprControl(EditorRoot(ScExprExpander().defaultState()))
    }
}