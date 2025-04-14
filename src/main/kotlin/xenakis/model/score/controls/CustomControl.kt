package xenakis.model.score.controls

import hextant.context.Context
import hextant.serial.EditorRoot
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.copy
import xenakis.model.obj.ParameterizedObject
import xenakis.model.obj.ProcessDefObject
import xenakis.sc.ControlSpec
import xenakis.sc.ScExpr
import xenakis.sc.client.ScWriter
import xenakis.sc.editor.ScExprExpander
import xenakis.sc.lambda
import xenakis.sc.substitute

@Serializable
@SerialName("Custom")
data class CustomControl(
    val expr: EditorRoot<@Contextual ScExprExpander>,
    val subWindow: ReactiveVariable<Boolean> = reactiveVariable(false),
) : ParameterControl() {
    override fun copy(): ParameterControl = CustomControl(expr.clone(context), subWindow.copy())

    override fun initialize(context: Context) {
        expr.initialize(context)
    }

    override fun validate(spec: ControlSpec, obj: ParameterizedObject): Boolean = true

    override fun providesConstantSynthArgument(): Boolean = false

    override fun generateCodeFor(obj: ParameterizedObject, spec: ControlSpec): ScExpr =
        when (obj.def) {
            is ProcessDefObject -> lambda("t") { expr.editor.result.now }
            else -> expr.editor.result.now
        }

    override fun ScWriter.applyToSynth(
        parameter: String,
        spec: ControlSpec,
        obj: ParameterizedObject,
        synthVar: String,
    ) {
        val code = obj.controls.controlMap.entries.associate { (name, control) ->
            val expr = control.generateCodeFor(obj, spec)
            expr.let { "~ctrl_$name" to it }
        }
        val expr = expr.editor.result.now
            .substitute(code)
        val busName = "~auxil_${obj.name.now}_${parameter}"
        +"$busName = Bus.control(s, 1)"
        if (obj.duration() != null) {
            appendBlock("", endLine = false) {
                +"Env.new(levels: [0, 0], times: [${obj.duration()!!.now}]).kr(Done.freeSelf)"
                expr.code(writer, context)
            }
            +".play(s, $busName)"
        }
        +"${synthVar}.map(\\$parameter, $busName)"

    }
}