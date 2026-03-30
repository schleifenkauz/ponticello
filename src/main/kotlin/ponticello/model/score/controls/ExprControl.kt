package ponticello.model.score.controls

import hextant.core.editor.defaultState
import hextant.serial.EditorRoot
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ponticello.model.instr.ParameterizedObject
import ponticello.sc.*
import ponticello.sc.editor.ScExprExpander
import reaktive.value.now

@Serializable
@SerialName("Expr")
class ExprControl(override val expr: EditorRoot<@Contextual ScExprExpander>) : CodeControl() {
    override fun copy(): ParameterControl = ExprControl(expr.clone())

    override fun validate(spec: ControlSpec, obj: ParameterizedObject): Boolean = true

    override fun writeCode(parameter: String, spec: ControlSpec?, obj: ParameterizedObject): String {
        val expr = substituteParameterReferences(expr.editor.result.now, obj)
        return "ExprControl('$parameter') { |inst, t| ${expr.code(context)} }"
    }

    companion object {
        fun create() = ExprControl(EditorRoot(ScExprExpander().defaultState()))

        fun substituteParameterReferences(expr: ScExpr, obj: ParameterizedObject) =
            substituteParameterReferences(expr, obj, null) { parameter ->
                Identifier("inst").send("get('$parameter')")
            }
    }
}