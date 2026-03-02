package ponticello.model.score.controls

import hextant.context.Context
import kotlinx.serialization.Serializable
import ponticello.impl.Logger
import ponticello.model.instr.ParameterizedObject
import ponticello.model.instr.SynthDefObject
import ponticello.model.obj.AbstractContextualObject
import ponticello.model.obj.NamedObject
import ponticello.model.registry.ObjectReference
import ponticello.model.score.controls.ParameterControlList.NamedParameterControl
import ponticello.sc.ControlSpec
import reaktive.value.now

@Serializable
sealed class ParameterControl : AbstractContextualObject() {
    abstract fun copy(): ParameterControl

    open fun initialize(context: Context, namedControl: NamedParameterControl) {
        if (initialized) return
        super.initialize(context)
    }

    abstract fun validate(spec: ControlSpec, obj: ParameterizedObject): Boolean

    abstract fun writeCode(parameter: String, spec: ControlSpec?, obj: ParameterizedObject): String

    fun hasOwnSynth(obj: ParameterizedObject): Boolean = when (this) {
        is UGenControl -> true
        is EnvelopeControl -> obj.def is SynthDefObject
        else -> false
    }

    companion object {
        @JvmStatic
        protected fun checkResolution(ownerObject: NamedObject, reference: ObjectReference<*>, type: String): Boolean =
            when {
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