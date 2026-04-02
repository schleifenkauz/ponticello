package ponticello.model.score.controls

import hextant.serial.EditorRoot
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ponticello.model.instr.ParameterizedObject
import ponticello.sc.*
import ponticello.sc.editor.ScExprExpander
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
@SerialName("UGen")
data class UGenControl(
    override val expr: EditorRoot<@Contextual ScExprExpander>,
    val display: ReactiveVariable<Boolean> = reactiveVariable(true),
) : CodeControl() {
    override fun copy(): ParameterControl = UGenControl(expr.clone())

    override fun validate(spec: ControlSpec, obj: ParameterizedObject): Boolean = true

    override fun writeCode(parameter: String, spec: ControlSpec?, obj: ParameterizedObject): String {
        val expr = expr.editor.result.now
        val (subst, referencesStr) = substituteParameterReferences(expr, obj)
        return "LFOControl('$parameter', $referencesStr) { ${subst.code(context)} }"
    }

    companion object {
        fun substituteParameterReferences(expr: ScExpr, obj: ParameterizedObject): Pair<ScExpr, String> {
            val references = mutableSetOf<String>()
            val subst = substituteParameterReferences(expr, obj, references) { parameter ->
                SymbolLiteral(parameter).send("kr")
            }
            val referencesStr = references.joinToString(", ", "[", "]") { name -> "'${name}'" }
            return Pair(subst, referencesStr)
        }
    }
}
