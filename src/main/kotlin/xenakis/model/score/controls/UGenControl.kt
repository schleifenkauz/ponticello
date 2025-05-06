package xenakis.model.score.controls

import bundles.set
import hextant.context.Context
import hextant.context.extend
import hextant.serial.EditorRoot
import hextant.undo.UndoManager
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.event.unitEvent
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.model.ctx.PonticelloContext
import xenakis.model.ctx.Scope
import xenakis.model.obj.ParameterizedObject
import xenakis.model.obj.ProcessDefObject
import xenakis.model.obj.SynthDefObject
import xenakis.model.player.ActiveObject
import xenakis.sc.*
import xenakis.sc.client.ScWriter
import xenakis.sc.client.SuperColliderClient
import xenakis.sc.editor.ScExprExpander

@Serializable
@SerialName("UGen")
data class UGenControl(
    val expr: EditorRoot<@Contextual ScExprExpander>,
    val display: ReactiveVariable<Boolean> = reactiveVariable(true),
) : ParameterControl() {
    @Transient
    val update = unitEvent()

    override fun copy(): ParameterControl = UGenControl(expr.clone())

    override fun initialize(context: Context, parentObject: ParameterizedObject) {
        super.initialize(context, parentObject)
        val myContext = context.extend {
            set(UndoManager, context[UndoManager].createSubManager())
            set(PonticelloContext, PonticelloContext(parentObject, Scope.createEmpty()))
        }
        expr.initialize(myContext)
    }

    override fun validate(spec: ControlSpec, obj: ParameterizedObject): Boolean = true

    override fun providesConstantSynthArgument(): Boolean = false

    override fun allocatesBus(obj: ParameterizedObject): Boolean = true

    override fun usesAuxilSynth(obj: ParameterizedObject): Boolean = true

    override fun ScWriter.generatePreparationCode(
        obj: ParameterizedObject, uniqueName: String,
        parameter: String, spec: ControlSpec,
        context: CodegenContext,
    ) {
        val expr = substituteControlParameters(expr.editor.result.now, obj, uniqueName)
        val busName = auxilBusName(uniqueName, parameter)
        +"$busName = Bus.control(s, 1)"
        val auxilSynthName = auxilSynthName(uniqueName, parameter)
        val synthName = "${obj.superColliderPrefix}$uniqueName"
        append("$auxilSynthName = ")
        appendBlock("", endLine = false) {
            expr.code(writer, this@UGenControl.context)
        }
        +".play(target: $synthName, outbus: $busName, fadeTime: 0, addAction: 'addBefore')"
    }

    override fun generateArgumentExpr(
        obj: ParameterizedObject,
        uniqueName: String,
        parameter: String,
        spec: ControlSpec,
        context: CodegenContext,
    ): ScExpr {
        val busName = auxilBusName(uniqueName, parameter)
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
        uniqueName: String,
        synthVar: String,
        parameter: String,
        spec: ControlSpec,
    ) {
        val busName = auxilBusName(uniqueName, parameter)
        +"${synthVar}.map(\\$parameter, $busName)"
    }

    fun scope(activeObject: ActiveObject, parameter: String) {
        val client = context[SuperColliderClient]
        val uniqueName = activeObject.uniqueName
        val busName = auxilBusName(uniqueName, parameter)
        client.run {
            +"var scope"
            +"scope = Stethoscope.new(s, 1, $busName.index, rate:'control')"
            val closeScope = "AppClock.sched(0.05) { scope.window.close; scope.quit; nil  }"
            if (activeObject.associatedDef is SynthDefObject) {
                +"${activeObject.superColliderName}.onFree { $closeScope }"
            } else if (activeObject.associatedDef is ProcessDefObject) {
                +"${activeObject.superColliderName}.addDependant { |obj, signal| if (signal == 'stopped') { $closeScope } }"
            }
        }
    }

    companion object {
        fun substituteControlParameters(expr: ScExpr, obj: ParameterizedObject, uniqueName: String): ScExpr {
            val parameterMap = obj.controls.associateWith { ctrl ->
                val param = ctrl.name.now
                val spec = ctrl.spec.now!!
                { ctrl.now.generateArgumentExpr(obj, uniqueName, param, spec, context = CodegenContext.SubArg) }
            }
            val substitution = parameterMap.mapKeys { (param, _) -> "~ctrl_${param.name.now}" }
            return expr.transform<ParameterReference> { ref ->
                parameterMap[ref.parameter.get()]?.invoke()
                    ?: error("Unresolved control reference '${ref.parameter.getName()}'")
            }.substitute(substitution)
        }
    }
}