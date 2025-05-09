package xenakis.model.score.controls

import hextant.context.Context
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.now
import xenakis.impl.copy
import xenakis.model.obj.GlobalPatternReference
import xenakis.model.obj.ParameterizedObject
import xenakis.model.registry.GlobalPatternRegistry
import xenakis.sc.*
import xenakis.sc.client.ScWriter

@Serializable
class GlobalPatternControl(val pattern: ReactiveVariable<GlobalPatternReference>) : ParameterControl() {
    override fun copy(): ParameterControl = GlobalPatternControl(pattern.copy())

    override fun validate(spec: ControlSpec, obj: ParameterizedObject): Boolean =
        checkResolution(pattern.now, "Pattern")

    override fun initialize(context: Context, parentObject: ParameterizedObject) {
        super.initialize(context, parentObject)
        pattern.now.resolve(context[GlobalPatternRegistry])
    }

    override fun ScWriter.generatePreparationCode(
        obj: ParameterizedObject, uniqueName: String,
        parameter: String, spec: ControlSpec,
        ctx: CodegenContext,
    ) {
        if (ctx == CodegenContext.Process) {
            val patternName = pattern.now.force().superColliderName
            +"${uniqueArgumentName(uniqueName, parameter)} = $patternName"
        }
    }

    override fun generateArgumentExpr(
        obj: ParameterizedObject, uniqueName: String,
        parameter: String, spec: ControlSpec, context: CodegenContext,
    ): ScExpr = when (context) {
        CodegenContext.Process -> lambda("t") {
            Identifier(uniqueArgumentName(uniqueName, parameter)).send("next")
        }

        else -> pattern.now.force().superColliderExpr.send("next")
    }
}