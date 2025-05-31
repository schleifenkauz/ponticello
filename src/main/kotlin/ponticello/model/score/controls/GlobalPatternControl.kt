package ponticello.model.score.controls

import hextant.context.Context
import kotlinx.serialization.Serializable
import ponticello.impl.Decimal
import ponticello.impl.copy
import ponticello.model.obj.GlobalPatternReference
import ponticello.model.obj.ParameterizedObject
import ponticello.model.registry.GlobalPatternRegistry
import ponticello.sc.*
import ponticello.sc.client.ScWriter
import reaktive.value.ReactiveVariable
import reaktive.value.now

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
        cutoff: Decimal,
        ctx: CodegenContext,
    ) {
        if (ctx == CodegenContext.Process) {
            val patternName = pattern.now.force().superColliderName
            +"${uniqueArgumentName(uniqueName, parameter)} = $patternName"
        }
    }

    override fun generateArgumentExpr(
        obj: ParameterizedObject, uniqueName: String,
        parameter: String, spec: ControlSpec, cutoff: Decimal, context: CodegenContext,
    ): ScExpr = when (context) {
        CodegenContext.Process -> lambda("t") {
            Identifier(uniqueArgumentName(uniqueName, parameter)).send("next")
        }

        else -> pattern.now.force().superColliderExpr.send("next")
    }
}