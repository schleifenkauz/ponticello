package xenakis.model.score.controls

import kotlinx.serialization.Serializable
import reaktive.value.now
import xenakis.impl.Logger
import xenakis.model.obj.AbstractContextualObject
import xenakis.model.obj.ParameterizedObject
import xenakis.model.registry.ObjectReference
import xenakis.sc.ControlSpec
import xenakis.sc.ScExpr
import xenakis.sc.client.ScWriter

@Serializable
sealed class ParameterControl : AbstractContextualObject() {
    abstract fun copy(): ParameterControl

    abstract fun validate(spec: ControlSpec, obj: ParameterizedObject): Boolean

    open fun providesConstantSynthArgument(): Boolean = true

    open fun ScWriter.generatePreparationCode(
        obj: ParameterizedObject, uniqueName: String,
        parameter: String, spec: ControlSpec,
        associatedServerObjects: MutableList<String>,
    ) {
    }

    open fun ScWriter.applyToSynth(obj: ParameterizedObject, synthVar: String, parameter: String, spec: ControlSpec) {

    }

    abstract fun generateArgumentExpr(
        obj: ParameterizedObject, uniqueName: String?,
        parameter: String, spec: ControlSpec,
    ): ScExpr

    open fun generateSubArgumentExpr(
        obj: ParameterizedObject, uniqueName: String?,
        parameter: String, spec: ControlSpec,
    ): ScExpr = generateArgumentExpr(obj, uniqueName, parameter, spec)

    companion object {
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