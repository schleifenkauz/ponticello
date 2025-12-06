package ponticello.model.score.controls

import hextant.context.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ponticello.impl.Decimal
import ponticello.impl.Logger
import ponticello.impl.copy
import ponticello.model.instr.ParameterizedObject
import ponticello.model.obj.BufferReference
import ponticello.model.obj.superColliderExpr
import ponticello.model.registry.reference
import ponticello.model.server.BufferObject
import ponticello.model.server.BufferRegistry
import ponticello.sc.BufferControlSpec
import ponticello.sc.ControlSpec
import ponticello.sc.Identifier
import ponticello.sc.ScExpr
import ponticello.sc.client.ScWriter
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
@SerialName("Buffer")
data class BufferControl(
    val sample: ReactiveVariable<BufferReference>
) : ParameterControl() {
    override fun initialize(context: Context, namedControl: ParameterControlList.NamedParameterControl) {
        super.initialize(context, namedControl)
        sample.now.resolve(context[BufferRegistry])
    }

    override fun copy(): ParameterControl = BufferControl(sample.copy())

    override fun validate(spec: ControlSpec, obj: ParameterizedObject): Boolean {
        if (spec !is BufferControlSpec) {
            Logger.warn("Expected BufferControlSpec but got $spec", Logger.Category.Playback)
            return false
        }
        return checkResolution(obj, sample.now, "Sample")
    }

    override fun ScWriter.generatePreparationCode(
        obj: ParameterizedObject, uniqueName: String,
        parameter: String, spec: ControlSpec,
        cutoff: Decimal,
        ctx: CodegenContext,
    ) {
        if (ctx == CodegenContext.Process) {
            val bufferName = sample.now.force().superColliderName
            +"${uniqueArgumentName(uniqueName, parameter)} = $bufferName"
        }
    }

    override fun generateArgumentExpr(
        obj: ParameterizedObject, uniqueName: String,
        parameter: String, spec: ControlSpec, cutoff: Decimal, context: CodegenContext,
    ): ScExpr = when(context) {
        CodegenContext.Process -> Identifier(uniqueArgumentName(uniqueName, parameter))
        else -> sample.now.force().superColliderExpr
    }

    companion object {
        fun create(buffer: BufferObject) = BufferControl(reactiveVariable(buffer.reference()))
    }
}