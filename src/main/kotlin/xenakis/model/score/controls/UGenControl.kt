package xenakis.model.score.controls

import hextant.context.Context
import hextant.serial.EditorRoot
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.event.unitEvent
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
@SerialName("UGen")
data class UGenControl(
    val expr: EditorRoot<@Contextual ScExprExpander>,
    val subWindow: ReactiveVariable<Boolean> = reactiveVariable(false),
) : ParameterControl() {
    @Transient
    val update = unitEvent()

    override fun copy(): ParameterControl = UGenControl(expr.clone(context), subWindow.copy())

    override fun initialize(context: Context) {
        expr.initialize(context)
    }

    override fun validate(spec: ControlSpec, obj: ParameterizedObject): Boolean = true

    override fun providesConstantSynthArgument(): Boolean = false

    override fun ScWriter.generatePreparationCode(
        obj: ParameterizedObject, uniqueName: String,
        parameter: String, spec: ControlSpec,
        associatedServerObjects: MutableList<String>,
    ) {
        val expr = substituteControlParameters(expr.editor.result.now, spec, obj, uniqueName)
        val busName = uniqueArgumentName(uniqueName, parameter)
        +"$busName = Bus.control(s, 1)"
        val auxilSynthName = synthName(uniqueName, parameter)
        val synthName = "~synth_$uniqueName"
        append("$auxilSynthName = ")
        appendBlock("", endLine = false) {
            expr.code(writer, context)
        }
        +".play($synthName, $busName, fadeTime: 0, addAction: 'addBefore')"
        associatedServerObjects.addAll(listOf(busName, auxilSynthName))
    }

    override fun generateArgumentExpr(
        obj: ParameterizedObject,
        uniqueName: String,
        parameter: String,
        spec: ControlSpec,
    ): ScExpr {
        val busName = uniqueArgumentName(uniqueName, parameter)
        return when (obj.def) {
            is SynthDefObject -> Identifier(busName).send("kr")
            is ProcessDefObject -> lambda {
                val busVar = Identifier(busName)
                busVar.send("getSynchronous")
            }

            else -> throw AssertionError("UGenControl is only applicable to SynthDef and ProcessDef")
        }
    }

    override fun ScWriter.applyToSynth(
        obj: ParameterizedObject,
        synthVar: String,
        parameter: String,
        spec: ControlSpec,
    ) {
        val busName = uniqueArgumentName(synthVar.removePrefix("~synth_"), parameter)
        +"${synthVar}.map(\\$parameter, $busName)"
    }

    companion object {
        fun synthName(uniqueName: String, parameter: String) = "~auxil_synth_${uniqueName}_${parameter}"

        fun substituteControlParameters(
            expr: ScExpr, spec: ControlSpec,
            obj: ParameterizedObject, uniqueName: String,
        ): ScExpr {
            val code = obj.controls.controlMap.entries.associate { (param, control) ->
                val arg = control.generateSubArgumentExpr(obj, uniqueName, param, spec)
                arg.let { "~ctrl_$param" to it }
            }
            return expr.substitute(code)
        }

    }
}