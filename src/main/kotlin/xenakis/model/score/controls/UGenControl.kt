package xenakis.model.score.controls

import hextant.context.Context
import hextant.serial.EditorRoot
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.event.unitEvent
import reaktive.value.now
import xenakis.model.obj.ParameterizedObject
import xenakis.model.obj.ProcessDefObject
import xenakis.model.obj.SynthDefObject
import xenakis.model.player.ActiveObjectManager
import xenakis.model.score.SynthObject
import xenakis.sc.*
import xenakis.sc.client.ScWriter
import xenakis.sc.client.SuperColliderClient
import xenakis.sc.editor.ScExprExpander

@Serializable
@SerialName("UGen")
data class UGenControl(
    val expr: EditorRoot<@Contextual ScExprExpander>,
) : ParameterControl() {
    @Transient
    val update = unitEvent()

    override fun copy(): ParameterControl = UGenControl(expr.clone(context))

    override fun initialize(context: Context) {
        super.initialize(context)
        expr.initialize(context)
    }

    override fun validate(spec: ControlSpec, obj: ParameterizedObject): Boolean = true

    override fun providesConstantSynthArgument(): Boolean = false

    override fun ScWriter.generatePreparationCode(
        obj: ParameterizedObject, uniqueName: String,
        parameter: String, spec: ControlSpec,
        associatedServerObjects: MutableList<String>,
    ) {
        val expr = substituteControlParameters(expr.editor.result.now, obj, uniqueName)
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
        uniqueName: String?,
        parameter: String,
        spec: ControlSpec,
    ): ScExpr {
        if (uniqueName == null) return substituteControlParameters(expr.editor.result.now, obj, null)
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

    fun scope(
        activeInstance: ActiveObjectManager.ActiveInstance?,
        parameter: String, parentObject: ParameterizedObject,
    ) {
        val client = context[SuperColliderClient]
        if (activeInstance != null) {
            val baseName = parentObject.name.now
            val uniqueName = ActiveObjectManager.uniqueName(baseName, activeInstance.suffix)
            val busName = uniqueArgumentName(uniqueName, parameter)
            client.run {
                +"var scope"
                +"scope = Stethoscope.new(s, 1, $busName.index, rate:'control')"
                if (activeInstance.obj is SynthObject) {
                    +"~synth_$uniqueName.onFree { AppClock.sched(0.1) { scope.window.close; scope.quit; nil  } }"
                }
            }
        } else {
            client.run {
                val expr = expr.editor.result.now
                val substituted = substituteControlParameters(expr, parentObject, uniqueName = null)
                val code = substituted.code(context)
                client.run {
                    +"var bus, scope, synth"
                    +"bus = Bus.control(s, 1)"
                    +"synth = { $code }.play(s, bus)"
                    +"scope = Stethoscope.new(s, 1, bus.index, rate:'control')"
                    +"scope.window.onClose = { synth.free; bus.free; scope.quit; }"
                }
            }
        }
    }

    companion object {
        fun synthName(uniqueName: String, parameter: String) = "~auxil_synth_${uniqueName}_${parameter}"

        fun substituteControlParameters(expr: ScExpr, obj: ParameterizedObject, uniqueName: String?): ScExpr {
            val substitution = obj.controls.associate { ctrl ->
                val param = ctrl.name.now
                val spec = ctrl.spec.now!!
                "~ctrl_$param" to { ctrl.now.generateSubArgumentExpr(obj, uniqueName, param, spec) }
            }
            return expr.substitute(substitution)
        }
    }
}