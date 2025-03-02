package xenakis.model.flow

import hextant.context.Context
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveValue
import reaktive.value.binding.binding
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.model.obj.BusObject
import xenakis.model.obj.ParameterizedObject
import xenakis.model.obj.ParameterizedObjectDef
import xenakis.model.obj.SynthDefObject
import xenakis.model.registry.InstrumentRegistry
import xenakis.model.registry.ObjectReference
import xenakis.model.score.BusControl
import xenakis.model.score.ParameterControls
import xenakis.model.score.getBus
import xenakis.sc.BusControlSpec
import xenakis.sc.client.ScWriter
import xenakis.sc.writeSynthCode

@Serializable
class SynthFlow(
    private var defRef: ObjectReference,
    override val controls: ParameterControls,
) : AudioFlow(), ParameterizedObject {
    val synthDef get() = defRef.get<SynthDefObject>()

    override val def: ParameterizedObjectDef
        get() = synthDef

    override lateinit var associatedBus: BusObject
        private set

    override lateinit var name: ReactiveValue<String>
        private set

    override fun initialize(context: Context) {
        super.initialize(context)
        defRef.resolve(context[InstrumentRegistry])
        controls.initialize(context, synthDef)
        val mainBusParameter = getMainBusParameter()
        associatedBus = controls[mainBusParameter].getBus()!!.get()
        name = binding(associatedBus.name, synthDef.name) { b, s -> "${b}_$s" }
    }

    fun getMainBusParameter() = synthDef.parameters.now.first { p -> p.spec.now is BusControlSpec }.name.now

    override fun copyFor(associatedBus: BusObject): AudioFlow {
        val ctrls = controls.copy()
        ctrls.controlMap[getMainBusParameter()] = BusControl(reactiveVariable(associatedBus.reference()))
        return SynthFlow(defRef, ctrls)
    }

    override fun ScWriter.writeCode(synthName: String, order: SynthOrder) {
        writeSynthCode(
            synthName, synthDef, controls,
            context, order, duration = null
        )
    }

    override fun getConnectedBusses(vararg flowType: FlowType): Set<BusObject> =
        super.getConnectedBusses(*flowType)
}