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

    open fun ScWriter.applyToSynth(parameter: String, spec: ControlSpec, obj: ParameterizedObject, synthVar: String) {
    }

    abstract fun generateCodeFor(obj: ParameterizedObject, spec: ControlSpec): ScExpr

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
    }
}