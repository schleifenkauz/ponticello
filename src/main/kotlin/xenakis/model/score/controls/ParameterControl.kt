package xenakis.model.score.controls

import hextant.context.Context
import kotlinx.serialization.Serializable
import reaktive.value.now
import xenakis.impl.Logger
import xenakis.model.obj.AbstractContextualObject
import xenakis.model.obj.ParameterizedObject
import xenakis.model.obj.SynthDefObject
import xenakis.model.registry.ObjectReference
import xenakis.sc.ControlSpec
import xenakis.sc.ScExpr
import xenakis.sc.client.ScWriter

@Serializable
sealed class ParameterControl : AbstractContextualObject() {
    abstract fun copy(): ParameterControl

    abstract fun validate(spec: ControlSpec, obj: ParameterizedObject): Boolean

    open fun providesConstantSynthArgument(): Boolean = true

    open fun allocatesBus(obj: ParameterizedObject): Boolean = false

    open fun usesAuxilSynth(obj: ParameterizedObject): Boolean = false

    open fun ScWriter.generatePreparationCode(
        obj: ParameterizedObject, uniqueName: String,
        parameter: String, spec: ControlSpec,
        associatedServerObjects: MutableList<String>,
        context: CodegenContext,
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
        parameter: String, spec: ControlSpec,
        context: CodegenContext,
    ): ScExpr

    open fun initialize(context: Context, parentObject: ParameterizedObject) {
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
        fun auxilSynthName(uniqueName: String, parameter: String) = "~auxil_synth_${uniqueName}_${parameter}"

        @JvmStatic
        protected fun checkResolution(reference: ObjectReference<*>, type: String): Boolean = when {
            reference.isResolved.now -> true
            reference.get() != null -> {
                Logger.severe("$type '${reference.getName()}' seems to have been removed from its registry.")
                false
            }

            else -> {
                Logger.severe("Cannot resolve $type '${reference.getName()}'")
                false
            }
        }

        fun uniqueArgumentName(uniqueName: String, parameter: String) = "~arg_${uniqueName}_$parameter"
    }
}