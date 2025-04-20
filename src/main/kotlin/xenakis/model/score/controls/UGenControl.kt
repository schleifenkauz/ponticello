package xenakis.model.score.controls

import hextant.context.Context
import hextant.serial.EditorRoot
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.event.unitEvent
import reaktive.value.now
import xenakis.model.flow.AudioFlow
import xenakis.model.obj.ParameterizedObject
import xenakis.model.player.ActiveObjectManager
import xenakis.model.player.PlaybackManager
import xenakis.model.score.ObjectPosition
import xenakis.model.score.ProcessObject
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
        context: CodegenContext,
    ) {
        val expr = substituteControlParameters(expr.editor.result.now, obj, uniqueName)
        val busName = uniqueArgumentName(uniqueName, parameter)
        +"$busName = Bus.control(s, 1)"
        val auxilSynthName = synthName(uniqueName, parameter)
        val synthName = "~synth_$uniqueName"
        append("$auxilSynthName = ")
        appendBlock("", endLine = false) {
            expr.code(writer, this@UGenControl.context)
        }
        +".play($synthName, $busName, fadeTime: 0, addAction: 'addBefore')"
        associatedServerObjects.addAll(listOf(busName, auxilSynthName))
    }

    override fun generateArgumentExpr(
        obj: ParameterizedObject,
        uniqueName: String,
        parameter: String,
        spec: ControlSpec,
        context: CodegenContext,
    ): ScExpr {
        val busName = uniqueArgumentName(uniqueName, parameter)
        return when (context) {
            CodegenContext.Synth, CodegenContext.SubArg -> Identifier(busName).send("kr")
            CodegenContext.Process -> lambda {
                val busVar = Identifier(busName)
                busVar.send("getSynchronous")
            }
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
        when {
            parentObject is AudioFlow -> {
                //TODO
            }
            activeInstance != null -> {
                val baseName = parentObject.name.now
                val uniqueName = ActiveObjectManager.uniqueName(baseName, activeInstance.suffix)
                val busName = uniqueArgumentName(uniqueName, parameter)
                client.run {
                    +"var scope"
                    +"scope = Stethoscope.new(s, 1, $busName.index, rate:'control')"
                    val closeScope = "AppClock.sched(0.05) { scope.window.close; scope.quit; nil  }"
                    if (activeInstance.obj is SynthObject) {
                        +"~synth_$uniqueName.onFree { $closeScope }"
                    } else if (activeInstance.obj is ProcessObject) {
                        +"~process_$uniqueName.addDependant { |obj, signal| if (signal == 'stopped') { $closeScope } }"
                    }
                }
            }
            parentObject is SynthObject -> {
                client.run {
                    +"var bus, scope, synth"
                    val associatedServerObjects = mutableListOf<String>()
                    val activeObjects = context[PlaybackManager].activeObjects
                    val suffix = activeObjects.insert(parentObject, ObjectPosition.ZERO)
                    val uniqueName = ActiveObjectManager.uniqueName(parentObject.name.now, suffix)
                    for (control in parentObject.controls) {
                        control.now.run {
                            generatePreparationCode(
                                parentObject, uniqueName,
                                control.name.now, control.spec.now!!,
                                associatedServerObjects,
                                context = CodegenContext.SubArg
                            )
                        }
                    }
                    +"~synth_$uniqueName = 'x'" //make it a dummy synth, so set and map commands don't fail...
                    +"bus = Bus.control(s, 1)"
                    +"scope = Stethoscope.new(s, 1, ~arg_${uniqueName}_$parameter.index, rate:'control')"
                    +"scope.window.onClose = { synth.free; bus.free; scope.quit; }" //TODO remove from active objects on close
                }
            }
        }
    }

    companion object {
        fun synthName(uniqueName: String, parameter: String) = "~auxil_synth_${uniqueName}_${parameter}"

        fun substituteControlParameters(expr: ScExpr, obj: ParameterizedObject, uniqueName: String): ScExpr {
            val substitution = obj.controls.associate { ctrl ->
                val param = ctrl.name.now
                val spec = ctrl.spec.now!!
                "~ctrl_$param" to {
                    ctrl.now.generateArgumentExpr(
                        obj, uniqueName,
                        param, spec, context = CodegenContext.SubArg
                    )
                }
            }
            return expr.substitute(substitution)
        }
    }
}