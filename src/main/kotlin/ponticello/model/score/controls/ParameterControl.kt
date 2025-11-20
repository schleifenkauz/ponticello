package ponticello.model.score.controls

import hextant.context.Context
import kotlinx.serialization.Serializable
import ponticello.impl.Decimal
import ponticello.impl.Logger
import ponticello.model.instr.ParameterizedObject
import ponticello.model.instr.SynthDefObject
import ponticello.model.obj.AbstractContextualObject
import ponticello.model.obj.NamedObject
import ponticello.model.registry.ObjectReference
import ponticello.model.score.controls.ParameterControlList.NamedParameterControl
import ponticello.sc.ControlSpec
import ponticello.sc.ScExpr
import ponticello.sc.client.ScWriter
import reaktive.value.now

@Serializable
sealed class ParameterControl : AbstractContextualObject() {
    abstract fun copy(): ParameterControl

    abstract fun validate(spec: ControlSpec, obj: ParameterizedObject): Boolean

    open fun providesConstantSynthArgument(obj: ParameterizedObject, spec: ControlSpec, cutoff: Decimal): Boolean = true

    open fun customSynthArguments(cutoff: Decimal, totalDuration: Decimal): String? = null

    open fun allocatesBus(obj: ParameterizedObject, spec: ControlSpec?): Boolean = false

    open fun usesAuxilSynth(obj: ParameterizedObject, spec: ControlSpec?): Boolean = false

    open fun ScWriter.generatePreparationCode(
        obj: ParameterizedObject, uniqueName: String,
        parameter: String, spec: ControlSpec, cutoff: Decimal,
        ctx: CodegenContext,
    ) {
    }

    open fun ScWriter.applyToSynth(
        obj: ParameterizedObject,
        uniqueName: String,
        synthVar: String,
        parameter: String,
        spec: ControlSpec,
    ) {

    }

    abstract fun generateArgumentExpr(
        obj: ParameterizedObject, uniqueName: String,
        parameter: String, spec: ControlSpec, cutoff: Decimal,
        context: CodegenContext,
    ): ScExpr

    open fun initialize(context: Context, namedControl: NamedParameterControl) {
        super.initialize(context)
    }

    fun hasOwnSynth(obj: ParameterizedObject): Boolean = when (this) {
        is UGenControl -> true
        is EnvelopeControl -> obj.def is SynthDefObject
        else -> false
    }

    enum class CodegenContext {
        Synth, Process, SubArg;
    }

    companion object {
        @JvmStatic
        protected fun checkResolution(ownerObject: NamedObject, reference: ObjectReference<*>, type: String): Boolean = when {
            reference.isResolved.now -> true
            reference.get() != null -> {
                Logger.severe("$type '${reference.getName()}' (owned by #${ownerObject.name.now}) seems to have been removed from its registry.")
                false
            }

            else -> {
                Logger.severe("Cannot resolve $type '${reference.getName()}' (owned by #${ownerObject.name.now})")
                false
            }
        }

        fun uniqueArgumentName(uniqueName: String, parameter: String) = "~args_${uniqueName}[\\$parameter]"

        fun auxilBusesVar(uniqueName: String) = "~auxil_buses_$uniqueName"

        fun auxilBusName(uniqueName: String, parameter: String) = "${auxilBusesVar(uniqueName)}[\\$parameter]"

        fun auxilSynthsVar(uniqueName: String) = "~auxil_synths_$uniqueName"

        fun auxilSynthName(uniqueName: String, parameter: String) = "${auxilSynthsVar(uniqueName)}[\\${parameter}]"
    }
}