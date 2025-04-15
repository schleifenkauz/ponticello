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
import xenakis.model.obj.SynthDefObject
import xenakis.sc.*
import xenakis.sc.client.ScWriter
import xenakis.sc.editor.ScExprExpander

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

    override fun ScWriter.generatePreparationCode(
        obj: ParameterizedObject, uniqueName: String,
        parameter: String, spec: ControlSpec,
        associatedServerObjects: MutableList<String>
    ) {
        val expr = substituteControlParameters(obj, uniqueName, spec)
        if (obj.def is SynthDefObject && obj.def.hasParameter(parameter)) {
            val busName = getBusName(uniqueName, parameter)
            +"$busName = Bus.control(s, 1)"
            val auxilSynthName = "~auxil_synth_${uniqueName}_${parameter}"
            append("$auxilSynthName = ")
            appendBlock("", endLine = false) {
                expr.code(writer, context)
            }
            +".play(s, $busName)"
            associatedServerObjects.addAll(listOf(busName, auxilSynthName))
        }
    }

    private fun substituteControlParameters(
        obj: ParameterizedObject,
        uniqueName: String,
        spec: ControlSpec,
    ): ScExpr {
        val code = obj.controls.controlMap.entries.associate { (param, control) ->
            val expr = control.generateArgumentExpr(obj, uniqueName, param, spec)
            expr.let { "~ctrl_$param" to it }
        }
        return this.expr.editor.result.now.substitute(code)
    }

    private fun getBusName(uniqueName: String, parameter: String) = "~auxil_bus_${uniqueName}_${parameter}"

    override fun generateArgumentExpr(
        obj: ParameterizedObject,
        uniqueName: String,
        parameter: String,
        spec: ControlSpec
    ): ScExpr =
        when (obj.def) {
            is ProcessDefObject -> lambda("t") { substituteControlParameters(obj, uniqueName, spec) }
            is SynthDefObject -> {
                if (obj.def.hasParameter(parameter)) {
                    val busName = getBusName(uniqueName, parameter)
                    Identifier(busName).send("kr")
                } else substituteControlParameters(obj, uniqueName, spec)
            }
            else -> substituteControlParameters(obj, uniqueName, spec)
        }

    override fun ScWriter.applyToSynth(
        obj: ParameterizedObject,
        synthVar: String,
        parameter: String,
        spec: ControlSpec
    ) {
        val busName = getBusName(synthVar.removePrefix("~synth_"), parameter)
        +"${synthVar}.map(\\$parameter, $busName)"
    }
}